package tardis.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.crdts.delta.implementations.DeltaORSet;
import pt.unl.fct.di.novasys.babel.crdts.utils.ReplicaID;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.ByteArrayType;
import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.SerializableType;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.Metric.Unit;
import pt.unl.fct.di.novasys.babel.metrics.Gauge;
import pt.unl.fct.di.novasys.babel.metrics.StatsGauge;
import pt.unl.fct.di.novasys.babel.metrics.StatsGauge.StatType;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;
import pt.unl.fct.di.novasys.babel.protocols.membership.notifications.NeighborDown;
import pt.unl.fct.di.novasys.babel.protocols.membership.notifications.NeighborUp;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.app.command.Command;
import tardis.app.data.CRDTStateMessage;
import tardis.app.data.Card;
import tardis.app.timers.SendStateTimer;
import tardis.app.timers.SendVVTimer;
import tardis.app.timers.SimulateClientTimer;

public class CRDTApp extends GenericProtocol {

	public final static String PROTO_NAME = "CRDT Mock Application (State-Based CRDT)";
	public final static short PROTO_ID = 9898;

	public final static String STATE_SIZE_SENT_METRIC = "averageStateSizeSent";
	public final static String FULL_STATE_SIZE_METRIC = "averageFullStateSize";
	public final static String TIME_MERGING_METRIC = "averageTimeMerging";

	public final static String PAR_WORKLOAD_PERIOD = "app.workload.period";
	public final static String PAR_WORKLOAD_SIZE = "app.workload.payload";
	public final static String PAR_GENERATE_WORKLOAD = "app.workload.on";

	private static final double BUY_PROBABILITY = 0.7;
	public final static String DEFAULT_WORKLOAD_PERIOD = "10000"; // 10 seconds

	public final static String PAR_WORKLOAD_PROBABILITY = "app.workload.probability";
	public final static String DEFAULT_WORKLOAD_PROBABILITY = "1.0";

	public final static String PAR_BCAST_PROTOCOL_ID = "app.bcast.id";

	public final static String PAR_BCAST_INIT_ENABLED = "app.bcast.enable";
	public final static boolean DEFAULT_BCAST_INIT_ENABLED = true;

	public final static String PAR_MANAGEMENT_PORT = "app.management.port";
	private static final String PAR_MANAGEMENT_THREAD = "app.management";

	public final static int DEFAULT_MANAGEMENT_PORT = Command.DEFAULT_MANAGE_PORT;

	private final Random rand = new Random(42);

	private long workloadPeriod;
	private double generateMessageProbability;

	private short bcastProtoID;

	private final Host myself;
	private int channelId;

	private Logger logger = LogManager.getLogger(CRDTApp.class);

	private AtomicBoolean executing;

	private Thread managementThread;

	private DeltaORSet crdt;
	private final Set<Host> neighbors = new HashSet<>();
	private ArrayList<Card> localLog;

	// Metrics
	private StatsGauge averageTimeMerging;
	private StatsGauge averageStateSizeSent;
	private Gauge fullStateSize;

	private long totalSentSize = 0;
	private int sentCount = 0;
	private PrintStream stateLog;

	// Debugging
	private final boolean testing = false;
	private int roundsLeft = 5;

	public CRDTApp(Host myself) throws HandlerRegistrationException, IOException {
		super(CRDTApp.PROTO_NAME, CRDTApp.PROTO_ID);

		this.myself = myself;
		Peer peer = new Peer(myself.getAddress(), myself.getPort());
		this.crdt = new DeltaORSet(new ReplicaID(peer));
		this.stateLog = new PrintStream("state_log_" + myself.getAddress().toString().split("\\.")[3] + ".csv");
		stateLog.println("timestamp_ms,full_state_size_bytes,avg_state_sent_bytes");
		stateLog.flush();
		this.localLog = new ArrayList<>();

		createChannel();

		registerMetrics();

		registerTimerHandler(SimulateClientTimer.TIMER_ID, this::uponSimulateClientTimer);
		registerTimerHandler(SendStateTimer.TIMER_ID, this::uponSendStateTimer);

		registerMessageSerializer(this.channelId, CRDTStateMessage.MSG_CODE, CRDTStateMessage.serializer);
		registerMessageHandler(this.channelId, CRDTStateMessage.MSG_CODE, this::uponCRDTStateMessage);

		subscribeNotification(NeighborUp.NOTIFICATION_ID, this::handleNeighborUp);
		subscribeNotification(NeighborDown.NOTIFICATION_ID, this::handleNeighborDown);
	}

	private void registerMetrics() {
		this.averageStateSizeSent = registerMetric(
				new StatsGauge.Builder(CRDTApp.STATE_SIZE_SENT_METRIC, Unit.BYTES).statTypes(StatType.AVG)
						.build());
		this.fullStateSize = registerMetric(
				new Gauge.Builder(CRDTApp.FULL_STATE_SIZE_METRIC, Unit.BYTES).build());
		this.averageTimeMerging = registerMetric(new StatsGauge.Builder(CRDTApp.TIME_MERGING_METRIC, "ms")
				.statTypes(StatType.AVG, StatType.MAX).build());
	}

	private void createChannel() throws IOException {
		Properties channelProps = new Properties();
		// The address to bind to
		channelProps.setProperty(TCPChannel.ADDRESS_KEY, myself.getAddress().getHostAddress());
		channelProps.setProperty(TCPChannel.PORT_KEY, "" + myself.getPort()); // The port to bind to
		this.channelId = createChannel(TCPChannel.NAME, channelProps);
		setDefaultChannel(channelId);
		logger.debug("Created new channel with id {} and bounded to: {}:{}", this.channelId, myself.getAddress(),
				myself.getPort());
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

		this.workloadPeriod = Long.parseLong(props.getProperty(PAR_WORKLOAD_PERIOD, DEFAULT_WORKLOAD_PERIOD));
		this.generateMessageProbability = Double
				.parseDouble(props.getProperty(PAR_WORKLOAD_PROBABILITY, DEFAULT_WORKLOAD_PROBABILITY));

		setupPeriodicTimer(new SimulateClientTimer(), this.workloadPeriod, this.workloadPeriod);
		setupPeriodicTimer(new SendVVTimer(), this.workloadPeriod * 5 - 2, this.workloadPeriod * 5 - 2);
		setupPeriodicTimer(new SendStateTimer(), this.workloadPeriod * 5, this.workloadPeriod * 5);

		boolean b = DEFAULT_BCAST_INIT_ENABLED;

		if (props.containsKey(PAR_BCAST_INIT_ENABLED)) {
			b = Boolean.parseBoolean(props.getProperty(PAR_BCAST_INIT_ENABLED));
		}

		this.executing = new AtomicBoolean(b);

		// If app.management is present but set to false, don't start the management
		// thread
		if (!Boolean.parseBoolean(props.getProperty(PAR_MANAGEMENT_THREAD, "false"))) {
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

	private void uponSimulateClientTimer(SimulateClientTimer t, long time) {
		if (!this.executing.getAcquire())
			return;

		if (this.generateMessageProbability < 1.0 && rand.nextDouble() > this.generateMessageProbability)
			return; // We have a probabibility for doing an action

		// BUY_PROBABILITY% chance of buying, 1-BUY_PROBABILITY%
		if (rand.nextDouble() > BUY_PROBABILITY) {
			// SELL
			if (localLog.size() == 0) {
				logger.debug("Nothing to sell");
				return;
			}

			int randomSell = this.rand.nextInt(localLog.size());
			Card card = this.localLog.get(randomSell);
			ByteArrayType cardBytes = new ByteArrayType(card.toBytes());

			if (!this.crdt.lookup(cardBytes)) {
				logger.error("Local log was off, card {} is not for sale anymore", card);
				return;
			}

			this.crdt.remove(cardBytes);
			// logger.debug("@remove, VV: {}", this.crdt.getReplicaState().getVV());

			if (!this.crdt.lookup(cardBytes)) {
				localLog.remove(card);
				logger.info("Sold card {}", card);
			} else {
				System.err.println("CRDT remove is broken");
			}
		} else {
			// BUY
			Card card = Card.randomCard();
			DeltaORSet delta = this.crdt.add(new ByteArrayType(card.toBytes()));
			// logger.debug("@add, VV: {}", this.crdt.getReplicaState().getVV());
			Iterator<SerializableType> it = delta.iterator();
			if (it.hasNext()) {
				localLog.add(card);
				logger.info("Bought card {}", card);
			}
		}
	}

	private int calculateSize(DeltaORSet toSend) {
		int size = 0;
		Iterator<SerializableType> it = toSend.iterator();
		while (it.hasNext()) {
			byte[] cardBytes = ((ByteArrayType) it.next()).getValue();
			size += cardBytes.length;
		}
		return size;
	}

	private void dumpState() {
		StringBuilder s = new StringBuilder();
		s.append("[");

		int count = 0;
		int stateSize = 0;
		Iterator<SerializableType> it = this.crdt.iterator();
		while (it.hasNext()) {
			byte[] cardBytes = ((ByteArrayType) it.next()).getValue();
			Card c = Card.fromBytes(cardBytes);
			s.append(c);
			stateSize += cardBytes.length;
			count++;
		}
		s.append("]\n");
		s.append("%d Pokemon\n");
		s.append("Total state size=%d kB\n");
		logger.info(String.format(s.toString(), count, stateSize / 1000));
	}

	private void uponSendStateTimer(SendStateTimer t, long time) {
		// Testing code
		if (this.testing) {
			if (this.roundsLeft == 0) {
				dumpState();
				System.exit(0);
			}

			if (--this.roundsLeft == 0)
				disableTransmissions();
		}

		logger.info("Communication step");

		// This could be its own thread, cause it's for metrics
		int totalSize = calculateSize(this.crdt);
		this.fullStateSize.set(totalSize);
		stateLog.printf("%d,%d", System.currentTimeMillis(), totalSize);
		
		for (Host neighbor : neighbors) {
			CRDTStateMessage msg = new CRDTStateMessage(this.crdt);

			// this.averageStateSizeSent.observe(totalSize);
			this.totalSentSize += totalSize;
			sentCount++;

			if (totalSize == 0) {
				logger.info("Nothing to send");
				return;
			}
			logger.info("Sending state of size {}, {}% of my total size", totalSize, 100);
			sendMessage(msg, CRDTApp.PROTO_ID, neighbor);
		}

		long avgSent = (sentCount > 0) ? totalSentSize / sentCount : 0;
		averageStateSizeSent.observe(avgSent);
		stateLog.printf(",%d%n", avgSent);
		stateLog.flush();
		totalSentSize=0;
		sentCount=0;
	}

	// Handle new message received with CRDT
	private void uponCRDTStateMessage(CRDTStateMessage msg, Host sender, short protoID, int cID) {
		logger.info("Received state from {}.", sender);

		long startMerge = System.nanoTime();

		this.averageTimeMerging.startTimedEvent("merge");

		DeltaORSet state = msg.getState();

		this.crdt.mergeDelta(state);

		this.averageTimeMerging.stopTimedEvent("merge");

		long endMerge = System.nanoTime();
		long mergeMicros = (endMerge - startMerge) / 1_000;

		logger.debug("Merge took {} µs", mergeMicros);

		// TODO this can probably be more efficient
		this.localLog.clear();
		Iterator<SerializableType> it = this.crdt.iterator();
		while (it.hasNext()) {
			this.localLog.add(Card.fromBytes(((ByteArrayType) it.next()).getValue()));
		}
	}

	private void handleNeighborUp(NeighborUp notif, short proto) {
		Host h = new Host(notif.getPeer().getAddress(), this.myself.getPort());
		this.neighbors.add(h);
		openConnection(h, this.channelId);
		logger.debug("Neighbor up: {} ({} total)", h, neighbors.size());
	}

	private void handleNeighborDown(NeighborDown notif, short proto) {
		Host h = new Host(notif.getPeer().getAddress(), this.myself.getPort());
		this.neighbors.remove(h);
		closeConnection(h, this.channelId);
		logger.debug("Neighbor down: {} ({} total)", h, neighbors.size());
	}

	/**
	 * This method disables message transmission
	 */
	public void disableTransmissions() {
		this.executing.set(false);
	}

	public void enableTransmission() {
		this.executing.set(true);
	}
}
