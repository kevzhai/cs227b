package org.ggp.base.player.gamer.statemachine.assignment3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class VariableDepthPlayer extends ProximityPlayer {

	@Override
	//Play a meta game to decide on some initial parameters (depth limit)
	public void stateMachineMetaGame(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		modelChoices.put("limit", 10); // Just in case something goes wrong
		finishBy = timeout - 1000; //Leave one second left

		setupPlayer();
		//Don't use more time than the play clock allows for
		modelChoices.put("limit", findCurrentLimit(timeout, Math.min(finishBy,
				System.currentTimeMillis() + getMatch().getPlayClock()*1000 - 2000)));
	}

	//Setup our player
	@Override
	protected void setupPlayer() {
		logger = Logger.getLogger(getClass().getSimpleName());
		modelChoices.put("limit", 6);
		weightMap.put("similarityProximityWeight", 0.5); //How much to weigh current goal value vs similarity to terminal states
		weightMap.put("timeForTerminal", 0.2); //The portion of time spent on finding terminal states
		heuristics.put("useSimilarityProximity", true); //Use either the state's goal value or mix it with a similarity to terminal sates
		heuristics.put("isCoop", false); //Whether we want to hurt our opponent's score or not.

		//Default Game parameters
		List<Role> roles = getStateMachine().getRoles();
		if(roles.size() == 1) {
			weightMap.put("proximity", 1.00); //Weight for proximity heuristic
			weightMap.put("oppProximity", 0.00); //Weight for opponent's proximity
		} else {
			weightMap.put("proximity", 0.80);
			weightMap.put("oppProximity", 0.20);
		}
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
		int bestScore = 0;

		if(moves.size() > 1) { //Don't bother computing anything if we have no choice in moves
			//First find some terminal states (This may be useful somewhere).
			findTerminalStates( (long) (start + (finishBy-start)*weightMap.get("timeForTerminal")));

			for (Move move: moves) {
				if (System.currentTimeMillis() > finishBy)
			        break;

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
			}
		}else {
			// TODO: do something productive
			// Right now rechecks the best limit to use
			// Only use as much time as would normally be available after finding terminal states
			logger.log(Level.INFO, weightMap+"");
			finishBy = (long) (start + (finishBy-start)*(1-weightMap.get("timeForTerminal")));
			modelChoices.put("limit", findCurrentLimit(timeout, finishBy));
		}

		long stop = System.currentTimeMillis();
		if(moves.size() > 1){ //If we didn't have to look through moves, these values don't mean much.
			if( stop > finishBy  )
				logger.log(Level.WARNING, "Ran out of time! \n\tBest score: " + bestScore);
			else {
				int timeUsed = (int) ((stop-start)*100.0/(finishBy-start)); //A percentage of the time used
				logger.log(Level.INFO, String.format("Time used: %d. \n Best score: %d", timeUsed, bestScore));
			}
		}

		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}


	// Do breadth-first search to set current depth limit
	protected int findCurrentLimit(long timeout, long finishBy) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
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
							getStateMachine().getGoal(nextState, role);
						}
						else
							stateEvaluation(role, nextState);
					}
				}
			}
		}
		logger.log(Level.INFO, String.format("New limit is: %d", level));
		logger.log(Level.INFO, String.format("Time left is: %.2f sec", (timeout - System.currentTimeMillis())/1000.0));
		return level;
	}


}
