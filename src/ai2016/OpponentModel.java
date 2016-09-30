package ai2016;

import java.util.ArrayList;
import java.util.HashMap;

import negotiator.Bid;
import negotiator.issue.Issue;

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
		
		//If agent has not been seen before, initialize it
		if(!weights.containsKey(agentHash) || !utilities.containsKey(agentHash)) {
			weights.put(agentHash, new HashMap<Issue, Float>());
			utilities.put(agentHash, new HashMap<Issue, HashMap<String, Integer>>());
		}
		
		//For each issue in the new bid		
		for(Issue i : newBid.getIssues()) {
			//INITIALIZATION
			//If issue has not been seen before in weights, initialize it
			if(!weights.get(agentHash).containsKey(i)) {
				float initialWeight = ((float) 1.0 / (float) newBid.getIssues().size());
				weights.get(agentHash).put(i, initialWeight);
			}
			//If issue has not been seen before in utilities, initialize it
			if(!utilities.get(agentHash).containsKey(i)) {
				utilities.get(agentHash).put(i, new HashMap<String, Integer>());
			}
			//If value has not been seen before, initialize it
			if(!utilities.get(agentHash).get(i).containsKey(newBid.getValue(i.getNumber()).toString())) {
				utilities.get(agentHash).get(i).put(newBid.getValue(i.getNumber()).toString(), 0);
			}
			
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
			//Increment frequency for this value of this issue
			int freq = utilities.get(agentHash).get(i).get(newBid.getValue(i.getNumber()).toString());
			utilities.get(agentHash).get(i).put(newBid.getValue(i.getNumber()).toString(), freq + 1);
		}
		
		System.out.println(weights.toString());
		System.out.println(utilities.toString());
		
	}
	
	/**
	 * Returns an estimate of the opponents utility of a bid.
	 * @param agentHash
	 * @return
	 */
	public double getOpponentUtility(int agentHash, Bid bid) {
		
		return (Double) 0.1;
	}
	
	public HashMap<Integer, HashMap<Issue, Float>> getWeights() {
		return weights;
	}
	public HashMap<Integer, HashMap<Issue, HashMap<String, Integer>>> getUtilities() {
		return utilities;
	}
}
