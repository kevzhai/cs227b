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

public class ProximityPlayer extends BoundedDepthPlayer {


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
	}

	//Setup our player
	protected void setupPlayer() {
		logger = Logger.getLogger(getClass().getSimpleName());
		modelChoices.put("limit", 6);
		weightMap.put("similarityProximityWeight", 0.5); //How much to weigh current goal value vs similarity to terminal states
		weightMap.put("timeForTerminal", 0.2); //The portion of time spent on finding terminal states
		heuristics.put("useSimilarityProximity", true); //Use either the state's goal value or mix it with a similarity to terminal sates
		heuristics.put("isCoop", false); //Whether we want to hurt our opponent's score or not.
		modelChoices.put("maxTerminalStates", 10);

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

	protected void findTerminalStates(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int counter = 0;
		int newStatesMax = modelChoices.get("maxTerminalStates"); //Maximum number of new states we would look for
		if(System.currentTimeMillis() < timeout && heuristics.get("useSimilarityProximity")) {
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

	//F-1 Score
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

	// Returns the current goal value
	protected int proximityValue(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException {
		int score = getStateMachine().getGoal(state, role);
		int similarityScore = 0;

		List<MachineState> goodStates2 = goodStates.get(role);
		List<MachineState> perfectStates2 = perfectStates.get(role);
		List<Integer> goodScores2 = goodScores.get(role);

		if (heuristics.get("useSimilarityProximity") && perfectStates2.size()+goodStates2.size() > 0) {
			for(int i = 0; i< perfectStates2.size(); i++)
				similarityScore += similarity(perfectStates2.get(i), state);
			for(int i = 0; i< goodStates2.size(); i++)
				similarityScore += goodScores2.get(i)/100.0*similarity(goodStates2.get(i), state);
			similarityScore /= perfectStates2.size()+goodStates2.size();

			score = (int)(weightMap.get("similarityProximityWeight")*score+
					(1-weightMap.get("similarityProximityWeight"))*similarityScore);
		}
		return score;
	}

	// Evaluate the current state based on a weighted combination of heuristics
	@Override
	protected int stateEvaluation(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {

		// Self Values
		int proximityScore = proximityValue(role, state);

		// Opponent Values
		int opponentProximityScore = opponentProximityValue(role, state, heuristics.get("isCoop"));

		// Weighted final value
		return (int)(weightMap.get("proximity")*proximityScore + weightMap.get("oppProximity")*opponentProximityScore);
	}

}
