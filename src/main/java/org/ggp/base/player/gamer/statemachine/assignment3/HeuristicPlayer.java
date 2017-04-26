package org.ggp.base.player.gamer.statemachine.assignment3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class HeuristicPlayer extends BoundedDepthPlayer {

	protected HashMap<String, Double> weightMap = new HashMap<String, Double>(); // A map of heuristic weights
	protected HashMap<String, Boolean> heuristics = new HashMap<String, Boolean>(); // A map of heuristic choices (i.e focus vs. mobility)

	protected HashMap<Role, ArrayList<MachineState>> perfectStates = new HashMap<Role, ArrayList<MachineState>>(); //States with 100 end values
	protected HashMap<Role, ArrayList<MachineState>> goodStates = new HashMap<Role, ArrayList<MachineState>>(); //States with large end values
	protected HashMap<Role, ArrayList<Integer>> goodScores = new HashMap<Role, ArrayList<Integer>>(); //The stored large end values

	@Override
	//Play a meta game to decide on some initial parameters (depth limit)
	public void stateMachineMetaGame(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		finishBy = timeout - 1000; //Leave one second left

		setupPlayer();

		List<Role> roles = getStateMachine().getRoles();
		//First initialize the values for each role
		for(Role role: roles) {
			//Reset the terminal states we found last time
			perfectStates.put(role, new ArrayList<MachineState>());
			goodStates.put(role, new ArrayList<MachineState>());
			goodScores.put(role, new ArrayList<Integer>());
		}

		//Don't use more time than the play clock allows for
		modelChoices.put("limit", findCurrentLimit(Math.min(finishBy,
				System.currentTimeMillis() + getMatch().getPlayClock()*1000 - 2000)));
		modelChoices.put("limit", Math.max(2, modelChoices.get("limit")-2)); // Just in case something goes wrong
	}

	//Setup our player
	protected void setupPlayer() {
		logger = Logger.getLogger(getClass().getSimpleName());
		modelChoices.put("steps", 1);
		weightMap.put("timeForTerminal", 0.3);
		heuristics.put("goodOppProx", false); //Whether we would like the opponent to have a high value on this or not
		heuristics.put("goodOppSim", false);
		heuristics.put("useOppFocus", true);
		heuristics.put("useFocus", false);
		modelChoices.put("maxTerminalStates", 20);

		// Default Game parameters
		List<Role> roles = getStateMachine().getRoles();
		if(roles.size() == 1) {
			weightMap.put("proximity", 0.70);
			weightMap.put("similarityProximity", 0.10);
			weightMap.put("oppSimilarityProximity", 0.0);
			weightMap.put("movement", 0.20);
			weightMap.put("oppMovement", 0.00);
			weightMap.put("oppProximity", 0.00);
		} else {
			weightMap.put("proximity", 0.50);
			weightMap.put("similarityProximity", 0.10);
			weightMap.put("oppSimilarityProximity", 0.10);
			weightMap.put("movement", 0.15);
			weightMap.put("oppMovement", 0.05);
			weightMap.put("oppProximity", 0.10);
		}
	}

	// Do breadth-first search to set current depth limit
	protected int findCurrentLimit(long finishBy) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		int level = 0; //Current level
		ArrayList<MachineState> stateQueue = new ArrayList<MachineState>(); // States to check in the current level
		stateQueue.add(getCurrentState());

		Role role = getRole();

		while(System.currentTimeMillis() < finishBy && stateQueue.size() > 0) {
			//Only check a deeper level if we have time and states left
			level++;
			int currentSize = stateQueue.size(); //How many states to check for this level

			outerloop:
			for(int i = 0; i< currentSize; i++) { //Iterate through each state

				MachineState currentState = stateQueue.remove(0);
				List<Move> myMoves = getStateMachine().getLegalMoves(currentState, role);

				for (Move move: myMoves) {
					List<List<Move>> jointMoves = getStateMachine().getLegalJointMoves(currentState, role, move);

					for (List<Move> jointMove: jointMoves) {
						if(System.currentTimeMillis() > finishBy)
							break outerloop;
						MachineState nextState = getStateMachine().getNextState(currentState, jointMove);

						// Checking the goal and evaluation here is not stored, it is only to mimic what actually happens
						if(!getStateMachine().isTerminal(nextState)){
							stateQueue.add(nextState);
							stateEvaluation(role, nextState);
						}
						else
							getStateMachine().getGoal(nextState, role);
					}
				}
			}
		}
		logger.log(Level.INFO, String.format("New limit is: %d", level));
		return level;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();
		//Finish with 2 seconds remaining
		finishBy = timeout - 2000;

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		moves = new ArrayList<Move>(moves);
		Collections.shuffle(moves); //In case we don't finish, this makes us semi-random

		Move bestMove = moves.get(0);

		if(moves.size() > 1) { //Don't bother computing anything if we have no choice in moves
			//First find some terminal states (This may be useful somewhere).
			findTerminalStates( (long) (start + (finishBy-start)*weightMap.get("timeForTerminal")));

			Move tempBestMove = moves.get(0);
			int initialLimit = modelChoices.get("limit"); //Reset the limit afterwards, in the case of a 1P game.

			//Iterative Deepening
			while(System.currentTimeMillis() < finishBy) {

				tempBestMove = findBestMove(moves);
				if(tempBestMove != null)
					bestMove = tempBestMove;
				modelChoices.put("limit", modelChoices.get("limit")+1);
			}
			logger.log(Level.INFO, String.format("Searched up to limit %d ", modelChoices.get("limit")));
			modelChoices.put("limit", initialLimit);

		}else {
			// Right now rechecks the best limit to use
			// Only use as much time as would normally be available after finding terminal states
			finishBy = (long) (start + (finishBy-start)*(1-weightMap.get("timeForTerminal")));

			//A guess at what level to look at. Minus as a buffer.
			modelChoices.put("limit", Math.max(2, findCurrentLimit(finishBy) - 2));
		}

		long stop = System.currentTimeMillis();
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	//Finds the best move from a given list
	protected Move findBestMove(List<Move> moves) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		Move bestMove = moves.get(0);
		int bestScore = 0;
		for (Move move: moves) {
			// Store the current best move
			int result = getMinScore(getRole(), move, getCurrentState(), 0, 100, 0);
			if(result == 100) {
				bestMove = move;
				break;
			}
			if (result > bestScore) {
				bestScore = result;
				bestMove = move;
			}
			if (System.currentTimeMillis() > finishBy) {
				bestMove = null; //Placeholder to basically say we have not completed this search
		        break;
			}
		}
		return bestMove;

	}

	//Returns self mobility or focus score
	protected int movementValue(Role role, MachineState state, boolean useFocus, int steps) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {

		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		List<Move> feasibles = getStateMachine().findActions(role);
		double score = actions.size()*100.0/feasibles.size();

		//This part is in case we would like to consider another step as well
		double terminalScore = 0;
		int statesConsidered = 1;
		int termStatesConsidered = 0;

		ArrayList<MachineState> stateQueue = new ArrayList<MachineState>();
		stateQueue.add(state);
		for(int i = 1; i < steps; i++){
			int currentSize = stateQueue.size();
			for(int j = 0; j< currentSize; j++) {
				List<List<Move>> jointMovesList = getStateMachine().getLegalJointMoves(stateQueue.remove(0));

				for(List<Move> jointMove: jointMovesList){
					MachineState nextState = getStateMachine().getNextState(state, jointMove);
					if(!getStateMachine().isTerminal(nextState)) {
						actions = getStateMachine().getLegalMoves(nextState, role);
						score += actions.size()*100.0/feasibles.size();
						stateQueue.add(nextState);
						statesConsidered++;
					}
					else {
						terminalScore += getStateMachine().getGoal(nextState, role);
						termStatesConsidered++;
					}
				}
			}
		}
		score /= statesConsidered;
		if(useFocus)
			score = 100 - score;
		return (int)(score*statesConsidered + terminalScore)/(statesConsidered+termStatesConsidered);
	}

	//Returns average opponent mobility or focus score
	protected int opponentMovementValue(Role role, MachineState state, boolean useFocus, int steps) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {

		int score = 0;
		List<Role> roles = getStateMachine().getRoles();
		if(roles.size() > 1) {
			for( Role opp_role: roles) {
				if(!opp_role.equals(role))
					score += movementValue(opp_role, state, useFocus, steps);
			}
			score /= (roles.size()-1);
		}
		return score;
	}

	// Evaluate the current state based on a weighted combination of heuristics
	@Override
	protected int stateEvaluation(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {

		// Self Values
		int movementScore = movementValue(role, state, heuristics.get("useFocus"), modelChoices.get("steps"));
		int proximityScore = proximityValue(role, state);
		int similarityScore = similarityValue(role, state);

		// Opponent Values
		int opponentMovementScore = opponentMovementValue(role, state, heuristics.get("useOppFocus"), modelChoices.get("steps"));
		int opponentProximityScore = opponentProximityValue(role, state, heuristics.get("goodOppProx"));
		int oppSimilarityScore = opponentSimilarityValue(role, state, heuristics.get("goodOppSim"));

		// Weighted final value
		return (int)(weightMap.get("proximity")*proximityScore + weightMap.get("movement")*movementScore +
				weightMap.get("oppMovement")*opponentMovementScore + weightMap.get("oppProximity")*opponentProximityScore +
				weightMap.get("oppSimilarityProximity")*oppSimilarityScore + weightMap.get("similarityProximity")*similarityScore);
	}

	// Caches potential terminal states for each role
	protected void findTerminalStates(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int counter = 0;
		int newStatesMax = modelChoices.get("maxTerminalStates"); //Maximum number of new states we would look for
		if(System.currentTimeMillis() < timeout) {
			int[] depth = new int[1];
			List<Role> roles = getStateMachine().getRoles();

			//First initialize the values for each role
			for(Role role: roles) {
				//Reset the terminal states we found last time
				perfectStates.put(role, new ArrayList<MachineState>());
				goodStates.put(role, new ArrayList<MachineState>());
				goodScores.put(role, new ArrayList<Integer>());
			}

			MachineState currentState = getCurrentState();
			while(System.currentTimeMillis() < timeout) {
				MachineState finalState = getStateMachine().performDepthCharge(currentState, depth); //Find a random terminal state

				HashMap<Role, Integer> playerScores = new HashMap<Role, Integer> ();
				int scoreAvg = 0;

				//Find scores for the current terminal state
				for(Role role: roles) {
					int currentScore = getStateMachine().getGoal(finalState, role);
					scoreAvg += currentScore;
					playerScores.put(role, currentScore);
				}
				scoreAvg /= roles.size();

				//Now add in good states for each role
				boolean haveEnough = true; //This will remain true unless we find a role which does not have enough states
				for(Role role: roles) {
					if(playerScores.get(role) == 100 && perfectStates.get(role).size() < newStatesMax) {
						ArrayList<MachineState> currentStates = perfectStates.get(role);
						currentStates.add(finalState);
						perfectStates.put(role, currentStates);

					} else if(playerScores.get(role) >= 50 && playerScores.get(role) >= scoreAvg && goodStates.get(role).size() < newStatesMax){
						ArrayList<MachineState> currentStates = goodStates.get(role);
						currentStates.add(finalState);
						goodStates.put(role, currentStates);

						ArrayList<Integer> currentScores = goodScores.get(role);
						currentScores.add(playerScores.get(role));
						goodScores.put(role, currentScores);
					}
					if(perfectStates.get(role).size() < newStatesMax || goodStates.get(role).size() < newStatesMax)
						haveEnough = false;
				}
				if(haveEnough)
					break;
				counter++;
			}

			//Print final sizes
			for(Role role: roles)
				logger.log(Level.INFO, String.format("Role %s found (perfect, good) states = (%d, %d)",
						role.toString(), perfectStates.get(role).size(), goodStates.get(role).size()));
			logger.log(Level.INFO, String.format("States Checked: %d", counter));
		}
	}

	//F-1 Score as a measure of similarity
	protected int similarity(MachineState state1, MachineState state2) {

		Set<GdlSentence> set1 = state1.getContents();
		Set<GdlSentence> set2 = state2.getContents();
		Iterator<GdlSentence> iterator = set1.iterator();
		int similar = 0;
		while(iterator.hasNext())
			if(set2.contains(iterator.next()))
				similar++;
		return (int) (200.0*similar/(set1.size()+set2.size()));
	}

	//Returns average proximity value of opponents
	protected int opponentProximityValue(Role role, MachineState state, boolean isCoop) throws MoveDefinitionException, GoalDefinitionException {

		int score = 0;
		List<Role> roles = getStateMachine().getRoles();
		if(roles.size() > 1) {
			for( Role opponent: roles) {
				if(opponent.equals(role)) {
					continue;
				}
				score += proximityValue(opponent, state);
			}
			score /= (roles.size()-1);
		}
		if(!isCoop)
			score = 100 - score;

		return score;
	}

	//Returns average proximity value of opponents
	protected int opponentSimilarityValue(Role role, MachineState state, boolean isCoop) throws MoveDefinitionException, GoalDefinitionException {

		int score = 0;
		List<Role> roles = getStateMachine().getRoles();
		if(roles.size() > 1) {
			for( Role opponent: roles) {
				if(opponent.equals(role)) {
					continue;
				}
				score += similarityValue(opponent, state);
			}
			score /= (roles.size()-1);
		}
		if(!isCoop)
			score = 100 - score;

		return score;
	}

	// Returns the current goal value
	protected int proximityValue(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException {
		int score = getStateMachine().getGoal(state, role);
		return score;
	}

	// Returns the similarity to cached terminal states
	protected int similarityValue(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException {
		int similarityScore = 0;

		List<MachineState> goodStates2 = goodStates.get(role);
		List<MachineState> perfectStates2 = perfectStates.get(role);
		List<Integer> goodScores2 = goodScores.get(role);

		if (perfectStates2.size()+goodStates2.size() > 0) {
			for(int i = 0; i< perfectStates2.size(); i++)
				similarityScore += similarity(perfectStates2.get(i), state);
			for(int i = 0; i< goodStates2.size(); i++)
				similarityScore += goodScores2.get(i)/100.0*similarity(goodStates2.get(i), state);
			similarityScore /= perfectStates2.size()+goodStates2.size();
		}
		return similarityScore;
	}
}
