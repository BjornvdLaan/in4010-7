package ai2016;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
 * This is your negotiation party.
 */
public class Group7 extends AbstractNegotiationParty {
	//constants
	private final double INITIAL_UTIL = 0.95;
	private final double MINIMUM_UTIL = 0.75;
	private final double TURNING_POINT = 0.3;
	      //constants for ACnext-strategy
	private final double alpha = 1.02;
	private final double beta = 0.02;
	
	//variables received in init
	private AbstractUtilitySpace utilSpace;
	private Deadline deadline;
	private TimeLineInfo timeline;
	private long randomSeed;
	private AgentID agentId;
	
	private ArrayList<Bid> bidsList = new ArrayList<Bid>();
	//Information about bids
	private Bid lastReceivedBid = null;
	private HashMap<Integer, ArrayList<Bid>> bidHistory = new HashMap<>();
	
	/** Bid with the highest possible utility. */
	private Bid maxBid;
	
	//Opponent model
	private OpponentModel opponentModel;

	@Override
	public void init(AbstractUtilitySpace utilSpace, Deadline dl,
			TimeLineInfo tl, long randomSeed, AgentID agentId) {
		
		this.utilSpace = utilSpace;
		this.deadline = dl;
		this.timeline = tl;
		this.randomSeed = randomSeed;
		this.agentId = agentId;		
		
		computeAllBids();
		
		//Initialize Opponent model
		opponentModel = new OpponentModel(utilSpace);

		super.init(utilSpace, dl, tl, randomSeed, agentId);
		System.out.println("Discount Factor is "
				+ utilSpace.getDiscountFactor());
		System.out.println("Reservation Value is "
				+ utilSpace.getReservationValueUndiscounted());
		

		// if you need to initialize some variables, please initialize them
		// below

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
			Action randomAction = getRandomBid(INITIAL_UTIL);				
			return new Offer(getPartyId(), ((Offer) randomAction).getBid());
		}
		
		//get current time as fraction of total time
		double current = timeline.getCurrentTime() / timeline.getTotalTime();
		
		//if we are before the turning point (use alpha and beta to tweak performance of ACnext)
		if(current <= TURNING_POINT) {
			if (lastReceivedBid != null && ((alpha * getUtility(lastReceivedBid)) + beta) >= INITIAL_UTIL) {
				return new Accept(getPartyId(), lastReceivedBid);
			} else {
				Action randomAction = getRandomBid(INITIAL_UTIL);				
				return new Offer(getPartyId(), ((Offer) randomAction).getBid());
			}
		} 
		//if we are after the turning point (use alpha and beta to tweak performance of ACnext)
		else {			
			double helling = (INITIAL_UTIL - MINIMUM_UTIL) / (timeline.getTotalTime() - TURNING_POINT);
			double concession = (timeline.getCurrentTime() - TURNING_POINT) * helling;
			Action action = getRandomBid(INITIAL_UTIL - concession);
			Bid nextBid = ((Offer) action).getBid();
			
			if(((alpha * getUtility(lastReceivedBid)) + beta) >= getUtility(nextBid)) {
				return new Accept(getPartyId(), lastReceivedBid);
			} else {
				return new Offer(getPartyId(), ((Offer) action).getBid());
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
			//save the received bid
			Bid receivedBid = ((Offer) action).getBid();
			lastReceivedBid = receivedBid;
			
			//Save bid in list of all bids
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
	 * Return a bid with a utility at least equal to the target utility, or the
	 * bid with the highest utility possible if it takes too long to find.
	 * 
	 * @param target
	 * @return found bid.
	 */
	private Action getRandomBid(double target) {
		Bid bid = null;
		try {
			int loops = 0;
			do {
				bid = utilitySpace.getDomain().getRandomBid(null);
				loops++;
			} while (loops < 100000 && utilitySpace.getUtility(bid) < target);
			if (bid == null) {
				if (maxBid == null) {
					// this is a computationally expensive operation, therefore
					// cache result
					maxBid = utilitySpace.getMaxUtilityBid();
				}
				bid = maxBid;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new Offer(this.getPartyId(), bid);
	}
	
	private ArrayList<Bid> getBidsBetween(double lower, double upper) {
		ArrayList<Bid> result = new ArrayList<Bid>();
		
		for(Bid b : bidsList) {
			if(lower < getUtility(b) && getUtility(b) < upper) {
				result.add(b);
			}
		}
		
		return result;
	}
	
	private void computeAllBids() {
		Issue issue = utilSpace.getDomain().getIssues( ).get(1);
		
		if (issue instanceof IssueDiscrete) {
		    IssueDiscrete discreteIssue = (IssueDiscrete) issue;
		    List<ValueDiscrete> values = discreteIssue.getValues();
		    for(Value value : values) {
		    	HashMap<Integer, Value> bidValues = new HashMap<Integer, Value>();
		    	traverseDomain(bidValues, 1, value);
		    }
		}
		
		System.out.println(bidsList.toString());
	}
	
	private void traverseDomain(HashMap<Integer, Value> bidValues, int issueNumber, Value previousValue) {
		//add value to bid
		bidValues.put(issueNumber, previousValue);
		
		//stop condition
		if(issueNumber >= utilSpace.getDomain().getIssues().size() - 1) {
			bidsList.add(new Bid(utilSpace.getDomain(), bidValues));
			return;
		} 
		//recursive step
		else {
			Issue issue = utilSpace.getDomain().getIssues().get(issueNumber + 1);
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
