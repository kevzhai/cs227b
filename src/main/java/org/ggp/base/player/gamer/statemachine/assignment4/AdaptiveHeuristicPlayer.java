package org.ggp.base.player.gamer.statemachine.assignment4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.assignment3.HeuristicPlayer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class AdaptiveHeuristicPlayer extends HeuristicPlayer {

	@Override
	//Play a meta game to decide on some initial parameters (depth limit)
	public void stateMachineMetaGame(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		finishBy = timeout - 3000; //Leave one second left

		setupPlayer( finishBy ); //Setup the initial heuristic weights

		modelChoices.put("limit", 1);

		//This heuristic is for early terminal of the iterative deepening.
		//Basically if we do not see a terminal state, we have not reached the end, and stop is false.
		heuristics.put("stop", false);
//		if( System.currentTimeMillis() > timeout )
//			logger.log(Level.WARNING, "TIMEOUT meta:" + (timeout - System.currentTimeMillis()) +" "+(timeout-finishBy));
	}

	// Setup our player with variable weights
	// Run random games and see how each move corresponds to each heuristic
	// Calculates the correlation between each heuristic and good terminal states
	protected void setupPlayer(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		logger = Logger.getLogger(getClass().getSimpleName());
		modelChoices.put("steps", 1);
		weightMap.put("timeForTerminal", 0.3);
		modelChoices.put("maxTerminalStates", 20);

		//First find some terminal states for the similarity heuristics
		findTerminalStates( (long) (System.currentTimeMillis() + (timeout-System.currentTimeMillis())*weightMap.get("timeForTerminal")));

		StateMachine theMachine = getStateMachine();
		Role role = getRole();

		//Store the values of each heuristic at every state
		ArrayList<Double> movement = new ArrayList<Double>();
		ArrayList<Double> proximity = new ArrayList<Double>();
		ArrayList<Double> oppMovement = new ArrayList<Double>();
		ArrayList<Double> oppProximity = new ArrayList<Double>();
		ArrayList<Double> similarity = new ArrayList<Double>();
		ArrayList<Double> oppSimilarity = new ArrayList<Double>();
		ArrayList<Integer> goals = new ArrayList<Integer>();

		int terminalStates = 0;
		while(System.currentTimeMillis() < timeout) {
			int states = 0;
			MachineState state = getCurrentState();

			//Do one step at a time, and store the heuristic values at those steps
			while( !theMachine.isTerminal(state) && proximity.size() < 30000 && System.currentTimeMillis() < timeout ) {
				proximity.add(proximityValue(role, state)*1.0);
				movement.add(movementValue(role, state, false, modelChoices.get("steps"))*1.0);
				oppProximity.add(opponentProximityValue(role, state, true)*1.0);
				oppMovement.add(opponentMovementValue(role, state, false, modelChoices.get("steps"))*1.0);
				similarity.add(similarityValue(role, state)*1.0);
				oppSimilarity.add(opponentSimilarityValue(role, state, true)*1.0);

	            state = theMachine.getNextStateDestructively(state, theMachine.getRandomJointMove(state));
	            states++;
	        }
			//Get into a terminal state if we have time
			if(!theMachine.isTerminal(state) && System.currentTimeMillis() < timeout )
				state = performSafeDepthCharge(state, timeout);
			terminalStates++;

			//Correlate each of the values seen so far with the terminal state
			int finalScore = theMachine.getGoal(state, role);
			for(int i = 0;i < states; i++)
				goals.add(finalScore);
		}
		//Correlation values
		double proximityCorr = correlation(proximity, goals);
		double movementCorr = correlation(movement, goals);
		double similarityCorr = correlation(similarity, goals);
		double oppProximityCorr = 0;
		double oppMovementCorr = 0;
		double oppSimilarityCorr = 0;

		if(theMachine.getRoles().size() > 1) {
			oppProximityCorr = correlation(oppProximity, goals);
			oppMovementCorr = correlation(oppMovement, goals);
			oppSimilarityCorr = correlation(oppSimilarity, goals);
		}
		logger.log(Level.INFO, String.format("The correlations after %d values and %d terminal states for (prox, move, sim, oppProx, oppMove, oppSim) are \n(%.2f, %.2f, %.2f, %.2f, %.2f, %.2f)",
				goals.size(), terminalStates, proximityCorr,movementCorr, similarityCorr, oppProximityCorr, oppMovementCorr, oppSimilarityCorr));

		//These heuristics define whether the correlations are positive or negative.
		if(oppProximityCorr > 0)
			heuristics.put("goodOppProx", true);
		else
			heuristics.put("goodOppProx", false);

		if(oppSimilarityCorr > 0)
			heuristics.put("goodOppSim", true);
		else
			heuristics.put("goodOppSim", false);

		if(oppMovementCorr > 0)
			heuristics.put("useOppFocus", false);
		else
			heuristics.put("useOppFocus", true);

		if(movementCorr > 0)
			heuristics.put("useFocus", false);
		else
			heuristics.put("useFocus", true);

		//Normalize weights
		double total = Math.abs(proximityCorr)+Math.abs(movementCorr)+Math.abs(oppMovementCorr)+Math.abs(oppProximityCorr)
				+Math.abs(oppSimilarityCorr)+Math.abs(similarityCorr);
		if(total < 0.001) { //This might happen in the last few moves of the game, where you basically don't have much choice in moves
			proximityCorr = 0.90;
			similarityCorr = 0.10;
			total = 1.0;
		}

		//Store the weights
		weightMap.put("proximity", Math.abs(proximityCorr)/total );
		weightMap.put("movement", Math.abs(movementCorr)/total);
		weightMap.put("similarityProximity", Math.abs(similarityCorr)/total);

		weightMap.put("oppMovement", Math.abs(oppMovementCorr)/total);
		weightMap.put("oppProximity", Math.abs(oppProximityCorr)/total);
		weightMap.put("oppSimilarityProximity", Math.abs(oppSimilarityCorr)/total);

		logger.log(Level.INFO, "The current heuristics are: " + heuristics);
		logger.log(Level.INFO, "The current weights are: " + weightMap);
		logger.log(Level.INFO, "The current model choices are: " + modelChoices);
		if( System.currentTimeMillis() > timeout )
			logger.log(Level.WARNING, "TIMEOUT Setup:" + (timeout - System.currentTimeMillis()) +" "+(timeout-finishBy));
	}

	//Overrides to stop iterative deepening if it does not find a terminal state
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();
		//Finish with 2 seconds remaining
		finishBy = timeout - 2500;

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		moves = new ArrayList<Move>(moves);
		Collections.shuffle(moves); //In case we don't finish, this makes us semi-random

		Move bestMove = moves.get(0);

		if(moves.size() > 1) { //Don't bother computing anything if we have no choice in moves
			//First find some terminal states (This may be useful somewhere).
			findTerminalStates( (long) (start + (finishBy-start)*weightMap.get("timeForTerminal")));

			Move tempBestMove = moves.get(0);
			modelChoices.put("limit", 1);
			heuristics.put("stop", false);
			//Iterative Deepening
			while(System.currentTimeMillis() < finishBy && !heuristics.get("stop")) {
				heuristics.put("stop", true);
				tempBestMove = findBestMove(moves);
				if(tempBestMove != null)
					bestMove = tempBestMove;
				modelChoices.put("limit", modelChoices.get("limit")+1);

//				if( System.currentTimeMillis() > timeout )
//					logger.log(Level.WARNING, "TIMEOUT Finding Move:" + (timeout - System.currentTimeMillis()) +" "+(timeout-finishBy));
			}
			logger.log(Level.INFO, String.format("Searched up to limit %d ", modelChoices.get("limit")));
		}else {
			setupPlayer( finishBy );
		}

		long stop = System.currentTimeMillis();
		logger.log(Level.INFO, "Time taken:" + (stop-start));
		if( stop > timeout )
			logger.log(Level.WARNING, "TIMEOUT Move:" + (stop-start) +" "+(timeout-finishBy) + " " + (stop-finishBy));
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	//Calculates the Pearson correlation between two arraylists.
	protected double correlation(ArrayList<Double> list1, ArrayList<Integer> list2) {

		double mean1 = 0;
		double mean2 = 0;
		double moment1 = 0;
		double moment2 = 0;
		for(int i = 0; i < list1.size(); i++) {
			mean1 +=  list1.get(i);
			mean2 +=  list2.get(i);
			moment1 +=  Math.pow(list1.get(i),2);
			moment2 +=  Math.pow(list2.get(i),2);
		}
		// Get expectations
		if(list1.size() > 0) {
			mean1 /= 1.0*list1.size();
			mean2 /= 1.0*list2.size();
			moment1 /= 1.0*list1.size();
			moment2 /= 1.0*list2.size();
		}

		//Get standard deviations
		double std1 = Math.sqrt(Math.max(0, moment1 - Math.pow(mean1, 2))); //If numbers are basically equal, floats may cause issues
		double std2 = Math.sqrt(Math.max(0, moment2 - Math.pow(mean2, 2)));

		//If we have something that's basically a constant, just ignore it.
		if(std1 < 0.001 || std2 < 0.001)
			return 0.00;

		double corr = 0;
		double stds = std1*std2;
		for(int i = 0; i < list1.size(); i++) {
			corr += (list1.get(i) - mean1) * (list2.get(i) - mean2) / stds;
		}
		corr /= list1.size()*1.0;
		return corr;
	}

	//Overridden so that it does not look for legal moves unless there is sufficient time.
	@Override
	protected int getMinScore(Role role, Move move, MachineState state, int alpha, int beta, int level) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (System.currentTimeMillis() > finishBy) return beta;
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

	//Overridden so that iterative deepening will not continue unless it has not seen a terminal state
	@Override
	protected int getMaxScore(Role role, MachineState state, int alpha, int beta, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (System.currentTimeMillis() > finishBy) return alpha;
		if (getStateMachine().isTerminal(state)) return getStateMachine().getGoal(state, role);
		if (depthLimit(role,state,level))  {
			heuristics.put("stop", false);
			return stateEvaluation(role, state);
		}
		List<Move> moves = getStateMachine().getLegalMoves(state, role);
		for (Move move: moves) {
			if (System.currentTimeMillis() > finishBy) return alpha;

			int result = getMinScore(role, move, state, alpha, beta, level);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) return beta;
		}
		return alpha;
	}

	// Overrides to perform a safer depth charge
	@Override
	protected void findTerminalStates(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int counter = 0;
		int newStatesMax = modelChoices.get("maxTerminalStates"); //Maximum number of new states we would look for
		if(System.currentTimeMillis() < timeout) {
			List<Role> roles = getStateMachine().getRoles();

			//First initialize the values for each role
			for(Role role: roles) {
				//Reset the terminal states we found last time
				perfectStates.put(role, new ArrayList<MachineState>());
				goodStates.put(role, new ArrayList<MachineState>());
				goodScores.put(role, new ArrayList<Integer>());
			}
			StateMachine theMachine = getStateMachine();
			MachineState currentState = getCurrentState();
			while(System.currentTimeMillis() < timeout) {
				MachineState finalState = performSafeDepthCharge(currentState, timeout); //Find a random terminal state

//				if( System.currentTimeMillis() > timeout )
//					logger.log(Level.WARNING, "TIMEOUT In Find after depth:" + (timeout - System.currentTimeMillis()) +" "+(timeout-finishBy));

				HashMap<Role, Integer> playerScores = new HashMap<Role, Integer> ();
				int scoreAvg = 0;

				//Find scores for the current terminal state
				for(Role role: roles) {
					int currentScore = theMachine.getGoal(finalState, role);
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
//			if( System.currentTimeMillis() > timeout )
//				logger.log(Level.WARNING, "TIMEOUT In Find:" + (timeout - System.currentTimeMillis()) +" "+(timeout-finishBy));
		}
	}

	//This depth charge only performs a move if it has time
	//This helps prevent timeouts on very long games with relatively low play clocks
    public MachineState performSafeDepthCharge(MachineState state, long timeout) throws TransitionDefinitionException, MoveDefinitionException {
        StateMachine theMachine = getStateMachine();
        while(!theMachine.isTerminal(state) && System.currentTimeMillis() < timeout) {
            long start = System.currentTimeMillis();
            state = theMachine.getNextStateDestructively(state, theMachine.getRandomJointMove(state));
            long stop = System.currentTimeMillis();
            if(stop > timeout){
            	logger.log(Level.WARNING, "TIMEOUT safe: " + (stop-start));
            }

        }
        return state;
    }
}
