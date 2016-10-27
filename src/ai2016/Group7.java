package ai2016;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import negotiator.AgentID;
import negotiator.Bid;
import negotiator.Deadline;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;
import negotiator.issue.Issue;
import negotiator.issue.IssueDiscrete;
import negotiator.issue.Value;
import negotiator.issue.ValueDiscrete;
import negotiator.parties.AbstractNegotiationParty;
import negotiator.session.TimeLineInfo;
import negotiator.utility.AbstractUtilitySpace;

/**
 * Negotiation agent of group 7.
 * @author Bjorn van der Laan, Max van Zoest, Rik Oude Grote Bevelsborg, Sebas Joosten
 */
public class Group7 extends AbstractNegotiationParty {
	//constants for bidding strategy
	private final double phase_one_util = 1.0;
	private final double phase_two_minimum_util = 0.82;
	private final double turning_point = 0.1;
	
	//constants for acceptance strategy
	private final double alpha = 1.02;
	private final double beta = 0.02;
	
	//variables received in init
	private AbstractUtilitySpace utilSpace;
	private Deadline deadline;
	private TimeLineInfo timeline;
	private long randomSeed;
	private AgentID agentId;
	
	//List of all possible bids
	private ArrayList<Bid> bidsList = new ArrayList<Bid>();
	
	//Information about previous bids
	private Bid lastReceivedBid = null;
	private HashMap<Integer, ArrayList<Bid>> bidHistory = new HashMap<>();
	
	//Opponent model
	private OpponentModel opponentModel;
	
	//boolean for phase 2 of the bidding strategy to decide between SUM of MAXMIN bid
	private boolean minAndMax = false;

	/**
	 * Initializes the agent.
	 * @param utilSpace
	 * @param dl
	 * @param tl
	 * @param randomSeed
	 * @param agentId
	 */
	@Override
	public void init(AbstractUtilitySpace utilSpace, Deadline dl,
			TimeLineInfo tl, long randomSeed, AgentID agentId) {
		
		//Save all parameters for future reference
		this.utilSpace = utilSpace;
		this.deadline = dl;
		this.timeline = tl;
		this.randomSeed = randomSeed;
		this.agentId = agentId;		
		
		//Compute all possible bids
		computeAllBids();
		
		//Initialize Opponent model
		opponentModel = new OpponentModel(utilSpace);

		super.init(utilSpace, dl, tl, randomSeed, agentId);
		System.out.println("Discount Factor is "
				+ utilSpace.getDiscountFactor());
		System.out.println("Reservation Value is "
				+ utilSpace.getReservationValueUndiscounted());
	}
	

	/**
	 * Each round this method gets called and ask you to accept or offer. The
	 * first party in the first round is a bit different, it can only propose an
	 * offer.
	 *
	 * @param validActions
	 *            Either a list containing both accept and offer or only offer.
	 * @return The chosen action.
	 */
	@Override
	public Action chooseAction(List<Class<? extends Action>> validActions) {			
		//If there is no previous bid, make a random offer
		if(lastReceivedBid == null) {
			Bid randomBid = getRandomBid(phase_one_util);
			return new Offer(getPartyId(), randomBid);
		}
		
		//compute the current point in time as fraction of the total time
		double current = timeline.getCurrentTime() / timeline.getTotalTime();
		
		//PHASE 1
		//if we are before the turning point
		if(current <= turning_point) {
			//Option 1: Accept the offer according to the ACnext acceptance strategy with alpha and beta
			if (lastReceivedBid != null && ((alpha * getUtility(lastReceivedBid)) + beta) >= phase_one_util) {
				return new Accept(getPartyId(), lastReceivedBid);
			} 
			//Option 2: Make a random offer which an utility value of at 'phase_one_util'
			else {
				Bid randomBid = getRandomBid(phase_one_util);				
				return new Offer(getPartyId(), randomBid);
			}
		} 
		//PHASE 2
		//if we are after the turning point
		else {
			//compute the concession the agent will make based on the current point in time
			double helling = (phase_one_util - phase_two_minimum_util) / (1 - turning_point);
			double concession = (current - turning_point) * helling;
			
			//compute all feasible bids based on the concession
			double lower = phase_one_util - concession;
			double upper = phase_one_util;
			ArrayList<Bid> feasibleBids = getBidsBetween(lower, upper);
			
			//select the next bid based on one of two criteria between which the agent alternates
			Bid nextBid = null;		
			if (minAndMax == false) {
				//criteria: select the bid that has the highest minimum utility value of all opponents
		    	nextBid = opponentModel.formNiceBid(feasibleBids, BidStrategy.MIN);
		    	minAndMax = true;
		    } else {
		    	//criteria: select the bid that has the highest sum of utility values for all opponents
		    	nextBid = opponentModel.formNiceBid(feasibleBids, BidStrategy.SUM);
		    	minAndMax = false;
		    }
		    
			//if no bid is formed, do a random bid
			if(nextBid == null) {
				return new Offer(getPartyId(), getRandomBid(phase_one_util - concession));
			}

			//Option 1: Accept if the last bid is higher than the lower bound
			if(getUtility(lastReceivedBid) >= lower) {
				return new Accept(getPartyId(), lastReceivedBid);
			} 
			//Option 2: Accept the offer according to the ACnext acceptance strategy with alpha and beta
			else if(((alpha * getUtility(lastReceivedBid)) + beta) >= getUtility(nextBid)) {
				return new Accept(getPartyId(), lastReceivedBid);
			} 
			//Option 3: Accept if deadline is almost reached and the bid is 'good enough'
			else if(current >= 0.99 && getUtility(lastReceivedBid) >= 0.7) {
				return new Accept(getPartyId(), lastReceivedBid);
			} 
			//Option 4: Make the offer computed above
			else {
				return new Offer(getPartyId(), nextBid);
			}
		}
	}

	/**
	 * All offers proposed by the other parties will be received as a message.
	 * You can use this information to your advantage, for example to predict
	 * their utility.
	 *
	 * @param sender
	 *            The party that did the action. Can be null.
	 * @param action
	 *            The action that party did.
	 */
	@Override
	public void receiveMessage(AgentID sender, Action action) {
		super.receiveMessage(sender, action);
		if (action instanceof Offer) {
			//Save as the last received bid
			Bid receivedBid = ((Offer) action).getBid();
			lastReceivedBid = receivedBid;
			
			//Save bid in list of all bids for future reference
			if(!bidHistory.containsKey(sender.hashCode())) {
				bidHistory.put(sender.hashCode(), new ArrayList<Bid>());
			}
			bidHistory.get(sender.hashCode()).add(receivedBid);
			
			//Update opponent model
			ArrayList<Bid> agentsBids = bidHistory.get(sender.hashCode());
			opponentModel.update(sender.hashCode(), agentsBids);
		}
	}

	@Override
	public String getDescription() {
		return "Group 7";
	}

	/**
	 * Generates a random bid with an utility value between parameter target and 1.0.
	 * @param target 
	 * 			the lower bound of the utility range
	 * @return The chosen bid
	 */
	private Bid getRandomBid(double target) {
		//get all possible bids in this range
		ArrayList<Bid> candidates = getBidsBetween(target, 1.0);
		
		//If no bids are found, choose the maximum bid
		if(candidates.size() == 0) {
			try {
				return utilSpace.getMaxUtilityBid();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//If bids are found, choose one randomly
		Random rand = new Random();
	    int random = rand.nextInt(candidates.size());
	    return candidates.get(random);
	}

	/**
	 * Gets all bids with a utlity value between the lower and upper bound.
	 * @param lower
	 * 			lower bound of the range
	 * @param upper
	 * 			upper bound of the range
	 * @return the list of bids in this range
	 */
	private ArrayList<Bid> getBidsBetween(double lower, double upper) {
		ArrayList<Bid> result = new ArrayList<Bid>();
		
		//Select all bids with an utility value between lower and upper
		for(Bid b : bidsList) {
			if(lower <= getUtility(b) && getUtility(b) <= upper) {
				result.add(b);
			}
		}
		
		//Return the resulting list
		return result;
	}

	/**
	 * Computes all possible bids and saves them in a list for future reference.
	 */
	private void computeAllBids() {
		Issue issue = utilSpace.getDomain().getIssues().get(0);
		if (issue instanceof IssueDiscrete) {
		    IssueDiscrete discreteIssue = (IssueDiscrete) issue;
		    List<ValueDiscrete> values = discreteIssue.getValues();
		    
		    //For each of the values of the first issue, start a depth first search
		    for(Value value : values) {
		    	traverseDomain(new HashMap<Integer, Value>(), 1, value);
		    }
		}
	}

	/**
	 * Traverses the domain and computes all possible bids recursively.
	 * @param bidValues
	 * @param issueNumber
	 * @param previousValue
	 */
	private void traverseDomain(HashMap<Integer, Value> bidValues, int issueNumber, Value previousValue) {
		//add value to bid
		bidValues.put(issueNumber, previousValue);
		
		//stop condition
		if(issueNumber == utilSpace.getDomain().getIssues().size()) {
			HashMap<Integer, Value> vals = new HashMap<Integer, Value>();
			vals.putAll(bidValues);
			bidsList.add(new Bid(utilSpace.getDomain(), vals));
		} 
		//recursive step
		else {
			Issue issue = utilSpace.getDomain().getIssues().get(issueNumber);
	        if (issue instanceof IssueDiscrete) {
	            IssueDiscrete discreteIssue = (IssueDiscrete) issue;
	            List<ValueDiscrete> values = discreteIssue.getValues();
	            for(Value value : values) {
	            	traverseDomain(bidValues, issueNumber + 1, value);
	            }
	        }
		}
	}
}

