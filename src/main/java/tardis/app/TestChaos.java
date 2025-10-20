package tardis.app;

import java.net.InetAddress;
import java.util.Iterator;

import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaORSet;
import pt.unl.fct.di.novasys.babel.crdts.utils.ReplicaID;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.StringType;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.SerializableType;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;

public class TestChaos {
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

        System.out.println("=== Initial Adds ===");
        crdt1.add(new StringType("A"));
        crdt1.add(new StringType("B"));
        crdt2.add(new StringType("X"));
        crdt2.add(new StringType("Y"));
        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);

        System.out.println("\n=== First Merge ===");
        DeltaORSet delta1to2 = crdt1.generateDelta(crdt2.getReplicaState().getVV());

        crdt2.mergeDelta(delta1to2);

        DeltaORSet delta2to1 = crdt2.generateDelta(crdt1.getReplicaState().getVV());
        crdt1.mergeDelta(delta2to1);

        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);

        System.out.println("\n=== Interleaved Adds/Removes ===");
        crdt1.add(new StringType("C"));
        crdt2.remove(new StringType("X"));
        crdt1.remove(new StringType("A"));
        crdt2.add(new StringType("Z"));
        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);

        System.out.println("\n=== Second Merge ===");
        delta1to2 = crdt1.generateDelta(crdt2.getReplicaState().getVV());
        crdt2.mergeDelta(delta1to2);

        delta2to1 = crdt2.generateDelta(crdt1.getReplicaState().getVV());
        crdt1.mergeDelta(delta2to1);

        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);

        System.out.println("\n=== More Operations ===");
        crdt1.add(new StringType("D"));
        crdt2.add(new StringType("E"));
        crdt2.remove(new StringType("Y"));
        crdt1.remove(new StringType("B"));

        delta1to2 = crdt1.generateDelta(crdt2.getReplicaState().getVV());
        crdt2.mergeDelta(delta1to2);
        delta2to1 = crdt2.generateDelta(crdt1.getReplicaState().getVV());
        crdt1.mergeDelta(delta2to1);

        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);

        System.out.println("\n=== Final State ===");
        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);
    }
}
