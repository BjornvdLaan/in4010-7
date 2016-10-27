package ai2016;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import negotiator.Bid;
import negotiator.issue.Issue;
import negotiator.issue.Value;
import negotiator.utility.AbstractUtilitySpace;

/**
 * An opponent model based on the Frequency Analysis Heuristic.
 */
public class OpponentModel {
	private AbstractUtilitySpace utilSpace;
	private HashMap<Integer, HashMap<Issue, Double>> weights;
	private HashMap<Integer, HashMap<Issue, HashMap<Value, Double>>> frequencies, utilities;
	private final double factor = (double) 0.1;

	/**
	 * Constructor of this class.
	 * @param us 
	 * 			The utility space to used in this negotiation
	 */
	public OpponentModel(AbstractUtilitySpace us) {
		//Store the utility space for future reference
		utilSpace = us;
		
		//Initialize datastructures of the opponent model
		weights = new HashMap<Integer, HashMap<Issue, Double>>();
		frequencies = new HashMap<Integer, HashMap<Issue, HashMap<Value, Double>>>();
		utilities = new HashMap<Integer, HashMap<Issue, HashMap<Value, Double>>>();
	}
	
	/**
	 * Updates the opponent model.
	 * @param agentHash hash of the agent
	 * @param newBid the new bid of the agent
	 * @param previousBid the previous bid of that same agent
	 */
	public void update(int agentHash, ArrayList<Bid> agentsBids) {
		//Get the opponents latest bid
		Bid newBid = agentsBids.get(agentsBids.size()-1);
		
		//Initialize parts of the datastructures if necessary
		initializeIfNecessary(agentHash, newBid);
		
		//For each issue in the new bid		
		for(Issue i : newBid.getIssues()) {	
			//STEP 1: UPDATE WEIGHTS
			//Note: this is only possible if this is not the first bid by this agent
			if(agentsBids.size() >= 2) {
				//Get the previous bid by this agent
				Bid previousBid = agentsBids.get(agentsBids.size()-2);
				//If the value for this issue is same as the one in the previous bid, then increase the weight
				if(newBid.getValue(i.getNumber()).equals(previousBid.getValue(i.getNumber()))) {
					double currentWeight = weights.get(agentHash).get(i);
					weights.get(agentHash).put(i, currentWeight + factor);
				}
			}
			
			//STEP 2: UPDATE UTILITIES
			//If value has not been seen before, initialize it
			if(!frequencies.get(agentHash).get(i).containsKey(newBid.getValue(i.getNumber()))) {
				frequencies.get(agentHash).get(i).put(newBid.getValue(i.getNumber()), 0.0);
			}
			//Increment frequency for this value of this issue
			double freq = frequencies.get(agentHash).get(i).get(newBid.getValue(i.getNumber()));
			frequencies.get(agentHash).get(i).put(newBid.getValue(i.getNumber()), freq + 1.0);
		}
		
		//After the update, normalize all data
		normalizeAgentData(agentHash);		
	}
	
	/**
	 * Normalizes model for this agent.
	 * @param agentHash
	 * 				Unique identifier for this agent
	 */
	private void normalizeAgentData(int agentHash) {
		normalizeWeightsOfAgent(agentHash);
		normalizeUtilitiesOfAgent(agentHash);
	}

	/**
	 * Normalizes the weights for this agent.
	 * @param agentHash
	 * 				Unique identifier for this agent
	 */
	private void normalizeWeightsOfAgent(int agentHash) {
		//Get the weights of this agent
		HashMap<Issue, Double> agentWeights = weights.get(agentHash);
		
		//Compute the sum of all weights
		double sum = (double) 0.0;
		for(Issue i : agentWeights.keySet()) {
			sum += agentWeights.get(i);
		}
		
		//Divide all weights by the sum to normalize
		for(Issue i : agentWeights.keySet()) {
			double oldWeight = weights.get(agentHash).get(i);
			weights.get(agentHash).put(i, oldWeight / sum);
		}
	}

	/**
	 * Normalizes the utility values for this agent.
	 * @param agentHash
	 * 				Unique identifier for this agent
	 */
	private void normalizeUtilitiesOfAgent(int agentHash) {
		//Get the frequencies of the values of this agent
		HashMap<Issue, HashMap<Value, Double>> agentUtils = frequencies.get(agentHash);
		
		//For each issue
		for(Issue i : agentUtils.keySet()) {
			HashMap<Value, Double> issueUtils = agentUtils.get(i);
			
			//Find the biggest value of this issue
			double max = (double) Integer.MIN_VALUE;
			for(double val : issueUtils.values()) {
				max = Double.max(max, val);
			}
			
			//Normalize all values by dividing them by the maximum
			for(Value option : issueUtils.keySet()) {
				double oldUtil = agentUtils.get(i).get(option);
				utilities.get(agentHash).get(i).put(option, oldUtil / max);
			}
		}
	}
	
	/**
	 * Initializes parts of the datastructure if necessary.
	 * @param agentHash
	 * 				Unique identifier for this agent
	 * @param newBid
	 */
	private void initializeIfNecessary(int agentHash, Bid newBid) {
		//If agent has not been seen before, initialize it
		if(!weights.containsKey(agentHash) || !frequencies.containsKey(agentHash)) {
			weights.put(agentHash, new HashMap<Issue, Double>());
			frequencies.put(agentHash, new HashMap<Issue, HashMap<Value, Double>>());
			utilities.put(agentHash, new HashMap<Issue, HashMap<Value, Double>>());
			
			//Initialize all issues
			for(Issue i : newBid.getIssues()) {
				//If issue has not been seen before in weights, initialize it
				if(!weights.get(agentHash).containsKey(i)) {
					double initialWeight = ((double) 1.0 / (double) newBid.getIssues().size());
					weights.get(agentHash).put(i, initialWeight);
				}
				//If issue has not been seen before in utilities, initialize it
				if(!frequencies.get(agentHash).containsKey(i)) {
					frequencies.get(agentHash).put(i, new HashMap<Value, Double>());
					utilities.get(agentHash).put(i, new HashMap<Value, Double>());
				}
			}
		}
	}
	
	/**
	 * Computes an estimate of the opponents utility of a bid.
	 * @param agentHash
	 * 				Unique identifier for this agent
	 * @param bid
	 * @return an estimate of the opponents utility of a bid.
	 */
	public double getOpponentUtility(int agentHash, Bid bid) {
		//if the agent is not modelled yet
		if(!frequencies.containsKey(agentHash) || !weights.containsKey(agentHash)) {
			System.out.println("Agent Unknown");
			return Double.MAX_VALUE;
		}
		
		//Get information for this agent
		HashMap<Issue, HashMap<Value, Double>> agentUtils = utilities.get(agentHash);
		HashMap<Issue, Double> agentWeights = weights.get(agentHash);
		
		
		//Compute estimate with linear utility function
		double utility = 0.0;
		for(Issue i : agentUtils.keySet()) {
			//If a utility is not available
			if(!agentUtils.get(i).containsKey(bid.getValue(i.getNumber()))) {
				//expected value for U(0,1)
				utility += agentWeights.get(i) * 0.5;			
			} else {
				utility += agentWeights.get(i) * agentUtils.get(i).get(bid.getValue(i.getNumber()));
			}
		}
		
		return utility;
	}
	
	/**
	 * Returns the identifiers of all agents in the model.
	 * @return
	 */
	public Set<Integer> getAgentHashes() {
		return weights.keySet();
	}

	/**
	 * Select a nice bid based on the feasible bids and according to a given bid strategy.
	 * @param feasibleBids
	 * 				The list of feasible bids
	 * @param strategy
	 * 				The strategy to apply
	 * @return the selected bid
	 */
	public Bid formNiceBid(ArrayList<Bid> feasibleBids, BidStrategy strategy) {
		
		double maxSumBidUtility = 0;
		Bid maxSumBid = null;
		double maxMinBidUtility = 1;
		Bid maxMinBid = null;
		
		//Investigate all bids
		for(Bid b : feasibleBids) {
			
			double maxMin = 1;
			double sum = 0;
			
			//Compute utility value of this bid for each agent
			for(int hash : weights.keySet()) {
				//we have to calculate the utility of every opponent 
				double opponentUtility = getOpponentUtility(hash, b);
				//calculate the sum over the opponents utility for the bid
				sum += opponentUtility;
				//calculate the maxMin utility for the bid
				if(opponentUtility <= maxMin) {
					maxMin = opponentUtility;
				}	
			}
			
			//update maxSumBid and maxMinBid if we found a better bid
			if(sum >= maxSumBidUtility) {
				maxSumBidUtility = sum;
				maxSumBid = b;
			}
			
			if(maxMin >= maxMinBidUtility) {
				maxMinBidUtility = maxMin;
				maxMinBid = b;
			}
		}
		
		//Return the bid according to the strategy
		if(strategy == BidStrategy.SUM) {
			return maxSumBid;
		}
		else if(strategy == BidStrategy.MIN) {
			return maxMinBid;
		}
		else {
			return null;
		}
	}
}