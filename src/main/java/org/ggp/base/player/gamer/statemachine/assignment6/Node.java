package org.ggp.base.player.gamer.statemachine.assignment6;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class Node {
	ArrayList<Node> children = new ArrayList<Node>(); //Current children added
	ArrayList<Move> moves = null; //This should stay null for terminal states, is basically unadded children
	double utility = 0;
	double avgUtility = Double.NEGATIVE_INFINITY;
	double oppUtility = 0;
	double oppAvgUtility = Double.NEGATIVE_INFINITY;
	Node parent = null;
	int visits = 0;
	MachineState state = null;
	Move move = null;

	boolean isExplored = false;

	double[] terminalScore = null;

	double std = 20;
	double secondMoment = 0;
	double oppStd = 20;
	double oppSecondMoment = 0;

	//Similar to moves but for MIN nodes. Should never be null because min nodes are never terminal
	List<List<Move>> jointMoves = new ArrayList<List<Move>>();
	boolean isMax;


	public Node(MachineState state, Node parent, Move move, List<List<Move>> jointMoves, boolean isMax) {
		this.state = state;
		this.parent = parent;
		this.move = move;
		this.jointMoves = jointMoves;
		this.isMax = isMax;
	}

	public void update(double[] scores) {
		this.visits = this.visits + 1;

		this.utility += scores[0];
    	this.oppUtility += scores[1];

		this.avgUtility = this.utility / this.visits;
		this.oppAvgUtility = this.oppUtility / this.visits;

		this.secondMoment += Math.pow(scores[0], 2);
		this.oppSecondMoment += Math.pow(scores[1], 2);

		if(this.visits > 10) { //Only calculate after 10 visits
			this.std = Math.sqrt(secondMoment/this.visits - Math.pow(this.avgUtility,2));
			this.oppStd = Math.sqrt(oppSecondMoment/this.visits - Math.pow(this.oppAvgUtility,2));
		}
		if(this.std <0.01)
			this.std = 20;
		if(this.oppStd <0.01)
			this.oppStd = 20;
	}

	public void update(double[] scores, boolean inputProve) {
		this.visits = this.visits + 1;

		this.utility += scores[0];
    	this.oppUtility += scores[1];

		this.avgUtility = this.utility / this.visits;
		this.oppAvgUtility = this.oppUtility / this.visits;

		this.secondMoment += Math.pow(scores[0], 2);
		this.oppSecondMoment += Math.pow(scores[1], 2);

		if(this.visits > 10) { //Only calculate after 10 visits
			this.std = Math.sqrt(secondMoment/this.visits - Math.pow(this.avgUtility,2));
			this.oppStd = Math.sqrt(oppSecondMoment/this.visits - Math.pow(this.oppAvgUtility,2));
		}
		if(this.std <0.01)
			this.std = 20;
		if(this.oppStd <0.01)
			this.oppStd = 20;
	}

}
