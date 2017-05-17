package org.ggp.base.player.gamer.statemachine.assignment6;

import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;

//Does a bit from than the other SimulationThread.
//Will only keep this thread in future iterations.
public class SimulationThreadV2 extends Thread {
   Node root;
   StateMachine theMachine;
   long finishBy;
   double[] result = new double[2];
   boolean opponent = false;
   boolean useWinRatio = false;
   Role role = null;
   List<Role> roles = null;

   SimulationThreadV2(  Node inNode, StateMachine inMachine, long inFinishBy, boolean useOpponent, Role inRole, boolean winRatio ) {
      root = inNode;
      theMachine = inMachine;
      finishBy = inFinishBy;
      role = inRole;
      useWinRatio = winRatio;
      roles = theMachine.getRoles();
      start();
   }

   //Performs a depth charge
   @Override
   public void run() {
		MachineState terminal = root.state;

		if(root.moves != null)
			try {
				terminal = theMachine.performSafeDepthCharge(root.state.clone(), finishBy);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		try {
			result = getResult(terminal);
		} catch (GoalDefinitionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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

	public double[] getResult(MachineState terminal) throws GoalDefinitionException {
		double[] result = new double[2];
		result[0] = theMachine.findReward(role, terminal);

		/* For a small speedup, we only compute this if we want the win ratio (need both scores)
		 * or if we want to use the greedy opponent model (also need both scores).
		 * This is unused for the minimax and terminal score choices. */
		if(opponent)
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
}