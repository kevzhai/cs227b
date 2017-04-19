import java.util.ArrayList;
import java.util.List;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
public class MiniMaxPlayer extends StateMachineGamer {

	@Override
	public StateMachine getInitialStateMachine() {
		// TODO Auto-generated method stub
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
		long start = System.currentTimeMillis();

		Role role = getRole();
		MachineState currentState = getCurrentState();
		List<Move> moves = getStateMachine().getLegalMoves(currentState, role);
		Move bestMove = moves.get(0);
		int bestScore = 0;
		for (Move move: moves) {
			int result = getMinScore(role, move, currentState);
			if (result > bestScore) {
				bestScore = result;
				bestMove = move;
			}
		}

		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	private int getMinScore(Role role, Move move, MachineState state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		// just one opponent for now
		// https://piazza.com/class/ixl3tqyvphf6fu?cid=72
		StateMachine machine = getStateMachine();
		Role opponent = machine.getRoles().get(1);
		List<Move> opponentMoves = machine.getLegalMoves(state, opponent);
		int score = 100;
		for (Move opponentMove: opponentMoves) {
			List<Move> moves = new ArrayList<Move>();
			moves.add(move);
			moves.add(opponentMove);
			// getNextState() returns the next state of the game given the current state and a joint move list containing one move per role.
			MachineState nextState = machine.getNextState(state, moves);
			int result = getMaxScore(role, nextState);
			if (result < score) {
				score = result;
			}
		}
		return score;
	}

	private int getMaxScore(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine machine = getStateMachine();
		if (getStateMachine().isTerminal(state)) {
			return machine.getGoal(state, role);
		}
		List<Move> moves = machine.getLegalMoves(state, role);
		int score = 0;
		for (Move move: moves) {
			// Minimax alternates between getting max and min scores
			int result = getMinScore(role, move, state);
			if (result > score) {
				score = result;
			}
		}
		return score;
	}

	@Override
	public DetailPanel getDetailPanel() {
		return new SimpleDetailPanel();
	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Kilimanjaro MiniMax";
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub

	}

}
