package org.ggp.base.player.gamer.statemachine.assignment3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class BoundedDepthPlayer extends SampleGamer {

	protected int limit = 10;
	protected long finishBy = 0;
	Logger logger = Logger.getLogger(getClass().getSimpleName());

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();
		finishBy = timeout - 2000; //Finish with 2 seconds remaining

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		moves = new ArrayList<Move>(moves);
		Collections.shuffle(moves); //In case we don't finish, this makes us semi-random

		Move bestMove = moves.get(0);
		int bestScore = 0;
		if(moves.size() > 1) //Don't bother computing anything if we have no choice
			for (Move move: moves) {
				if (System.currentTimeMillis() > finishBy)
			        break;
				int result = getMinScore(getRole(), move, getCurrentState(), 0, 100, 1);
				if(result == 100) {
					bestMove = move;
					break;
				}
				if (result > bestScore) {
					bestScore = result;
					bestMove = move;
				}
			}

		long stop = System.currentTimeMillis();
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));

		if( stop > finishBy ) { //decrease our depth limit if we took too long
			logger.log(Level.WARNING, "Ran out of time! \n\tBest score: " + bestScore);
			//limit = Math.max(2, limit - 1);
			//logger.log(Level.INFO, String.format("Limit is now: %d", limit));
		}
		else if (moves.size() > 1){ //Time used is not important if we didn't compute anything
			double timeUsed = (stop-start)*100.0/(finishBy-start); //A percentage of the time used
			logger.log(Level.INFO, String.format("Time used: %f. \n Best score: %d", timeUsed, bestScore));
			if (timeUsed < 50) { //increase our depth limit if we were quick
				//limit++;
				//logger.log(Level.INFO, String.format("Limit is now: %d", limit));
			}
		}

		return bestMove;
	}

	protected int getMinScore(Role role, Move move, MachineState state, int alpha, int beta, int level) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<List<Move>> jointMovesList = getStateMachine().getLegalJointMoves(state, role, move);
		for (int i = 0; i < jointMovesList.size(); i++) {
			if (System.currentTimeMillis() > finishBy) return beta;

			List<Move> jointMoves = jointMovesList.get(i);
			MachineState nextState = getStateMachine().getNextState(state, jointMoves);
			int result = getMaxScore(role, nextState, alpha, beta, level+1);
			beta = Math.min(beta, result);
			if (beta <= alpha) return alpha;
		}
		return beta;
	}

	protected int getMaxScore(Role role, MachineState state, int alpha, int beta, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(state)) return getStateMachine().getGoal(state, role);
		if (depthLimit(role,state,level)) return stateEvaluation(role, state);
		List<Move> moves = getStateMachine().getLegalMoves(state, role);
		for (Move move: moves) {
			if (System.currentTimeMillis() > finishBy) return alpha;

			int result = getMinScore(role, move, state, alpha, beta, level);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) return beta;
		}
		return alpha;
	}

	// This function is for adaptively determining the depth limit based on state.
	// Right now it keeps the default limit.
	protected boolean depthLimit(Role role, MachineState state, int level) {
		return level >= limit;
	}

	// Evaluate the current state. Add in some heuristics here.
	protected int stateEvaluation(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException {
		return 0;
	}

}