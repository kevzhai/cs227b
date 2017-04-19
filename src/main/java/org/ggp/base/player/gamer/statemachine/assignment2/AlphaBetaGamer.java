package org.ggp.base.player.gamer.statemachine.assignment2;

import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class AlphaBetaGamer extends SampleGamer {

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move bestMove = moves.get(0);
		int bestScore = 0;
		for (Move move: moves) {
			int result = getMinScore(getRole(), move, getCurrentState(), 0, 100);
			if (result > bestScore) {
				bestScore = result;
				bestMove = move;
			}
		}

		long stop = System.currentTimeMillis();
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	private int getMinScore(Role role, Move move, MachineState state, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
	   /* var opponent = findopponent(role,game);
		  var actions = findlegals(opponent,state,game);
		  for (var i=0; i<actions.length; i++) {
		  	   var move;
		       if (role==roles[0]) {
		          move = [action,actions[i]]}
		       else {
		          move = [actions[i],action]
		       }
		       var newstate = findnext(move,state,game);
		       var result = maxscore(role,newstate,alpha,beta);
		       beta = min(beta,result);
		       if (beta<=alpha) then {
		       	  return alpha
		       }
		   };
		   return beta */
		List<List<Move>> jointMovesList = getStateMachine().getLegalJointMoves(state, role, move);
		for (int i = 0; i < jointMovesList.size(); i++) {
			List<Move> jointMoves = jointMovesList.get(i);
			MachineState nextState = getStateMachine().getNextState(state, jointMoves);
			int result = getMaxScore(role, nextState, alpha, beta);
			beta = Math.min(beta, result);
			if (beta <= alpha) return alpha;
		}
		return beta;
	}

	private int getMaxScore(Role role, MachineState state, int alpha, int beta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		/* if (findterminalp(state,game)) {
				return findreward(role,state,game)};
		  		var actions = findlegals(role,state,game);
		  		for (var i=0; i<actions.length; i++) {
		      		var result = minscore(role,actions[i],state,alpha,beta);
		       		alpha = max(alpha,result);
		       		if (alpha>=beta) then {
		       			return beta
		       		}
		       	};
		  return alpha */

		if (getStateMachine().isTerminal(state)) return getStateMachine().getGoal(state, role);
		List<Move> moves = getStateMachine().getLegalMoves(state, role);
		for (Move move: moves) {
			int result = getMinScore(role, move, state, alpha, beta);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) return beta;
		}
		return alpha;
	}

}
