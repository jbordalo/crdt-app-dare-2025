package tardis.app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class AntiEntropyPeriodTimer extends ProtoTimer {

	public final static short TIMER_ID = 9777;
	
	public AntiEntropyPeriodTimer() {
		super(TIMER_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

}
