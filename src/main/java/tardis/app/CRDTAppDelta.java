package tardis.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
import pt.unl.fct.di.novasys.babel.crdts.utils.ordering.VersionVector;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.Metric.Unit;
import pt.unl.fct.di.novasys.babel.metrics.StatsGauge;
import pt.unl.fct.di.novasys.babel.metrics.StatsGauge.StatType;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;
import pt.unl.fct.di.novasys.babel.protocols.membership.notifications.NeighborDown;
import pt.unl.fct.di.novasys.babel.protocols.membership.notifications.NeighborUp;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionUp;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionFailed;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionUp;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.app.command.Command;
import tardis.app.data.CRDTStateMessage;
import tardis.app.data.Card;
import tardis.app.timers.AppWorkloadGenerateTimer;
import tardis.app.timers.BroadcastStateTimer;

public class CRDTAppDelta extends GenericProtocol {

	public final static String PROTO_NAME = "CRDT w/ Delta Mock Application";
	public final static short PROTO_ID = 9898;

	public final static String STATE_SIZE_METRIC = "averageStateSize";
	public final static String TIME_MERGING_METRIC = "averageTimeMerging";

	private boolean generateWorkload;

	public final static String PAR_WORKLOAD_PERIOD = "app.workload.period";
	public final static String PAR_WORKLOAD_SIZE = "app.workload.payload";
	public final static String PAR_GENERATE_WORKLOAD = "app.workload.on";

	private static final double BUY_PROBABILITY = 0.7;
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

	private final Host myself;

	private final Map<Host, VersionVector> neighborMetadata = new HashMap<>();

	private Logger logger = LogManager.getLogger(CRDTAppDelta.class);

	private AtomicBoolean executing;

	private String nodeLabel;

	private Thread managementThread;

	private DeltaLWWSet crdt;
	private ArrayList<StringType> localLog;
	private StatsGauge averageTimeMerging;
	private StatsGauge averageStateSize;
	private int channelId;

	public CRDTAppDelta(Host myself) throws HandlerRegistrationException, IOException {
		super(CRDTAppDelta.PROTO_NAME, CRDTAppDelta.PROTO_ID);

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
				new StatsGauge.Builder(CRDTAppDelta.STATE_SIZE_METRIC, Unit.BYTES).statTypes(StatType.AVG).build());
		this.averageTimeMerging = registerMetric(new StatsGauge.Builder(CRDTAppDelta.TIME_MERGING_METRIC, "ms")
				.statTypes(StatType.AVG, StatType.MAX).build());

		registerTimerHandler(AppWorkloadGenerateTimer.PROTO_ID, this::handleAppWorkloadGenerateTimer);
		registerTimerHandler(BroadcastStateTimer.PROTO_ID, this::handleBroadcastStateTimer);

		subscribeNotification(NeighborUp.NOTIFICATION_ID, this::handleNeighborUp);
		subscribeNotification(NeighborDown.NOTIFICATION_ID, this::handleNeighborDown);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
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
			logger.debug("CRDTAppDelta has workload generation enabled.");
		} else
			logger.debug("CRDTAppDelta has workload generation disabled.");

		Properties channelProps = new Properties();
		// The address to bind to
		channelProps.setProperty(TCPChannel.ADDRESS_KEY, myself.getAddress().getHostAddress());
		channelProps.setProperty(TCPChannel.PORT_KEY, "" + myself.getPort()); // The port to bind to
		this.channelId = createChannel(TCPChannel.NAME, channelProps);
		setDefaultChannel(channelId);
		logger.debug("Created new channel with id {} and bounded to: {}:{}", this.channelId, myself.getAddress(),
				myself.getPort());

		registerMessageSerializer(this.channelId, CRDTStateMessage.MSG_CODE, CRDTStateMessage.serializer);
		registerMessageHandler(this.channelId, CRDTStateMessage.MSG_CODE, this::uponCRDTStateMessage);

		/*-------------------- Register Channel Event ------------------------------- */
		registerChannelEventHandler(this.channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
		registerChannelEventHandler(this.channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
		registerChannelEventHandler(this.channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
		registerChannelEventHandler(this.channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
		registerChannelEventHandler(this.channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);

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

	private void uponCRDTStateMessage(CRDTStateMessage msg, Host sender, short protoID, int cID) {
		logger.info("Received state from {}.", sender);

		long startMerge = System.nanoTime();

		this.averageTimeMerging.startTimedEvent("merge");

		DeltaLWWSet state = msg.getState();

		this.crdt.mergeDelta(state);

		this.averageTimeMerging.stopTimedEvent("merge");

		this.neighborMetadata.put(sender, state.getReplicaState().getVV());

		long endMerge = System.nanoTime();
		long mergeMicros = (endMerge - startMerge) / 1_000;

		logger.debug("Merge took {} µs", mergeMicros);

		this.localLog.clear();

		Iterator<SerializableType> it = this.crdt.iterator();
		while (it.hasNext()) {
			this.localLog.add((StringType) it.next());
		}
	}

	private void handleAppWorkloadGenerateTimer(AppWorkloadGenerateTimer t, long time) {
		if (!this.executing.getAcquire())
			return;

		if (this.generateMessageProbability < 1.0
				&& ThreadLocalRandom.current().nextDouble() > this.generateMessageProbability)
			return; // We have a probabibility for doing an action

		// BUY_PROBABILITY% chance of buying, 1-BUY_PROBABILITY% of selling, so the
		// state keeps growing steadily
		// That said, removing still grows state :)
		if (ThreadLocalRandom.current().nextDouble() > BUY_PROBABILITY) {
			// SELL
			if (localLog.size() == 0) {
				logger.debug("Nothing to sell");
				return;
			}

			int randomSell = ThreadLocalRandom.current().nextInt(localLog.size());
			StringType sold = this.localLog.get(randomSell);
			if (!this.crdt.lookup(sold)) {
				logger.error("Local log was off, card is not for sale anymore");
				return;
			}
			this.crdt.remove(sold);

			if (!this.crdt.lookup(sold)) {
				localLog.remove(sold);
				logger.info("Sold card {}", Card.preview(sold.getValue()));
			} else {
				System.err.println("CRDT remove is broken");
			}
		} else {
			// BUY
			StringType card = new StringType(Card.randomCard().toString());
			DeltaLWWSet delta = this.crdt.add(card);
			Iterator<SerializableType> it = delta.iterator();
			if (it.hasNext()) {
				localLog.add((StringType) it.next());
				logger.info("Bought card {}", Card.preview(card.getValue()));
			}
		}
	}

	private void dumpState() {
		StringBuilder s = new StringBuilder();
		s.append("%d Pokemon\n");
		s.append("[");

		int count = 0;
		Iterator<SerializableType> it = this.crdt.iterator();
		while (it.hasNext()) {
			s.append(String.format("%s ; ", Card.preview(((StringType) it.next()).getValue())));
			count++;
		}
		s.append("]");
		logger.info(String.format(s.toString(), count));
	}

	private void handleBroadcastStateTimer(BroadcastStateTimer t, long time) {
		if (!this.executing.getAcquire()) {
			dumpState();
			System.exit(0);
		}

		this.executing.set(false);

		for (Map.Entry<Host, VersionVector> entry : neighborMetadata.entrySet()) {
			Host neighbor = entry.getKey();
			VersionVector neighborVV = entry.getValue();
			DeltaLWWSet delta = this.crdt.generateDelta(neighborVV);

			CRDTStateMessage msg = new CRDTStateMessage(delta);

			sendMessage(msg, CRDTAppDelta.PROTO_ID, neighbor);

			ByteBuf in = Unpooled.buffer();
			try {
				delta.serialize(in);
			} catch (IOException e) {
				logger.error("Error serializing CRDT for state size calculation");
				e.printStackTrace();
			}

			this.averageStateSize.observe(in.writerIndex());
			logger.debug("Sending state of size ({} bytes) to neighbor {}", in.writerIndex(), neighbor);
		}
	}

	private void handleNeighborUp(NeighborUp notif, short proto) {
		Host h = new Host(notif.getPeer().getAddress(), this.myself.getPort());
		// Peer peer = new Peer(myself.getAddress(), myself.getPort());
		// neighborCrdts.putIfAbsent(h, new DeltaLWWSet(new ReplicaID(peer)));
		this.neighborMetadata.putIfAbsent(h, new VersionVector());
		openConnection(h, this.channelId);
		logger.debug("Neighbor up: {} ({} total)", h, neighborMetadata.size());
	}

	private void handleNeighborDown(NeighborDown notif, short proto) {
		Host h = new Host(notif.getPeer().getAddress(), this.myself.getPort());
		this.neighborMetadata.remove(h);
		closeConnection(h, this.channelId);
		logger.debug("Neighbor down: {} ({} total)", h, neighborMetadata.size());
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

	/*
	 * --------------------------------- Channel Events ----------------------------
	 */

	private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
		logger.trace("Host {} is down, cause: {}", event.getNode(), event.getCause());
	}

	private void uponOutConnectionFailed(OutConnectionFailed<?> event, int channelId) {
		logger.trace("Connection to host {} failed, cause: {}", event.getNode(), event.getCause());
	}

	private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
		logger.trace("Host (out) {} is up", event.getNode());
	}

	private void uponInConnectionUp(InConnectionUp event, int channelId) {
		logger.trace("Host (in) {} is up", event.getNode());
	}

	private void uponInConnectionDown(InConnectionDown event, int channelId) {
		logger.trace("Connection from host {} is down, cause: {}", event.getNode(), event.getCause());
	}

}
