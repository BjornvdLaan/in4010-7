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
import negotiator.parties.AbstractNegotiationParty;
import negotiator.session.TimeLineInfo;
import negotiator.utility.AbstractUtilitySpace;

/**
 * This is your negotiation party.
 */
public class Group7 extends AbstractNegotiationParty {
	
	//Information about bids
	private Bid lastReceivedBid = null;
	private HashMap<Integer, ArrayList<Bid>> bidList = new HashMap<>();
	
	/** Bid with the highest possible utility. */
	private Bid maxBid;
	
	//Opponent model
	private OpponentModel opponentModel;
	
	/** The minimum utility a bid should have to be accepted or offered. */
	private double MINIMUM_BID_UTILITY;

	@Override
	public void init(AbstractUtilitySpace utilSpace, Deadline dl,
			TimeLineInfo tl, long randomSeed, AgentID agentId) {
		
		//Set minimum utility
		MINIMUM_BID_UTILITY = 0.65;//utilSpace.getReservationValueUndiscounted();
		
		//Initialize Opponent model
		opponentModel = new OpponentModel();

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
		
		
		if (lastReceivedBid != null
				&& getUtility(lastReceivedBid) >= MINIMUM_BID_UTILITY) {
			return new Accept(getPartyId(), lastReceivedBid);
		}
		return getRandomBid(MINIMUM_BID_UTILITY);
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
			Bid receivedBid = ((Offer) action).getBid();
			lastReceivedBid = receivedBid;
			
			//Save bid
			if(!bidList.containsKey(sender.hashCode())) {
				bidList.put(sender.hashCode(), new ArrayList<Bid>());
			}
			bidList.get(sender.hashCode()).add(receivedBid);
			
			//Update opponent model
			ArrayList<Bid> agentsBids = bidList.get(sender.hashCode());
			opponentModel.update(sender.hashCode(), agentsBids);
			
			//Display estimate of utility
			System.out.println("Estimated utility of opponent for this bid is " + opponentModel.getOpponentUtility(sender.hashCode(), receivedBid));
		}
	}

	@Override
	public String getDescription() {
		return "example party group N";
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

}
