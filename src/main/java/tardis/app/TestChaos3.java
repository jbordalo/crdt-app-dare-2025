package tardis.app;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Random;

import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaORSet;
import pt.unl.fct.di.novasys.babel.crdts.utils.ReplicaID;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.StringType;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.SerializableType;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;

public class TestChaos3 {

    private static void dumpState(String label, DeltaORSet crdt) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" [");
        int count = 0;
        Iterator<SerializableType> it = crdt.iterator();
        while (it.hasNext()) {
            StringType s = (StringType) it.next();
            sb.append(s.getValue()).append(", ");
            count++;
        }
        if (count > 0) sb.setLength(sb.length() - 2);
        sb.append("] (").append(count).append(" items)");
        System.out.println(sb);
    }

    public static void main(String[] args) throws Exception {
        Peer p1 = new Peer(InetAddress.getByName("0.0.0.0"), 1);
        Peer p2 = new Peer(InetAddress.getByName("0.0.0.1"), 2);
        Peer p3 = new Peer(InetAddress.getByName("0.0.0.2"), 3);

        DeltaORSet crdt1 = new DeltaORSet(new ReplicaID(p1));
        DeltaORSet crdt2 = new DeltaORSet(new ReplicaID(p2));
        DeltaORSet crdt3 = new DeltaORSet(new ReplicaID(p3));

        Random rnd = new Random();
        String[] elems = {"A","B","C","D","E","F","G","H","I","J"};

        System.out.println("=== Chaotic Interleaving Test (3 Replicas) ===");

        for (int i = 0; i < 40; i++) {
            int action = rnd.nextInt(9);
            String e = elems[rnd.nextInt(elems.length)];

            switch (action) {
                case 0 -> { System.out.println("crdt1.add(" + e + ")"); crdt1.add(new StringType(e)); }
                case 1 -> { System.out.println("crdt2.add(" + e + ")"); crdt2.add(new StringType(e)); }
                case 2 -> { System.out.println("crdt3.add(" + e + ")"); crdt3.add(new StringType(e)); }
                case 3 -> { System.out.println("crdt1.remove(" + e + ")"); crdt1.remove(new StringType(e)); }
                case 4 -> { System.out.println("crdt2.remove(" + e + ")"); crdt2.remove(new StringType(e)); }
                case 5 -> { System.out.println("crdt3.remove(" + e + ")"); crdt3.remove(new StringType(e)); }
                case 6 -> {
                    System.out.println("Merge 1→2");
                    var d = crdt1.generateDelta(crdt2.getReplicaState().getVV());
                    crdt2.mergeDelta(d);
                }
                case 7 -> {
                    System.out.println("Merge 2→3");
                    var d = crdt2.generateDelta(crdt3.getReplicaState().getVV());
                    crdt3.mergeDelta(d);
                }
                case 8 -> {
                    System.out.println("Merge 3→1");
                    var d = crdt3.generateDelta(crdt1.getReplicaState().getVV());
                    crdt1.mergeDelta(d);
                }
            }

            if (i % 10 == 0) {
                System.out.println("---- Intermediate State ----");
                dumpState("CRDT1", crdt1);
                dumpState("CRDT2", crdt2);
                dumpState("CRDT3", crdt3);
                System.out.println("-----------------------------");
            }
        }

        System.out.println("\n=== Final Full Merge ===");
        // Merge all replicas in both directions
        crdt2.mergeDelta(crdt1.generateDelta(crdt2.getReplicaState().getVV()));
        crdt3.mergeDelta(crdt2.generateDelta(crdt3.getReplicaState().getVV()));
        crdt1.mergeDelta(crdt3.generateDelta(crdt1.getReplicaState().getVV()));

        dumpState("CRDT1", crdt1);
        dumpState("CRDT2", crdt2);
        dumpState("CRDT3", crdt3);
    }
}
