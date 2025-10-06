package tardis.app.data;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaLWWSet;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class CRDTStateMessage extends ProtoMessage {
    public final static short MSG_CODE = 3184;

    private DeltaLWWSet state;

    public CRDTStateMessage(DeltaLWWSet state) {
        super(CRDTStateMessage.MSG_CODE);
        this.state = state;
    }

    public DeltaLWWSet getState() {
        return this.state;
    }

    public static final ISerializer<CRDTStateMessage> serializer = new ISerializer<CRDTStateMessage>() {
        @Override
        public void serialize(CRDTStateMessage msg, ByteBuf out) throws IOException {
            msg.getState().serialize(out);
        }

        @Override
        public CRDTStateMessage deserialize(ByteBuf in) {
            DeltaLWWSet state = null;
            try {
                state = DeltaLWWSet.serializer.deserialize(in);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                System.err.println("I FAILED _HERE");
                e.printStackTrace();
            }
            return new CRDTStateMessage(state);
        }
    };

}
