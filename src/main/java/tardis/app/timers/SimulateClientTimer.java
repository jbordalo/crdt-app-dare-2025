package tardis.app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class SimulateClientTimer extends ProtoTimer {

	public final static short TIMER_ID = 9999;
	
	public SimulateClientTimer() {
		super(TIMER_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

}
