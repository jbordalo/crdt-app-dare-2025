package tardis.app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class SendStateTimer extends ProtoTimer {

	public final static short TIMER_ID = 9789;
	
	public SendStateTimer() {
		super(TIMER_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

}
