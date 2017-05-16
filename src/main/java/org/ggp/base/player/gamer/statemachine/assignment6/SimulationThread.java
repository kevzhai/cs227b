package org.ggp.base.player.gamer.statemachine.assignment6;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.StateMachine;

public class SimulationThread extends Thread {
   MachineState terminal;
   Node root;
   StateMachine theMachine;
   long finishBy;

   SimulationThread(  Node inNode, StateMachine inMachine, long inFinishBy ) {
      root = inNode;
      theMachine = inMachine;
      finishBy = inFinishBy;
      start();
   }

   //Performs a depth charge
   @Override
   public void run() {
		terminal = root.state;

		if(root.moves != null)
			try {
				terminal = theMachine.performSafeDepthCharge(root.state.clone(), finishBy);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
   }
}