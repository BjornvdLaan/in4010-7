package ai2016;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import negotiator.AgentID;
import negotiator.Bid;
import negotiator.issue.Issue;
import negotiator.issue.Objective;
import negotiator.issue.Value;
import negotiator.utility.AbstractUtilitySpace;

public class OpponentModel {
	private AbstractUtilitySpace utilSpace;
	private HashMap<Integer, HashMap<Issue, Double>> weights;
	private HashMap<Integer, HashMap<Issue, HashMap<Value, Double>>> frequencies, utilities;
	private final double FACTOR = (double) 0.1;
	
	public OpponentModel(AbstractUtilitySpace us) {
		//store util space
		utilSpace = us;
		//initialize datastructures
		weights = new HashMap<Integer, HashMap<Issue, Double>>();
		frequencies = new HashMap<Integer, HashMap<Issue, HashMap<Value, Double>>>();
		utilities = new HashMap<Integer, HashMap<Issue, HashMap<Value, Double>>>();
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
					double currentWeight = weights.get(agentHash).get(i);
					weights.get(agentHash).put(i, currentWeight + FACTOR);
				}
			}
			
			//UPDATE UTILITIES
			//If value has not been seen before, initialize it
			//NOTE: this could be moved to initializeIfNecessary if we find a way to get all possible values of an issue.
			if(!frequencies.get(agentHash).get(i).containsKey(newBid.getValue(i.getNumber()))) {
				frequencies.get(agentHash).get(i).put(newBid.getValue(i.getNumber()), 0.0);
			}
			
			//Increment frequency for this value of this issue
			double freq = frequencies.get(agentHash).get(i).get(newBid.getValue(i.getNumber()));
			frequencies.get(agentHash).get(i).put(newBid.getValue(i.getNumber()), freq + 1.0);
		}
		
		normalizeAgentData(agentHash);
		
		//System.out.println(weights.toString());
		//System.out.println(frequencies.toString());
		
	}
	
	/**
	 * Normalizes model for this agent.
	 * @param agentHash
	 */
	private void normalizeAgentData(int agentHash) {
		normalizeWeightsOfAgent(agentHash);
		normalizeUtilitiesOfAgent(agentHash);
	}
	
	private void normalizeWeightsOfAgent(int agentHash) {
		HashMap<Issue, Double> agentWeights = weights.get(agentHash);
		
		double sum = (double) 0.0;
		for(Issue i : agentWeights.keySet()) {
			sum += agentWeights.get(i);
		}
		
		for(Issue i : agentWeights.keySet()) {
			double oldWeight = weights.get(agentHash).get(i);
			weights.get(agentHash).put(i, oldWeight / sum);
		}
	}
	
	private void normalizeUtilitiesOfAgent(int agentHash) {
		HashMap<Issue, HashMap<Value, Double>> agentUtils = frequencies.get(agentHash);
		
		for(Issue i : agentUtils.keySet()) {
			HashMap<Value, Double> issueUtils = agentUtils.get(i);
			
			//find the biggest value of this issue
			double max = (double) Integer.MIN_VALUE;
			for(double val : issueUtils.values()) {
				max = Double.max(max, val);
			}
			
			for(Value option : issueUtils.keySet()) {
				double oldUtil = agentUtils.get(i).get(option);
				utilities.get(agentHash).get(i).put(option, oldUtil / max);
			}
		}
	}
	
	/**
	 * Adds a new agent to the opponent modelling.
	 * @param agentHash
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
	 * Returns an estimate of the opponents utility of a bid.
	 * @param agentHash
	 * @param bid
	 * @return
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
	 * Returns the agents most bidded Value for this Issue.
	 * @param agentHash
	 * @param i
	 * @return
	 */
	public Value getOpponentsFavoriteOfIssue(int agentHash, Issue i) {
		if(frequencies.get(agentHash) != null) {
			HashMap<Value, Double> values = frequencies.get(agentHash).get(i);
			Value favoriteValue = null;
			
			//get the value with the highest frequency
			double favoriteFreq = Double.MIN_VALUE;
			for(Value v : values.keySet()) {
				if(values.get(v) > favoriteFreq) {
					favoriteValue = v;
					favoriteFreq = values.get(v);
				}
			}
			
			return favoriteValue;
		} else {
			return null;
		}
	}	
	
	/**
	 * Computes the bid that this agent will like most.
	 * @param agentHash
	 * @return
	 */
	public Bid getOpponentsBestBid(int agentHash) {
		HashMap<Integer, Value> bidValues = new HashMap<Integer, Value>();
		for(Issue i : utilSpace.getDomain().getIssues()) {
			Value val = getOpponentsFavoriteOfIssue(agentHash, i);
			if(val == null) {
				return null;
			} else {
				bidValues.put(i.getNumber(), val);
			}			
		}
		return new Bid(utilSpace.getDomain(), bidValues);
	}
	
	public Set<Integer> getAgentHashes() {
		return weights.keySet();
	}
}