package tardis.app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class BroadcastStateTimer extends ProtoTimer {

	public final static short PROTO_ID = 9879;
	
	public BroadcastStateTimer() {
		super(PROTO_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

}
