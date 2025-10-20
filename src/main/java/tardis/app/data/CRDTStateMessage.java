package tardis.app.data;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.crdts.delta.causal.implementations.DeltaAWORSet;
import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaORSet;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class CRDTStateMessage extends ProtoMessage {
    public final static short MSG_CODE = 3184;

    private DeltaORSet state;

    public CRDTStateMessage(DeltaORSet state) {
        super(CRDTStateMessage.MSG_CODE);
        this.state = state;
    }

    public DeltaORSet getState() {
        return this.state;
    }

    public static final ISerializer<CRDTStateMessage> serializer = new ISerializer<CRDTStateMessage>() {
        @Override
        public void serialize(CRDTStateMessage msg, ByteBuf out) throws IOException {
            msg.getState().serialize(out);
        }

        @Override
        public CRDTStateMessage deserialize(ByteBuf in) throws IOException {
            DeltaORSet state = DeltaORSet.serializer.deserialize(in);
            return new CRDTStateMessage(state);
        }
    };

}
