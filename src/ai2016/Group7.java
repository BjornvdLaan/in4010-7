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
		
		//Compute all own bids
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
			Bid randomBid = getRandomBid(INITIAL_UTIL);
			return new Offer(getPartyId(), randomBid);
		}
		
		//get current time as fraction of total time
		double current = timeline.getCurrentTime() / timeline.getTotalTime();
		
		//if we are before the turning point (use alpha and beta to tweak performance of ACnext)
		if(current <= TURNING_POINT) {
			if (lastReceivedBid != null && ((alpha * getUtility(lastReceivedBid)) + beta) >= INITIAL_UTIL) {
				return new Accept(getPartyId(), lastReceivedBid);
			} else {
				Bid randomBid = getRandomBid(INITIAL_UTIL);				
				return new Offer(getPartyId(), randomBid);
			}
		} 
		//if we are after the turning point (use alpha and beta to tweak performance of ACnext)
		else {			
			double helling = (INITIAL_UTIL - MINIMUM_UTIL) / (timeline.getTotalTime() - TURNING_POINT);
			double concession = (timeline.getCurrentTime() - TURNING_POINT) * helling;
			
			double upper = INITIAL_UTIL - concession;
			double lower = upper - 0.05;
			
			ArrayList<Bid> feasibleBids = getBidsBetween(lower, upper);
			
			Bid nextBid = null;			
		    if ((Math.random() * 2) + 1 == 1) {
		    	nextBid = opponentModel.formNiceBid(feasibleBids, BidStrategy.MIN);
		    } else {
		    	nextBid = opponentModel.formNiceBid(feasibleBids, BidStrategy.SUM);
		    }
		    
			//if no bid is formed
			if(nextBid == null) {
				return new Offer(getPartyId(), getRandomBid(INITIAL_UTIL - concession));
			}
			
			if(((alpha * getUtility(lastReceivedBid)) + beta) >= getUtility(nextBid)) {
				return new Accept(getPartyId(), lastReceivedBid);
			} else {
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
	
	private Bid getRandomBid(double target) {
		//get all possible bids
		ArrayList<Bid> candidates = getBidsBetween(target, 1.0);
		
		
		//if no candidates, do max utility bid
		if(candidates.size() == 0) {
			try {
				return utilSpace.getMaxUtilityBid();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//draw a random number
		Random rand = new Random();
	    int random = rand.nextInt(candidates.size());
	    
	    return candidates.get(random);
	}
	
	private ArrayList<Bid> getBidsBetween(double lower, double upper) {
		ArrayList<Bid> result = new ArrayList<Bid>();
		
		for(Bid b : bidsList) {
			if(lower <= getUtility(b) && getUtility(b) <= upper) {
				result.add(b);
			}
		}
		System.out.println(result.size());
		return result;
	}
	
	private void computeAllBids() {
		computeAllBids2();
		/*Issue issue = utilSpace.getDomain().getIssues().get(0);
		if (issue instanceof IssueDiscrete) {
		    IssueDiscrete discreteIssue = (IssueDiscrete) issue;
		    List<ValueDiscrete> values = discreteIssue.getValues();
		    for(Value value : values) {
		    	traverseDomain(new HashMap<Integer, Value>(), 1, value);
		    }
		}
		
		System.out.println(bidsList);*/
	}
	
	/**
	 * Temporary method. ONLY USABLE ON PARTY DOMAIN.
	 */
	private void computeAllBids2() {
	    List<ValueDiscrete> avals = ((IssueDiscrete) utilSpace.getDomain().getIssues().get(0)).getValues();
	    List<ValueDiscrete> bvals = ((IssueDiscrete) utilSpace.getDomain().getIssues().get(1)).getValues();
	    List<ValueDiscrete> cvals = ((IssueDiscrete) utilSpace.getDomain().getIssues().get(2)).getValues();
	    List<ValueDiscrete> dvals = ((IssueDiscrete) utilSpace.getDomain().getIssues().get(3)).getValues();
	    List<ValueDiscrete> evals = ((IssueDiscrete) utilSpace.getDomain().getIssues().get(4)).getValues();
	    List<ValueDiscrete> fvals = ((IssueDiscrete) utilSpace.getDomain().getIssues().get(5)).getValues();
	    
		for(Value a : avals) {
			for(Value b : bvals) {
				for(Value c : cvals) {
					for(Value d : dvals) {
						for(Value e : evals) {
							for(Value f : fvals) {
								HashMap<Integer, Value> values = new HashMap<Integer, Value>();
								values.put(1, a);
								values.put(2, b);
								values.put(3, c);
								values.put(4, d);
								values.put(5, e);
								values.put(6, f);
								bidsList.add(new Bid(utilSpace.getDomain(), values));
							}
						}
					}
				}
			}
		}
	}
	
	private void traverseDomain(HashMap<Integer, Value> bidValues, int issueNumber, Value previousValue) {
		//add value to bid
		bidValues.put(issueNumber, previousValue);
		
		//stop condition
		if(issueNumber == utilSpace.getDomain().getIssues().size()) {
			bidsList.add(new Bid(utilSpace.getDomain(), bidValues));
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



