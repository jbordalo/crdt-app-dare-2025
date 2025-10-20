package tardis.app;

import java.net.InetAddress;
import java.util.Iterator;

import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaORSet;
import pt.unl.fct.di.novasys.babel.crdts.utils.ReplicaID;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.StringType;
import pt.unl.fct.di.novasys.babel.crdts.utils.ordering.VersionVector;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.SerializableType;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;

public class TestBug {
    private static void dumpState(String label, DeltaORSet crdt) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" [");
        int count = 0;
        Iterator<SerializableType> it = crdt.iterator();
        while (it.hasNext()) {
            StringType s = (StringType) it.next();
            sb.append(s.getValue());
            sb.append(", ");
            count++;
        }
        if (count > 0)
            sb.setLength(sb.length() - 2);
        sb.append("] (").append(count).append(" items)");
        System.out.println(sb.toString());
    }

    public static void main(String[] args) throws Exception {
        Peer p1 = new Peer(InetAddress.getByName("0.0.0.0"), 1);
        Peer p2 = new Peer(InetAddress.getByName("0.0.0.1"), 2);

        DeltaORSet crdt1 = new DeltaORSet(new ReplicaID(p1));
        DeltaORSet crdt2 = new DeltaORSet(new ReplicaID(p2));
        /////////
        crdt2.add(new StringType("Marowak"));
        VersionVector v1 = crdt1.getReplicaState().getVV();
        VersionVector v2 = crdt2.getReplicaState().getVV();
        var delta = crdt2.generateDelta(v1);
        crdt1.mergeDelta(delta);
        crdt1.remove(new StringType("Marowak"));
        delta = crdt1.generateDelta(v2); 
        crdt2.mergeDelta(delta);

        // delta = crdt2.generateDelta(crdt1.getReplicaState().getVV()); 
        // crdt1.mergeDelta(delta);
        
        // delta = crdt1.generateDelta(crdt2.getReplicaState().getVV()); 
        // crdt2.mergeDelta(delta);

        System.out.println("\n=== Final State ===");
        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);
    }
}
