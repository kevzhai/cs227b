package org.ggp.base.player.gamer.statemachine.assignment2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * CompulsiveDeliberationGamer searches the current game tree and
 * selects the move leading to the best terminal state.
 *
 */
public final class CompulsiveDeliberationPlayer extends SampleGamer
{
	/**
	 * This function is called at the start of each round
	 * You are required to return the Move your player will play
	 * before the timeout.
	 *
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// We get the current start time
		long start = System.currentTimeMillis();


		// These get reused a lot, so store them
		MachineState currentState = getCurrentState();
		StateMachine theMachine = getStateMachine();

		List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
		Move bestMove = moves.get(0);
		int bestScore = 0;
		for(int i = 0; i< moves.size(); i++)
		{
			List<Move> currentMove = new ArrayList<Move>(Arrays.asList(moves.get(i)));
			MachineState nextState = theMachine.getNextState(currentState, currentMove);

			int result = maxScore(nextState, getRole());
			if(result == 100)
				return moves.get(i);
			if(result > bestScore)
			{
				bestScore = result;
				bestMove = moves.get(i);
			}
		}

		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	// Returns the maximum possible score from the given state and role
	public int maxScore(MachineState state, Role role) throws TransitionDefinitionException, GoalDefinitionException, MoveDefinitionException
	{
		StateMachine theMachine = getStateMachine();

		if(theMachine.isTerminal(state))
			return theMachine.getGoal(state, role);
		List<Move> moves = theMachine.getLegalMoves(state, role);
		int bestScore = 0;
		for(int i = 0; i< moves.size(); i++)
		{
			List<Move> currentMove = new ArrayList<Move>(Arrays.asList(moves.get(i)));
			MachineState nextState = theMachine.getNextState(state, currentMove);

			int result = maxScore(nextState, role);
			if(result == 100)
				return result;
			if(result > bestScore)
				bestScore = result;
		}
		return bestScore;
	}
}