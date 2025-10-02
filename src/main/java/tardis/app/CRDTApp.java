package tardis.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaLWWSet;
import pt.unl.fct.di.novasys.babel.crdts.utils.ReplicaID;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.SerializableType;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.StringType;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.Metric.Unit;
import pt.unl.fct.di.novasys.babel.metrics.StatsGauge;
import pt.unl.fct.di.novasys.babel.metrics.StatsGauge.StatType;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.requests.BroadcastRequest;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.app.command.Command;
import tardis.app.data.Card;
import tardis.app.timers.AppWorkloadGenerateTimer;
import tardis.app.timers.BroadcastStateTimer;

public class CRDTApp extends GenericProtocol {

	public final static String PROTO_NAME = "CRDT Mock Application";
	public final static short PROTO_ID = 9898;

	public final static String STATE_SIZE_METRIC = "averageStateSize";
	public final static String TIME_MERGING_METRIC = "averageTimeMerging";

	private boolean generateWorkload;

	public final static String PAR_WORKLOAD_PERIOD = "app.workload.period";
	public final static String PAR_WORKLOAD_SIZE = "app.workload.payload";
	public final static String PAR_GENERATE_WORKLOAD = "app.workload.on";

	public final static long DEFAULT_WORKLOAD_PERIOD = 10 * 1000; // 10 seconds
	public final static int DEFAULT_WORKLOAD_SIZE = 63000;

	public final static String PAR_WORKLOAD_PROBABILITY = "app.workload.probability";
	public final static double DEFAULT_WORKLOAD_PROBABILITY = 1.0;

	public final static String PAR_BCAST_PROTOCOL_ID = "app.bcast.id";

	public final static String PAR_BCAST_INIT_ENABLED = "app.bcast.enable";
	public final static boolean DEFAULT_BCAST_INIT_ENABLED = true;

	public final static String PAR_MANAGEMENT_PORT = "app.management.port";
	private static final String PAR_MANAGEMENT_THREAD = "app.management";

	public final static int DEFAULT_MANAGEMENT_PORT = Command.DEFAULT_MANAGE_PORT;

	private long workloadPeriod;
	private double generateMessageProbability;

	private short bcastProtoID;

	private final Host myself;

	private Logger logger = LogManager.getLogger(CRDTApp.class);

	private AtomicBoolean executing;

	private String nodeLabel;

	private Thread managementThread;

	private DeltaLWWSet crdt;
	private ArrayList<StringType> localLog;
	private StatsGauge averageTimeMerging;
	private StatsGauge averageStateSize;

	public CRDTApp(Host myself) throws HandlerRegistrationException {
		super(CRDTApp.PROTO_NAME, CRDTApp.PROTO_ID);

		this.myself = myself;
		Peer peer = new Peer(myself.getAddress(), myself.getPort());
		this.crdt = new DeltaLWWSet(new ReplicaID(peer));

		this.localLog = new ArrayList<>();

		try {
			this.nodeLabel = myself.getAddress().getHostName().split(".")[3];
		} catch (Exception e) {
			this.nodeLabel = myself.getAddress().getHostName();
		}

		this.averageStateSize = registerMetric(
				new StatsGauge.Builder(CRDTApp.STATE_SIZE_METRIC, Unit.BYTES).statTypes(StatType.AVG).build());
		this.averageTimeMerging = registerMetric(new StatsGauge.Builder(CRDTApp.TIME_MERGING_METRIC, "ms")
				.statTypes(StatType.AVG, StatType.MAX).build());

		registerTimerHandler(AppWorkloadGenerateTimer.PROTO_ID, this::handleAppWorkloadGenerateTimer);
		registerTimerHandler(BroadcastStateTimer.PROTO_ID, this::handleBroadcastStateTimer);
		subscribeNotification(BroadcastDelivery.NOTIFICATION_ID, this::handleMessageDeliveryEvent);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		if (props.containsKey(PAR_BCAST_PROTOCOL_ID)) {
			this.bcastProtoID = Short.parseShort(props.getProperty(PAR_BCAST_PROTOCOL_ID));
			logger.debug("CRDTApp is configured to use broadcast protocol with id: " + this.bcastProtoID);
		} else {
			logger.error("The application requires the id of the broadcast protocol being used. Parameter: '"
					+ PAR_BCAST_PROTOCOL_ID + "'");
			System.exit(1);
		}

		this.generateWorkload = props.containsKey(PAR_GENERATE_WORKLOAD);

		if (this.generateWorkload) {
			if (props.containsKey(PAR_WORKLOAD_PERIOD))
				this.workloadPeriod = Long.parseLong(props.getProperty(PAR_WORKLOAD_PERIOD));
			else
				this.workloadPeriod = DEFAULT_WORKLOAD_PERIOD;

			if (props.containsKey(PAR_WORKLOAD_PROBABILITY))
				this.generateMessageProbability = Double.parseDouble(props.getProperty(PAR_WORKLOAD_PROBABILITY));
			else
				this.generateMessageProbability = DEFAULT_WORKLOAD_PROBABILITY;

			setupPeriodicTimer(new AppWorkloadGenerateTimer(), this.workloadPeriod, this.workloadPeriod);
			logger.debug("CRDTApp has workload generation enabled.");
		} else
			logger.debug("CRDTApp has workload generation disabled.");

		setupPeriodicTimer(new BroadcastStateTimer(), this.workloadPeriod * 5, this.workloadPeriod * 5);

		boolean b = DEFAULT_BCAST_INIT_ENABLED;

		if (props.containsKey(PAR_BCAST_INIT_ENABLED)) {
			b = Boolean.parseBoolean(props.getProperty(PAR_BCAST_INIT_ENABLED));
		}

		this.executing = new AtomicBoolean(b);

		// If app.management is present but set to false, don't start the management
		// thread
		if (props.containsKey(PAR_MANAGEMENT_THREAD)
				&& !Boolean.parseBoolean(props.getProperty(PAR_MANAGEMENT_THREAD))) {
			return;
		}

		int mPort = Command.DEFAULT_MANAGE_PORT;
		if (props.containsKey(PAR_MANAGEMENT_PORT)) {
			mPort = Integer.parseInt(props.getProperty(PAR_MANAGEMENT_PORT));
		}

		final int managementPort = mPort;

		this.managementThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try (ServerSocket sock = new ServerSocket(managementPort)) {

					logger.debug("Management socket at: {}", sock.getLocalSocketAddress().toString());

					while (true) {
						final Socket c = sock.accept();

						new Thread(() -> handleManagementCommand(c)).start();
					}
				} catch (IOException e1) {
					e1.printStackTrace();
					System.exit(1);
				}
			}
		});

		logger.info("Starting management socket.");
		this.managementThread.start();
	}

	private void handleManagementCommand(Socket c) {
		try {
			InputStream input = c.getInputStream();
			Scanner sc = new Scanner(input);
			PrintStream out = new PrintStream(c.getOutputStream());

			String cmd = sc.nextLine();
			logger.info("Management Command: " + cmd);

			if (cmd.equalsIgnoreCase(Command.CMD_START)) {
				float probability = Float.parseFloat(sc.nextLine());

				if (updateAndStartMessages(probability)) {
					out.println(Command.REPLY_SUCESS);
				} else {
					out.println(Command.REPLY_FAILURE);
				}
			} else if (cmd.equalsIgnoreCase(Command.CMD_STOP)) {
				if (stopMessages()) {
					out.println(Command.REPLY_SUCESS);
				} else {
					out.println(Command.REPLY_FAILURE);
				}
			} else if (cmd.equalsIgnoreCase(Command.CMD_FAIL)
					|| cmd.equalsIgnoreCase(Command.CMD_LEAVE)) {
				out.println(Command.REPLY_SUCESS);
				out.flush();
				System.exit(0);
			}

			sc.close();
			out.close();
			input.close();
		} catch (Exception e) {
			System.out.println("Client command has failed: " + e.getMessage());
			e.printStackTrace(System.out);
		}
	}

	public final boolean updateAndStartMessages(float probability) {
		logger.debug("Start messages request with {} probability", probability);

		this.generateMessageProbability = (double) probability;

		if (!this.executing.getAcquire()) {
			this.enableTransmission();
		}
		return true;
	}

	public final boolean stopMessages() {
		logger.debug("Stop messages request");

		if (this.executing.getAcquire()) {
			this.disableTransmissions();
			return true;
		} else {
			return false;
		}
	}

	private void handleAppWorkloadGenerateTimer(AppWorkloadGenerateTimer t, long time) {
		if (!this.executing.getAcquire())
			return;

		if (this.generateMessageProbability < 1.0 && new Random().nextDouble() > this.generateMessageProbability)
			return; // We have a probabibility for doing an action

		// 80% chance of buying, 20% of selling, so the state keeps growing steadily
		// That said, removing still grows state :)
		if (new Random().nextDouble() > 0.8) {
			// SELL
			if (localLog.size() == 0) {
				return;
			}

			int randomSell = ThreadLocalRandom.current().nextInt(localLog.size());
			StringType sold = this.localLog.get(randomSell);
			DeltaLWWSet delta = crdt.remove(sold);
			Iterator<SerializableType> it = delta.iterator();
			if (it.hasNext()) {
				localLog.remove((StringType) it.next());
				logger.info("Sold card {}", sold);
			}
		} else {
			// BUY
			StringType card = new StringType(Card.randomCard().toString());
			DeltaLWWSet delta = this.crdt.add(card);
			Iterator<SerializableType> it = delta.iterator();
			if (it.hasNext()) {
				localLog.add((StringType) it.next());
				logger.info("Bought card {}", card);
			}
		}
		logger.debug("CRDTApp generating a card.");
	}

	private void handleBroadcastStateTimer(BroadcastStateTimer t, long time) {
		ByteBuf in = Unpooled.buffer();

		try {
			// Send my state
			this.crdt.serialize(in);
		} catch (IOException e) {
			logger.error("Failed to serialize CRDT");
			e.printStackTrace();
		}

		int stateSize = in.writerIndex();
		logger.debug("Sending state of size {}", stateSize);
		this.averageStateSize.observe(stateSize);

		byte[] payload = new byte[stateSize];
		in.getBytes(0, payload);

		BroadcastRequest request = new BroadcastRequest(myself, payload, PROTO_ID);
		sendRequest(request, bcastProtoID);
	}

	// Handle new message received with CRDT
	private void handleMessageDeliveryEvent(BroadcastDelivery msg, short proto) {
		DeltaLWWSet state;

		try {
			ByteBuf in = Unpooled.wrappedBuffer(msg.getPayload());

			long startDeserialize = System.nanoTime();
			state = DeltaLWWSet.serializer.deserialize(in);
			long endDeserialize = System.nanoTime();

			long startMerge = System.nanoTime();
			this.averageTimeMerging.startTimedEvent("merge");
			this.crdt.mergeDelta(state);
			this.averageTimeMerging.stopTimedEvent("merge");
			long endMerge = System.nanoTime();

			long deserializationMicros = (endDeserialize - startDeserialize) / 1_000;
			long mergeMicros = (endMerge - startMerge) / 1_000;

			logger.debug("Deserialization took {} µs, merge took {} µs", deserializationMicros, mergeMicros);

			this.localLog.clear();
			Iterator<SerializableType> it = this.crdt.iterator();
			while (it.hasNext()) {
				this.localLog.add((StringType) it.next());
			}
		} catch (Exception e) {
			logger.error(e);
			System.err.println(e);
		}
	}

	/**
	 * This method disables the transmission of more messages after it being
	 * executed...
	 */
	public void disableTransmissions() {
		this.executing.set(false);
	}

	public void enableTransmission() {
		this.executing.set(true);
	}
}
