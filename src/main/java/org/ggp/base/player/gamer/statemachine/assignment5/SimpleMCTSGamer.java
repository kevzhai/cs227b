package org.ggp.base.player.gamer.statemachine.assignment5;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


//A Minimax Model MCTS Gamer with some default properties
//Use this one for easy testing, use GenericMCTSGamer to play competitively.
//Most of the documentation is on the Generic gamer
public class SimpleMCTSGamer extends SampleGamer {

	private long finishBy;
	private Logger logger = Logger.getLogger(getClass().getSimpleName());
	/*A constant. Higher means we would like to explore the tree more.*/
	private double explorationConstant = 20;
	private StateMachine theMachine;
	private Role role;
	private Random random;

	//Play a meta game to decide on some initial parameters (depth limit)
	@Override
	public void stateMachineMetaGame(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
//		finishBy = timeout - 3000; //Leave one second left
		theMachine = getStateMachine();
		role = getRole();
		random = new Random();
	}

	//Overrides to stop iterative deepening if it does not find a terminal state
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();
		//Finish with 2 seconds remaining
		finishBy = timeout - 2500;

		List<Move> moves = theMachine.getLegalMoves(getCurrentState(), role);
		Move bestMove = moves.get(random.nextInt(moves.size()));

		Node root = new Node(getCurrentState(), null, null, null, false);
		root.moves = new ArrayList<Move> ( moves );

		// Run the MCTS
		double[] scores = new double[2];
		while( System.currentTimeMillis() < finishBy ) {
			Node newNode = select(root);
			Node newGrandchild = expand(newNode);
			scores[0] = simulation(newGrandchild);
			backPropagate(newGrandchild, scores);
		}

		// Select the best move
		double bestScore = Double.NEGATIVE_INFINITY;
		double bestUtility = 0;
		// Don't know the best way to select this value
		// Try larger values
		double boundConstant = 4;
		for(Node child: root.children) {
			double curScore = child.avgUtility + boundConstant/Math.sqrt(child.visits);
			if(child.move == null) {
				logger.log(Level.WARNING, ""+root.moves);
			}
			if(child.visits > 0 && curScore > bestScore) {
				bestScore = curScore;
				bestMove = child.move;
				bestUtility = child.avgUtility;
			}
		}

		long stop = System.currentTimeMillis();
		logger.log(Level.INFO, "Depth charges:" + root.visits);
		logger.log(Level.INFO, "Best Score:" + (bestScore));
		logger.log(Level.INFO, "Best Utility:" + (bestUtility));
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}
	// Selects a node to focus on next
	// First visits unselected nodes, else uses selectfn to choose a node
	protected Node select(Node node) {
		if(!node.isMax && ( node.moves == null || node.moves.size() > 0) ) return node; //Is terminal or no new children to make

		if(node.isMax && node.jointMoves.size() > 0) { //create new grandChild
			return node;
		}

		double bestScore = Double.NEGATIVE_INFINITY;
		Node result = node;

		for(Node child: node.children) {
			double newScore = selectfn(child, node.isMax);
			if(newScore > bestScore) {
				bestScore = newScore;
				result = child;
			}
		}
		return select(result);
	}

	// Adds more nodes to our tree
	// Only does this if we are not on a terminal state
	protected Node expand(Node node) throws MoveDefinitionException, TransitionDefinitionException {
		if(!node.isMax && node.moves != null) { //If it's not terminal
			//Make a child and grandchild
			Move move = node.moves.remove(node.moves.size()-1);
			List<List<Move>> jointMoves = theMachine.getLegalJointMoves(node.state, role, move);
			MachineState nextState = theMachine.getNextState(node.state, jointMoves.remove(jointMoves.size()-1));
			Node newChild = new Node(node.state, node, move, jointMoves, true);
			node.children.add(newChild);

			Node newGrandchild = new Node(nextState, newChild, null, null, false);
			if(!theMachine.isTerminal(newGrandchild.state))
				newGrandchild.moves = new ArrayList<Move> (  theMachine.getLegalMoves(nextState, role) );
			newChild.children.add(newGrandchild);
			return newGrandchild;
		}
		else if (node.isMax){ //Make a child
			MachineState nextState = theMachine.getNextState(node.state, node.jointMoves.remove(node.jointMoves.size()-1));

			Node newGrandchild = new Node(nextState, node, null, null, false);
			if(!theMachine.isTerminal(newGrandchild.state))
				newGrandchild.moves = new ArrayList<Move> (  theMachine.getLegalMoves(nextState, role) );
			node.children.add(newGrandchild);
			return newGrandchild;
		}
		return node;
	}

	// An evaluation of which node to focus on next
	// This is a mix between the node's utility and the amount of visits it has
	public double selectfn(Node node, boolean isMax) {
		if(isMax)
			return -node.avgUtility + explorationConstant*Math.sqrt(2*Math.log(node.parent.visits)/node.visits);
		else
			return node.avgUtility + explorationConstant*Math.sqrt(2*Math.log(node.parent.visits)/node.visits);
	}

	// Calculates the value of our given node
	// Simulates a random playthrough until a terminal state, and returns the terminal state's reward
	public double simulation(Node node) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {

		if(node.isMax)
			logger.log(Level.WARNING, "Error. Should only simulate on the Max Nodes");

		if(node.moves == null) return theMachine.findReward(role, node.state);
		else{
			MachineState terminal = node.state;
			terminal = theMachine.performSafeDepthCharge(node.state.clone(), finishBy);
			return theMachine.findReward(role, terminal);
		}
	}

	// Backpropagate through our tree and update all nodes
	public void backPropagate(Node node, double[] scores) {
		node.update(scores);

		if(node.parent != null)
			backPropagate(node.parent, scores);
	}

}
