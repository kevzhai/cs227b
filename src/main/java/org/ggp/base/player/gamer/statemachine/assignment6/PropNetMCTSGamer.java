package org.ggp.base.player.gamer.statemachine.assignment6;

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
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;

/* This improves on the GenericMCTS gamer. It uses a propnet instead of the normal state machine.
 * It also adaptively changes the exploration constant to be the std of seen values, with a default of 20.
 * Finally, it uses multithreading to perform more depth charges.
 */
public class PropNetMCTSGamer extends SampleGamer {

	private long finishBy; //Global timeout variable
	private final Logger logger = Logger.getLogger(getClass().getSimpleName());

	/*This variable should be set to true if we want to use the minimax model (opponent is out to hurt us)
	 *Set this to false to use the greedy opponent model (opponent is out to profit himself)
	 *For zero-sum games there is no difference. For >2P games minimax is currently better.
	 *For 2P non-zero-sum games, the greedy model is better. For 1P it doesn't matter.*/
	private final boolean useMiniMax = false;

	/*This variable should be set to true if we want to use the the win ratio for our depth charges.
	 *This means good states are those where we score higher than the opponent (but we may have low scores).
	 *This should be set to false if we want to use the terminal goal for our depth charges (we may lose but have a high score).
	 *Looks like win ratio is good in some games (alquerque) and worse in others (skirmish).
	 *Overall, using win ratio helps us win (good for zero sum games) but we win with low scores (bad for arena).
	 *Using win ratio may have issues with some 1P games where the max score is not 100.*/
	private final boolean useWinRatio = false;

	//Other variables used throughout the gamer.
	private StateMachine theMachine;
	private ArrayList<StateMachine> extraMachines;
	private Role role;
	private List<Role> roles;
	private Node root; //A pointer to the root node
	private boolean firstMove; //Indicates whether we are performing our first move or not (for caching correctly from meta game)
	private Random random; //To select random moves when needed

	//Play a meta game to decide on some initial parameters (depth limit)
	@Override
	public void stateMachineMetaGame(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		finishBy = timeout - 3000; //Leave 3s left

		try{
			initializeVariables();
		}catch(InterruptedException e) {
			logger.log(Level.WARNING, "Error building Prop Net.");
		}

		// Run the MCTS so we have some data when the game starts
		runMCTS();

		logger.log(Level.INFO, "Initial Depth charges:" + root.visits);
	}

	@Override
	public StateMachine getInitialStateMachine() {
    	return new CachedStateMachine(new SamplePropNetStateMachine());
//    	return new CachedStateMachine(new ProverStateMachine());
    }


	// Initialize variables
	private void initializeVariables() throws MoveDefinitionException, InterruptedException {
		long start = System.currentTimeMillis();
		theMachine = getStateMachine();
		long stop = System.currentTimeMillis();

		//Create at most 5 extra machines
		extraMachines = new ArrayList<StateMachine>();
		while(System.currentTimeMillis() + (stop-start) < finishBy && extraMachines.size() < 5) {
			extraMachines.add(getInitialStateMachine());
			extraMachines.get(extraMachines.size()-1).initialize(getMatch().getGame().getRules());
		}
		logger.log(Level.INFO, String.format("Created %d machines for multithreading.", extraMachines.size()));

		role = getRole();
		roles = theMachine.getRoles();
		firstMove = true;
		root = new Node(getCurrentState(), null, null, null, false);

		List<Move> moves = theMachine.getLegalMoves(getCurrentState(), role);
		root.moves = new ArrayList<Move> ( moves );
		random = new Random();

	}

	// Runs MCTS for as long as time allows
	public void runMCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		while( System.currentTimeMillis() < finishBy ) {
			Node newNode = select(root);
			Node newGrandchild = expand(newNode);
			simulation(newGrandchild);
//			backPropagate(newGrandchild, scores);
		}
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();
		//Finish with 2.5 seconds remaining
		finishBy = timeout - 2500;

		//Get the initial state and moves
		//Can probably remove this later
		MachineState currentState = getCurrentState();
		List<Move> moves = theMachine.getLegalMoves(currentState, role);
//		logger.log(Level.INFO, "" + moves);
//		logger.log(Level.INFO, "" + currentState);
		Move bestMove = moves.get(random.nextInt(moves.size()));

		//Update the tree based on the opponent's move
		//If this is not the first move, then we use our cached tree and update based on whatever the opponent selected
		if(!firstMove) {
			boolean foundChild = false;
			for(Node child: root.children) {
				if(child.state.equals(currentState)) {
					child.parent = null;
					root = child;
					foundChild = true;
				}
			}
			if(!foundChild) { //This is if we did not explore every grandchild. Start a new tree
				root = new Node(currentState, null, null, null, false);
				root.moves = new ArrayList<Move> ( moves );
				logger.log(Level.WARNING, "Warning. Did not run enough states, starting new tree.");
			}
		}

		// Run the MCTS
		// Even if we only have one move (noop) this will play and build up our tree for later
		runMCTS();

		// Select the best move
		double bestScore = Double.NEGATIVE_INFINITY;
		double bestUtility = 0;

		/* Don't know the best way to select this value. This value is for selecting the move with the best bound,
		 * and not simply the best score. A zero here means we are only considering the score.
		 * Try larger values and negative values, pos. values are optimistic, neg. values are pessimistic.*/
		double boundConstant = 0;
		for(Node child: root.children) {
			double curScore = child.avgUtility + boundConstant/Math.sqrt(child.visits);
			if(child.visits > 0 && curScore > bestScore) {
				bestScore = curScore;
				bestMove = child.move;
				bestUtility = child.avgUtility;
			}
		}

		logger.log(Level.INFO, "Depth charges:" + root.visits);
		logger.log(Level.INFO, "Best Score:" + (bestScore));
		logger.log(Level.INFO, "Best Utility:" + (bestUtility));
		logger.log(Level.INFO, String.format("Root (std, oppstd): (%.2f, %.2f)", root.std,root.oppStd));

		//Update the Game tree depending on what our current move is
		for(Node child: root.children) {
			if(child.move != null && child.move.equals(bestMove)) {
				child.parent = null;
				root = child;
			}
		}

		firstMove = false; //This should never be true again
		long stop = System.currentTimeMillis();
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	// Selects a node to focus on next
	// First visits unselected nodes, else uses selectfn to choose a node
	private Node select(Node node) {
		if(!node.isMax && ( node.moves == null || node.moves.size() > 0) ) return node; //Is terminal or no new children to make

		if(node.isMax && node.jointMoves.size() > 0) { //create new grandChild
			return node;
		}

		double bestScore = Double.NEGATIVE_INFINITY;
		Node result = node;

		for(Node child: node.children) {
			double newScore = selectfn(child, node.isMax, node.std, node.oppStd);
			if(newScore > bestScore) {
				bestScore = newScore;
				result = child;
			}
		}
		return select(result);
	}

	// Adds more nodes to our tree
	// Only does this if we are not on a terminal state
	public Node expand(Node node) throws MoveDefinitionException, TransitionDefinitionException {
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
	public double selectfn(Node node, boolean opponent, double explorationConstant, double oppExplorationConstant) {
		if(useMiniMax) { //Here the opponent tries to hurt us
			if(opponent)
				return -node.avgUtility + explorationConstant*Math.sqrt(2*Math.log(node.parent.visits)/node.visits);
			else
				return node.avgUtility + explorationConstant*Math.sqrt(2*Math.log(node.parent.visits)/node.visits);
		}else {
			if(opponent) //Here the opponent tries to better himself
				return node.oppAvgUtility + oppExplorationConstant*Math.sqrt(2*Math.log(node.parent.visits)/node.visits);
			else
				return node.avgUtility + explorationConstant*Math.sqrt(2*Math.log(node.parent.visits)/node.visits);
		}
	}

	// Calculates the value of our given node
	// Simulates a random playthrough until a terminal state, and returns the terminal state's reward or whether we won or not
	public void simulation(Node root) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		if(root.isMax)
			logger.log(Level.WARNING, "Error. Should only simulate on the Max Nodes");

		SimulationThread thread = new SimulationThread(root, theMachine, finishBy);
		ArrayList<SimulationThread> threads = new ArrayList<SimulationThread>();
		for(StateMachine machine: extraMachines)
			threads.add(new SimulationThread(root, machine, finishBy));
		while(thread.isAlive()){
			//Wait
		}

		double[] result = getResult(thread.terminal);
		backPropagate(root, result);

		for(SimulationThread thr: threads) {
			while(thr.isAlive()){
				//wait
			}
			result = getResult(thr.terminal);
			backPropagate(root, result);
		}
	}

	public double[] getResult(MachineState terminal) throws GoalDefinitionException {
		double[] result = new double[2];
		result[0] = theMachine.findReward(role, terminal);



		/* For a small speedup, we only compute this if we want the win ratio (need both scores)
		 * or if we want to use the greedy opponent model (also need both scores).
		 * This is unused for the minimax and terminal score choices. */
		if(useWinRatio || !useMiniMax)
			result[1] = avgOpponentScore(terminal);

		//Calculate whether the game was won or not
		if(useWinRatio) {
			if(roles.size() == 1){ //If this is a 1P game, winning means a score of >90.
				if(result[0] < 90)
					result[0] = 0.0;
				if(result[0] > 90)
					result[0] = 100.0;
			} else{  //If this is a 2P game, winning means a score higher than the opponent's.
				if(Math.abs(result[0] - result[1]) < 0.1) { //This is because we are storing in doubles
					result[0] = 50.0;
					result[1] = 50.0;
				}
				else if(result[0] > result[1]) {
					result[0] = 100.0;
					result[1] = 0.0;
				} else {
					result[0] = 0.0;
					result[1] = 100.0;
				}
			}
		}

		return result;
	}

	//Averages ALL opponent score.
	//Might be better to use MAX instead of Average for >2P games.
	public double avgOpponentScore(MachineState state) throws GoalDefinitionException {
		double result = 0;
		if(roles.size() > 1) {
			for(Role curRole: roles)
				if(!curRole.equals(role))
					result += theMachine.findReward(curRole, state);
//					result = Math.max(result, theMachine.findReward(curRole, state));
			result /= (roles.size()-1);
		}
		return result;
	}

	// Backpropagate through our tree and update all nodes
	public void backPropagate(Node node, double[] scores) {

		node.update(scores);

		if(node.parent != null)
			backPropagate(node.parent, scores);
	}



}
