package ai2016;

import java.util.ArrayList;
import java.util.HashMap;

import negotiator.AgentID;
import negotiator.Bid;
import negotiator.issue.Issue;
import negotiator.issue.Objective;

public class OpponentModel {
	private HashMap<Integer, HashMap<Issue, Float>> weights;
	private HashMap<Integer, HashMap<Issue, HashMap<String, Integer>>> utilities;
	private final float FACTOR = (float) 0.1;
	
	public OpponentModel() {
		//initialize datastructures
		weights = new HashMap<Integer, HashMap<Issue, Float>>();
		utilities = new HashMap<Integer, HashMap<Issue, HashMap<String, Integer>>>();
	}
	
	/**
	 * Updates the opponent model
	 * @param agentHash hash of the agent
	 * @param newBid the new bid of the agent
	 * @param previousBid the previous bid of that same agent
	 */
	public void update(int agentHash, ArrayList<Bid> agentsBids) {
		Bid newBid = agentsBids.get(agentsBids.size()-1);
		
		initializeIfNecessary(agentHash, newBid);
		
		//For each issue in the new bid		
		for(Issue i : newBid.getIssues()) {	
			if(agentsBids.size() >= 2) {
				//UPDATE WEIGHTS
				Bid previousBid = agentsBids.get(agentsBids.size()-2);
				//If the value for this issue is same as the one in the previous bid
				if(newBid.getValue(i.getNumber()).equals(previousBid.getValue(i.getNumber()))) {
					//Increase the weight
					float currentWeight = weights.get(agentHash).get(i);
					weights.get(agentHash).put(i, (float) (currentWeight + FACTOR));
				}
			}
			
			//UPDATE UTILITIES
			//If value has not been seen before, initialize it
			//NOTE: this could be moved to initializeIfNecessary if we find a way to get all possible values of an issue.
			if(!utilities.get(agentHash).get(i).containsKey(newBid.getValue(i.getNumber()).toString())) {
				utilities.get(agentHash).get(i).put(newBid.getValue(i.getNumber()).toString(), 0);
			}
			
			//Increment frequency for this value of this issue
			int freq = utilities.get(agentHash).get(i).get(newBid.getValue(i.getNumber()).toString());
			utilities.get(agentHash).get(i).put(newBid.getValue(i.getNumber()).toString(), freq + 1);
		}
		
		normalizeAgentData(agentHash);
		
		System.out.println(weights.toString());
		System.out.println(utilities.toString());
		
	}
	
	/**
	 * Normalizes model for this agent.
	 * @param agentHash
	 */
	public void normalizeAgentData(int agentHash) {
		normalizeWeightsOfAgent(agentHash);
		normalizeUtilitiesOfAgent(agentHash);
	}
	
	public void normalizeWeightsOfAgent(int agentHash) {
		HashMap<Issue, Float> agentWeights = weights.get(agentHash);
		
		float sum = (float) 0.0;
		for(Issue i : agentWeights.keySet()) {
			sum += agentWeights.get(i);
		}
		
		for(Issue i : agentWeights.keySet()) {
			float oldWeight = weights.get(agentHash).get(i);
			weights.get(agentHash).put(i, oldWeight / sum);
		}
	}
	
	public void normalizeUtilitiesOfAgent(int agentHash) {
		HashMap<Issue, HashMap<String, Integer>> agentUtils = utilities.get(agentHash);
		
		for(Issue i : agentUtils.keySet()) {
			HashMap<String, Integer> issueUtils = agentUtils.get(i);
			
			//find the biggest value of this issue
			int max = Integer.MIN_VALUE;
			for(int val : issueUtils.values()) {
				max = Integer.max(max, val);
			}
			
			for(String option : issueUtils.keySet()) {
				int oldUtil = agentUtils.get(i).get(option);
				//NOTE: might not add up to one because of rounding differences. Maybe make it doubles?
				utilities.get(agentHash).get(i).put(option, oldUtil / max);
			}
		}
	}
	
	/**
	 * Adds a new agent to the opponent modelling.
	 * @param agentHash
	 * @param newBid
	 */
	public void initializeIfNecessary(int agentHash, Bid newBid) {
		//If agent has not been seen before, initialize it
		if(!weights.containsKey(agentHash) || !utilities.containsKey(agentHash)) {
			weights.put(agentHash, new HashMap<Issue, Float>());
			utilities.put(agentHash, new HashMap<Issue, HashMap<String, Integer>>());
			
			//Initialize all issues
			for(Issue i : newBid.getIssues()) {
				//If issue has not been seen before in weights, initialize it
				if(!weights.get(agentHash).containsKey(i)) {
					float initialWeight = ((float) 1.0 / (float) newBid.getIssues().size());
					weights.get(agentHash).put(i, initialWeight);
				}
				//If issue has not been seen before in utilities, initialize it
				if(!utilities.get(agentHash).containsKey(i)) {
					utilities.get(agentHash).put(i, new HashMap<String, Integer>());
				}
			}
		}
	}
	
	/**
	 * Returns an estimate of the opponents utility of a bid.
	 * @param agentHash
	 * @param bid
	 * @return
	 */
	public double getOpponentUtility(int agentHash, Bid bid) {
		//if the agent is not modelled yet
		if(!utilities.containsKey(agentHash) || !weights.containsKey(agentHash)) {
			System.out.println("Agent Unknown");
			return Double.MAX_VALUE;
		}
		
		//Get information for this agent
		HashMap<Issue, HashMap<String, Integer>> agentUtils = utilities.get(agentHash);
		HashMap<Issue, Float> agentWeights = weights.get(agentHash);
		
		//Compute estimate with linear utility function
		double utility = 0.0;
		for(Issue i : agentUtils.keySet()) {
			//If a utility is not available
			if(!agentUtils.get(i).containsKey(bid.getValue(i.getNumber()).toString())) {
				//calculate for worst-case scenario
				utility += agentWeights.get(i) * 1.0;			
			} else {
				utility += agentWeights.get(i) * agentUtils.get(i).get(bid.getValue(i.getNumber()).toString());
			}
		}
		
		return utility;
	}
	
	/**
	 * Returns an estimate of the opponents utility of a bid.
	 * @param agent
	 * @param bid
	 * @return
	 */
	public double getOpponentUtility(AgentID agent, Bid bid) {
		return getOpponentUtility(agent.hashCode(), bid);
	}
	
	public HashMap<Integer, HashMap<Issue, Float>> getWeights() {
		return weights;
	}
	public HashMap<Integer, HashMap<Issue, HashMap<String, Integer>>> getUtilities() {
		return utilities;
	}
}
