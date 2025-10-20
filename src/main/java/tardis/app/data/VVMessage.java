package tardis.app.data;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.crdts.utils.ordering.VersionVector;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class VVMessage extends ProtoMessage {
    public final static short MSG_CODE = 3122;

    private VersionVector vv;

    public VVMessage(VersionVector vv) {
        super(VVMessage.MSG_CODE);
        this.vv = vv;
    }

    public VersionVector getVV() {
        return this.vv;
    }

    public static final ISerializer<VVMessage> serializer = new ISerializer<VVMessage>() {
        @Override
        public void serialize(VVMessage msg, ByteBuf out) throws IOException {
            VersionVector.serializer.serialize(msg.getVV(), out);
        }

        @Override
        public VVMessage deserialize(ByteBuf in) throws IOException {
            return new VVMessage(VersionVector.serializer.deserialize(in));
        }
    };

}
