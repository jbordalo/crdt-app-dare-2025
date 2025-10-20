import java.io.File;
import java.net.InetAddress;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.metrics.exporters.MonitorExporter;
import pt.unl.fct.di.novasys.babel.metrics.monitor.SimpleMonitor;
import pt.unl.fct.di.novasys.babel.protocols.cyclon.Cyclon;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import pt.unl.fct.di.novasys.babel.protocols.hyparview.HyParView;
import pt.unl.fct.di.novasys.babel.utils.NetworkingUtilities;
import pt.unl.fct.di.novasys.babel.utils.memebership.monitor.MembershipMonitor;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.app.CRDTApp;

public class Main {
	// Sets the log4j (logging library) configuration file
	static {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	// private static final String SAVE_PATH = "/app/logs/";
	private static final String SAVE_PATH = "logs/";

	// Creates the logger object
	private static final Logger logger = LogManager.getLogger(Main.class);

	// Default babel configuration file (can be overridden by the "-config" launch
	// argument)
	private static final String DEFAULT_CONF = "tardis.conf";

	@SuppressWarnings("unused")
	private final CRDTApp app;

	public Main(CRDTApp app) {
		this.app = app;
	}

	public static void main(String[] args) throws Exception {
		logger.info("Starting...");

		// Get the (singleton) babel instance
		Babel babel = Babel.getInstance();
		if (new File(DEFAULT_CONF).exists()) {
			System.err.println("The config file: " + DEFAULT_CONF + " is not accessible.");
			System.exit(1);
		}

		Properties props = Babel.loadConfig(args, DEFAULT_CONF);

		String address = null;

		if (props.containsKey(Babel.PAR_DEFAULT_INTERFACE))
			address = NetworkingUtilities.getAddress(props.getProperty(Babel.PAR_DEFAULT_INTERFACE));
		else if (props.containsKey(Babel.PAR_DEFAULT_ADDRESS))
			address = props.getProperty(Babel.PAR_DEFAULT_ADDRESS);

		int port = -1;

		if (props.containsKey(Babel.PAR_DEFAULT_PORT))
			port = Integer.parseInt(props.getProperty(Babel.PAR_DEFAULT_PORT));

		Host h = null;

		if (address == null || port == -1) {
			System.err.println("Configuration must contain one of '" + Babel.PAR_DEFAULT_INTERFACE + "' or '"
					+ Babel.PAR_DEFAULT_ADDRESS + "' and the '" + Babel.PAR_DEFAULT_PORT + "'");
			System.exit(1);
		}

		h = new Host(InetAddress.getByName(address), port);

		InetAddress monitorAddress = InetAddress.getByName(props.getProperty("Metrics.monitor.address"));
		int monitorPort = Integer.parseInt(props.getProperty("Metrics.monitor.port"));

		Host monitorHost = new Host(monitorAddress, monitorPort);

		SimpleMonitor mon = null;

		// if (Boolean.parseBoolean(props.getProperty("monitor", "false"))) {
		// // I'm the monitor
		// logger.info("{} acting as monitor.", h.getAddress());
		// mon = new SimpleMonitor(monitorHost,
		// new LocalTextStorage.Builder().setPath(SAVE_PATH + "MonitorStorage.json")
		// .setFormatter(new SimpleFormatter()).setAppend(false).build());
		// mon.addAggregation(new DefaultAggregation(CRDTApp.PROTO_ID,
		// CRDTApp.FULL_STATE_SIZE_METRIC));
		// mon.addAggregation(new DefaultAggregation(CRDTApp.PROTO_ID,
		// CRDTApp.STATE_SIZE_SENT_METRIC));
		// mon.addAggregation(new DefaultAggregation(CRDTApp.PROTO_ID,
		// CRDTApp.TIME_MERGING_METRIC));
		// }

		Host exporterHost = new Host(h.getAddress(), h.getPort() + 2);

		MonitorExporter exporter = null;
		// MonitorExporter exporter = new MonitorExporter(exporterHost, monitorHost,
		// 58000,
		// ExporterCollectOptions.builder().protocolsToCollect(CRDTApp.PROTO_ID).collectAllMetrics(false)
		// .metricCollectOptions(CRDTApp.PROTO_ID, CRDTApp.FULL_STATE_SIZE_METRIC,
		// new CollectOptions(true))
		// .metricCollectOptions(CRDTApp.PROTO_ID, CRDTApp.STATE_SIZE_SENT_METRIC,
		// new CollectOptions(true))
		// .metricCollectOptions(CRDTApp.PROTO_ID, CRDTApp.TIME_MERGING_METRIC, new
		// CollectOptions(true))
		// .build());

		System.out.println("localhost is set to: " + h);

		if (!props.containsKey("Membership.overlay")) {
			System.err.println("Missing membership overlay.");
		}

		GenericProtocol membershipProtocol = null;

		switch (props.getProperty("Membership.overlay").toLowerCase()) {
			case "hyparview":
				membershipProtocol = new HyParView("channel.hyparview", props, h);
				break;
			case "cyclon":
				membershipProtocol = new Cyclon(TCPChannel.NAME, props, h);
				break;
		}

		if (membershipProtocol == null) {
			System.err.println("No membership protocol provided.");
			System.exit(1);
		}

		MembershipMonitor mm = new MembershipMonitor();

		Host antiEntropyHost = new Host(h.getAddress(), h.getPort() + 1);

		boolean deltaEnabled = props.getProperty("app.type").equals("delta");
		CRDTApp app = new CRDTApp(antiEntropyHost, deltaEnabled);

		if (!props.containsKey("Metrics.monitor.address") || !props.containsKey("Metrics.monitor.port")) {
			System.out.println("Missing monitor configuration");
			System.exit(1);
		}

		// Solve the dependency between the data dissemination app and the broadcast
		// protocol if omitted from the config
		props.putIfAbsent(CRDTApp.PAR_BCAST_PROTOCOL_ID,
				AdaptiveEagerPushGossipBroadcast.PROTOCOL_ID + "");

		GenericProtocol[] protocols = { membershipProtocol, mm, app, mon, exporter };

		for (GenericProtocol protocol : protocols) {
			if (protocol == null)
				continue;
			babel.registerProtocol(protocol);
			System.out.printf("Loaded: %s %d%n", protocol.getProtoName(), protocol.getProtoId());
		}

		for (GenericProtocol protocol : protocols) {
			if (protocol == null)
				continue;
			protocol.init(props);
			System.out.printf("Initialized: %s %d%n", protocol.getProtoName(), protocol.getProtoId());
		}

		System.out.println("Setup is complete.");

		// babel.startMetrics();
		babel.start();
		System.out.println("System is running.");
	}
}
