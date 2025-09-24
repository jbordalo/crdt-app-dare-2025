package tardis.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.hash.Hashing;

import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.requests.BroadcastRequest;
import pt.unl.fct.di.novasys.babel.protocols.membership.Peer;
import pt.unl.fct.di.novasys.babel.crdts.state.implementations.StateLWWSet;
import pt.unl.fct.di.novasys.babel.crdts.utils.ReplicaID;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.app.command.Command;
import tardis.app.data.Card;
import tardis.app.timers.AppWorkloadGenerateTimer;

public class CRDTApp extends GenericProtocol {

	public final static String PROTO_NAME = "CRDT Mock Application";
	public final static short PROTO_ID = 9898;

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
	private int payloadSize;
	private double generateMessageProbability;

	private short bcastProtoID;

	private final Host myself;

	private Logger logger = LogManager.getLogger(DataDisseminationApp.class);

	private AtomicBoolean executing;

	private String nodeLabel;

	private Thread managementThread;

	private StateLWWSet crdt;

	public CRDTApp(Host myself) throws HandlerRegistrationException {
		super(DataDisseminationApp.PROTO_NAME, DataDisseminationApp.PROTO_ID);

		this.myself = myself;
		Peer peer = new Peer(myself.getAddress(), myself.getPort());
		this.crdt = new StateLWWSet(new ReplicaID(peer));

		try {
			this.nodeLabel = myself.getAddress().getHostName().split("\\.")[3];
		} catch (Exception e) {
			this.nodeLabel = myself.getAddress().getHostName();
		}

		registerTimerHandler(AppWorkloadGenerateTimer.PROTO_ID, this::handleAppWorkloadGenerateTimer);
		subscribeNotification(BroadcastDelivery.NOTIFICATION_ID, this::handleMessageDeliveryEvent);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		if (props.containsKey(PAR_BCAST_PROTOCOL_ID)) {
			this.bcastProtoID = Short.parseShort(props.getProperty(PAR_BCAST_PROTOCOL_ID));
			logger.debug("DataDisseminationApp is configured to used broadcast protocol with id: " + this.bcastProtoID);
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

			if (props.containsKey(PAR_WORKLOAD_SIZE))
				this.payloadSize = Integer.parseInt(props.getProperty(PAR_WORKLOAD_SIZE));
			else
				this.payloadSize = DEFAULT_WORKLOAD_SIZE;

			if (props.containsKey(PAR_WORKLOAD_PROBABILITY))
				this.generateMessageProbability = Double.parseDouble(props.getProperty(PAR_WORKLOAD_PROBABILITY));
			else
				this.generateMessageProbability = DEFAULT_WORKLOAD_PROBABILITY;

			setupPeriodicTimer(new AppWorkloadGenerateTimer(), this.workloadPeriod, this.workloadPeriod);
			logger.debug("DataDisseminationApp has workload generation enabled.");
		} else
			logger.debug("DataDisseminationApp has workload generation disabled.");

		boolean b = DEFAULT_BCAST_INIT_ENABLED;

		if (props.containsKey(PAR_BCAST_INIT_ENABLED)) {
			b = Boolean.parseBoolean(props.getProperty(PAR_BCAST_INIT_ENABLED));
		}

		this.executing = new AtomicBoolean(b);

		// If app.management is present but set to false, don't start the management
		// thread
		if (props.containsKey(PAR_MANAGEMENT_THREAD) && !Boolean.parseBoolean(props.getProperty(PAR_MANAGEMENT_THREAD))) {
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
			return; // We have conditioned probability to generate message

		// 80% chance of buying, 20% of selling, so the state keeps growing steadily
		// That said, removing still grows state :)
		if (new Random().nextDouble() > 0.8) {
			// SELL

		} else {
			// BUY
			Card card = Card.randomCard();
			this.crdt.add(card);
		}

		logger.debug("CRDTApp generating a card.");
	}

	// Handle new message received with CRDT
	private void handleMessageDeliveryEvent(BroadcastDelivery msg, short proto) {
		UserMessage um = null;

		
		try {
			um = UserMessage.fromByteArray(msg.getPayload());
		} catch (Exception e) {
			// Purposefully not dealing with the exception
			// Assuming it means the message was not for me
			return;
		}
		// this.crdt.add();

		if (um.hasAttachment()) {
			logger.debug("Delivered a message with attachment");

			String readable = readableOutput(um.getMessage(), um.getAttachmentName());

			logger.info(myself + " recv message: [" + msg.getSender() + "::::" + readable + "]");
		} else {
			logger.debug("Delivered a message");
			logger.info(myself + " recv message: [" + msg.getSender() + "::::" + readableOutput(um.getMessage()) + "]");
		}
	}

	public static String randomCapitalLetters(int length) {
		int leftLimit = 65; // letter 'A'
		int rightLimit = 90; // letter 'Z'
		Random random = new Random();
		return random.ints(leftLimit, rightLimit + 1).limit(length)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
	}

	public static String readableOutput(String msg, String attachName) {
		return Hashing.sha256().hashString(msg + "::" + attachName, StandardCharsets.UTF_8).toString();
	}

	public static String readableOutput(String msg) {
		if (msg.length() > 32) {
			return Hashing.sha256().hashString(msg, StandardCharsets.UTF_8).toString();
		} else
			return msg;
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
