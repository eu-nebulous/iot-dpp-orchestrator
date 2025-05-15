package eut.nebulouscloud.iot_dpp.persistor;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.Ready.StatusEnum;
import com.influxdb.client.domain.WritePrecision;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;

import eut.nebulouscloud.iot_dpp.persistor.MessageDataExtractor.Point;

public class DataPersistorPlugin implements ActiveMQServerMessagePlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataPersistorPlugin.class);

	String influxDBHost;
	String influxDBToken;
	String influxDBOrganization;
	private DataPersistor persistor;
	public List<MessageDataExtractor> dataExtractors = new LinkedList<MessageDataExtractor>();
	private ParseContext parseContext = JsonPath.using(MessageDataExtractor.conf);

	@Override
	public void init(Map<String, String> properties) {
		LOGGER.debug("Initializing DataPersistorPlugin with properties: {}", properties);

		influxDBHost = Optional.ofNullable(properties.get("influxDB.host"))
				.orElseThrow(() -> new IllegalStateException("influxDB.host parameter is not defined"));

		influxDBOrganization = Optional.ofNullable(properties.get("influxDB.organization"))
				.orElseThrow(() -> new IllegalStateException("influxDB.organization parameter is not defined"));

		influxDBToken = Optional.ofNullable(properties.get("influxDB.token"))
				.orElseThrow(() -> new IllegalStateException("influxDB.token parameter is not defined"));

		LOGGER.info("DataPersistorPlugin initialized with realm public key");

		persistor = new DataPersistor();
		new Thread(persistor).start();
	}

	private static String getBody(Message message) {
		String body = message.getStringBody();
		if (body == null) {
			try {
				ActiveMQBuffer buf = message.toCore().getBodyBuffer();

				byte[] data = new byte[buf.writerIndex() - buf.readerIndex()];
				buf.readFully(data);
				body = new String(data);
			} catch (Exception ex) {
				LOGGER.error("cant get body", ex);

			}
		}
		return body;
	}

	private void extractData(Message message) {
		DocumentContext bodyJsonpath = null;
		try {
			String body = getBody(message);
			bodyJsonpath = parseContext.parse(body);
		} catch (Exception ex) {
			LOGGER.error("", ex);
		}

		for (MessageDataExtractor extractor : dataExtractors) {
			try {
				String address = message.getAddress();
				address = address.replaceAll("\\\\", "");
				Point p = extractor.extract(address, bodyJsonpath);
				if (p != null) {
					persistor.points.add(p);
				}
			} catch (Exception ex) {
				LOGGER.error("", ex);
			}
		}
	}

	@Override
	public void registered(ActiveMQServer server) {
		// TODO Auto-generated method stub
		ActiveMQServerMessagePlugin.super.registered(server);
	}

	@Override
	public void afterSend(ServerSession session, Transaction tx, Message message, boolean direct,
			boolean noAutoCreateQueue, RoutingStatus result) throws ActiveMQException {
		extractData(message);
	}

	class DataPersistor implements Runnable {

		public List<Point> points = Collections.synchronizedList(new LinkedList<Point>());
		InfluxDBClient influxDBClient;
		WriteApi writeAPI;

		private boolean isInfluxDBClientReady() {
			if (influxDBClient == null)
				return false;
			try {
				if (influxDBClient.ready().getStatus() == StatusEnum.READY)
					return true;

			} catch (Exception ex) {
				LOGGER.trace("getWriteAPI", ex);
			}
			return false;
		}

		private WriteApi getWriteAPI() {
			boolean newConnection = false;
			while (!isInfluxDBClientReady()) {
				LOGGER.info("Connection to influxDBClient not available. Try to connect");
				newConnection = true;
				try {
					influxDBClient = InfluxDBClientFactory.create(influxDBHost, influxDBToken.toCharArray(),
							influxDBOrganization);
			
					// Enable batch writes to get better performance.
					/*
					 * influxDBClient.enableBatch( BatchOptions.DEFAULTS .threadFactory(runnable ->
					 * { Thread thread = new Thread(runnable); thread.setDaemon(true); return
					 * thread; }) );
					 */

					writeAPI = influxDBClient.makeWriteApi();

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				} catch (Exception ex) {
					LOGGER.error("Failed to connect to influx", ex);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			if (newConnection)
				LOGGER.info("Connection is ready");
			return writeAPI;

		}
		Set<String> knownBuckets = new HashSet<String>();

		private void checkBucketExists(String bucket)
		{
			if(knownBuckets.contains(bucket)) return;
			
				if(influxDBClient.getBucketsApi().findBucketByName(bucket)!=null) {
					knownBuckets.add(bucket);
				}else
				{
					String orgId = influxDBClient.getOrganizationsApi().findOrganizations().stream().filter(o->o.getName().equals(influxDBOrganization)).findFirst().get().getId();
					influxDBClient.getBucketsApi().createBucket(bucket, orgId);
					knownBuckets.add(bucket);
				}
			
		}
		
		@Override
		public void run() { // TODO Auto-generated method stub

			while (true) {
				WriteApi api = getWriteAPI();
				while (!points.isEmpty()) {
					try {
						Point p =  points.remove(0);
						checkBucketExists(p.bucket);
						api.writePoint(p.bucket,influxDBOrganization,p.point);
					} catch (Exception ex) {
						LOGGER.error("", ex);
					}
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}

	}

}
