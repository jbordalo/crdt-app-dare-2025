package tardis.app;

import java.net.InetAddress;
import java.util.Iterator;

import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaORSet;
import pt.unl.fct.di.novasys.babel.crdts.utils.ReplicaID;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.StringType;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.SerializableType;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;

public class Test3 {

    private static int totalBytesSent = 0;

    private static int calcSize(DeltaORSet set) {
        int size = 0;
        Iterator<SerializableType> it = set.iterator();
        while (it.hasNext()) {
            StringType s = (StringType) it.next();
            String v = s.getValue();
            if (v != null)
                size += v.getBytes().length;
        }
        return size;
    }

    private static void dump(String lbl, DeltaORSet s) {
        StringBuilder sb = new StringBuilder();
        sb.append(lbl).append(" [");
        Iterator<SerializableType> it = s.iterator();
        while (it.hasNext()) {
            sb.append(((StringType) it.next()).getValue()).append(", ");
        }
        if (sb.lastIndexOf(", ") > 0)
            sb.setLength(sb.length() - 2);
        sb.append("]");
        System.out.println(sb);
    }

    private static void sendDelta(String lbl, DeltaORSet from, DeltaORSet to) {
        DeltaORSet delta = from.generateDelta(to.getReplicaState().getVV());

        // print delta contents
        dump("Delta "+lbl+" :", delta);

        int deltaSize = calcSize(delta);
        int totalSize = calcSize(from);
        totalBytesSent += deltaSize;
        double pct = totalSize == 0 ? 0 : (100.0 * deltaSize / totalSize);
        System.out.printf("%s: sending %d bytes (%.1f%% of total)%n", lbl, deltaSize, pct);

        to.mergeDelta(delta);
    }

    public static void main(String[] args) throws Exception {
        Peer p1 = new Peer(InetAddress.getByName("0.0.0.0"), 1);
        Peer p2 = new Peer(InetAddress.getByName("0.0.0.1"), 2);
        Peer p3 = new Peer(InetAddress.getByName("0.0.0.2"), 3);

        DeltaORSet crdt1 = new DeltaORSet(new ReplicaID(p1));
        DeltaORSet crdt2 = new DeltaORSet(new ReplicaID(p2));
        DeltaORSet crdt3 = new DeltaORSet(new ReplicaID(p3));

        System.out.println("=== Test3: Simple Delta Traffic ===");

        crdt1.add(new StringType("A"));
        crdt1.add(new StringType("B"));
        dump("CRDT 1 after adds: ", crdt1);
        sendDelta("1→2", crdt1, crdt2);
        sendDelta("1→3", crdt1, crdt3);

        System.out.println("=== After sending deltas ===");
        dump("CRDT 2: ", crdt2);
        dump("CRDT 3: ", crdt3);

        crdt2.add(new StringType("C"));
        dump("CRDT 2 after add: ", crdt2);
        sendDelta("2→3", crdt2, crdt3);

        System.out.println("\n=== Final States ===");
        dump("CRDT1", crdt1);
        dump("CRDT2", crdt2);
        dump("CRDT3", crdt3);

        System.out.println("\n=== Traffic Statistics ===");
        System.out.printf("Total bytes sent: %d%n", totalBytesSent);
    }
}
