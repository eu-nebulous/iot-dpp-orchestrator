package eut.nebulouscloud.iot_dpp.persistor;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.influxdb.annotations.Measurement;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.Ready.StatusEnum;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.jayway.jsonpath.JsonPath;

import eut.nebulouscloud.auth.KeycloackAuthPlugin;

public class DataPersistorPlugin implements ActiveMQServerMessagePlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataPersistorPlugin.class);

	String influxDBHost;
	String influxDBToken;
	String influxDBOrganization;
	private DataPersistor persistor;

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

	private static String getBody(Message message)
	{
		String body = message.getStringBody();
		if(body==null)
		{
			try {
				ActiveMQBuffer buf = message.toCore().getBodyBuffer();
				
				byte[] data = new byte[buf.writerIndex() - buf.readerIndex()];
				buf.readFully(data);
				body = new String(data);
			} catch (Exception ex) {
				LOGGER.error("cant get body",ex);
				
			}
		}
		return body;
	}

	/**
	 * https://restfulapi.net/json-jsonpath/
	 * 
	 * @param message
	 * @param jsonPath
	 * @return
	 */
	public static String extractData(Message message, String jsonPath) {
		if (message instanceof LargeServerMessage) {
			LOGGER.error("Can't extract data JSON body on LargeServerMessages");
			return null;
		}
		
		String body = getBody(message);


		if (body == null) {
			LOGGER.error(
					String.format("Can't extract JSON body on message %s since body is null", message));
			return null;
		}

		try {
			return JsonPath.read(body, jsonPath).toString();
		} catch (Exception ex) {
			LOGGER.error(
					String.format("Can't extract path %s from JSON body of message %s", jsonPath, body),
					ex);
			return null;
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
		Point point = Point.measurement("temperature").addTag("location", "west").addField("value", extractData(message,"temperature"))
				.time(Instant.now().toEpochMilli(), WritePrecision.MS);
		persistor.points.add(point);
	}
	
	
	
	private Point processMessage(Message message)
	{
		String address = message.getAddress();
		String body = getBody(message);
		//return JsonPath.read(body, jsonPath).toString();
		
		
		return null;
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

		@Override
		public void run() { // TODO Auto-generated method stub

			while (true) {
				WriteApi api = getWriteAPI();

				while(!points.isEmpty())
				{
					try {
					api.writePoint(points.remove(0));
					}catch(Exception ex)
					{
						LOGGER.error("",ex);
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
