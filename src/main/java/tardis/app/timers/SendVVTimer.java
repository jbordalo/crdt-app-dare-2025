package tardis.app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class SendVVTimer extends ProtoTimer {

	public final static short TIMER_ID = 9587;

	public SendVVTimer() {
		super(TIMER_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

}
