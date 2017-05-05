package org.ggp.base.player.gamer.statemachine.assignment5;

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
	}

}
