package eut.nebulouscloud.bridge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.protonj2.client.Message;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;

/**
 * Test the correct functionaltiy of DynamicBridgePluginTest
 */
class ManualTestUtilities {

	static Logger LOGGER = LoggerFactory.getLogger(ManualTestUtilities.class);
	static int QueuesMonitoringProcesQueryIntervalSeconds = 3;
	ObjectMapper om = new ObjectMapper();


	@Test
	void defineClusterOnDevEnv() throws InterruptedException
	{
		String APP_ID = "25a7877c-9de1-4fbd-a427-2396bd7ec2dc";
		//String NEBULOUS_CONTROL_PLANE_PASSWORD = APP_ID;
		String NEBULOUS_MESSAGE_BRIDGE_PASSWORD = "4BUKmZyHXf";
		Publisher controlPlaneDefinePub = new Publisher("controlPlanePublisher",
				"eu.nebulouscloud.exn.sal.cluster.define", true, true);		
		Connector controlPlaneClient = new Connector("controlPlaneClient", new ConnectorHandler() {
			public void onReady(AtomicReference<Context> context) {
				LOGGER.info("Optimiser-controller connected to ActiveMQ");
			}
		}, List.of(controlPlaneDefinePub), List.of(), true, true,new StaticExnConfig("158.37.63.86", 32757, "admin", "nebulous"));
		controlPlaneClient.start();
		Thread.sleep(1000);
		String jsonString = "{\"name\":\"11461-9\",\"master-node\":\"m11461-9-master\",\"nodes\":[{\"nodeName\":\"m11461-9-master\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-worker-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-controller-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"}],\"env-var\":{\"NEBULOUS_MESSAGE_BRIDGE_PASSWORD\":\""
				+ NEBULOUS_MESSAGE_BRIDGE_PASSWORD + "\",     \"APPLICATION_ID\":\"" + APP_ID
				+ "\",\"BROKER_ADDRESS\":\"158.37.63.86\",\"ACTIVEMQ_HOST\":\"158.37.63.86\",\"BROKER_PORT\":\"32754\",\"ACTIVEMQ_PORT\":\"32754\",\"ONM_IP\":\"158.39.201.249\",\"ONM_URL\":\"https://onm.cd.nebulouscloud.eu\",\"AMPL_LICENSE\":\"NjYxZTQzNDQ5ODE2NDczZWIzNDIwNDc2NzZlZjI5Mzc1MjQ0MDUyMGM3MzczYzI5MTg1ODBjNWFmNzBmMzZiN2U3YWYxNzZjYTY2NjQyYTZjMWYzYzFiNjQwNmFlYTgxMTRiZjhhNDg5ZjQ0OGJjZGIyYTc2MDYzNzNiMjNiMTdjNWQ4ZjlhMjg2MjcyYzg4ZjIxOWZjZWZjMTY0MzIxMmU2ZWFjZTY5M2EzMDliYjNlMzBkN2UzNTI3MjA3OTgxZTBhMjNhNWNkOGIzYjcyOGUwZTc2ZWJiZDQwMjNhZTZiNGJkZmFiYmY1MDdkZTJlODM0M2UyNmNjNDc4NjlhNjQ0ZmZkODYxZmQzNjE0ZmVmYTJkYmZhNzI0YmMyODU3MTFmM2Q1Zjg3M2IyOTk0ODViZGNlOTBiYTRlNzc1YjQwMjI1MTI3MzIzNTBlYzZhNjExOGI4NjkyNmUwMDhjNjg1OTQwNjAyYjA5NzhlYzAxMjlmY2Q4NzM0ZDhjNGM2NDIwYmQ4MzE4OWU0NWM0MTk1ZWE4MzMxMzI0NjE4ZjBjN2RlYTViMTk0MTQ0MTJjN2MzMTNiOTIzMmQ4MTVlMGIxZDYzZjYxY2M0MWM1MzIzMDdkOTBiYjkwMWMyYTM0NTZhMWU0MGQ0OTkzOTAxMWEwMTIwMjEwYzNkYWE1YjNlN2YzZTk4ZGNhMDRmZTgyNDA3ZDc4MzQ0NGIzODcwMGU1MzdlNGJkOWI3MmY3MGY1NDQwZGM4YmE1OWE5MjU1YzJlMWM0OGRmYWM2ZTAwNmE5MGZkMzI1ODYwYzVkMzFkNDRlZTBhNTZjZTJlNTM2OWM3MTMzOTE4NWNhZjAxMWIxNzY2NGE3YTRjNWRhZjM5MjMxM2Q4YWUxODdmZTI0NzY2M2JmYjI2MDIwMGFjNGIyN2JmNGI0NDIzNTYxMzE1MmJlZDQxODMzYTZlOWViNTE1YjBjMjNiNjkzMmRhNjE2MmQ3OTE0OWY4NTE1MTdiYTgwNDY4MjAzMzcwODA0YjYyZmZi\"}}";
		controlPlaneDefinePub.send(Map.of("body", jsonString), APP_ID, false);
		
	}
	

	@Test
	void sendMessageToBroker() throws InterruptedException
	{
		/*
		String APP_ID = "25a7877c-9de1-4fbd-a427-2396bd7ec2dc";
		String brokerIP = "23.22.103.53";
		String brokerUser="admin";
		int port = 30356;
		String brokerpassword = "vpwfDz7PJ14GZd";
		String topic = "eu.nebulouscloud.ems.boot";
		String payload ="{\"address\":\"10.0.2.105\",\"application\":\"25a7877c-9de1-4fbd-a427-2396bd7ec2dc\"}";*/
		
		String APP_ID = "5d04e1a4-32be-4c39-875b-05ab2d6fb549";
		String brokerIP = "18.212.235.117";
		String brokerUser="admin";
		int port = 30356;
		String brokerpassword = "vpwfDz7PJ14GZd";
		String topic = "eu.nebulouscloud.ems.boot";
		String payload = "payload:{\"when\":\"2025-05-06T21:06:19.504028654Z\",\"metric_list\":[{\"lower_bound\":\"-Infinity\",\"name\":\"NumPendingRequests\",\"upper_bound\":\"Infinity\"},{\"lower_bound\":\"-Infinity\",\"name\":\"AccumulatedSecondsPendingRequests\",\"upper_bound\":\"Infinity\"},{\"lower_bound\":\"-Infinity\",\"name\":\"NumWorkers\",\"upper_bound\":\"Infinity\"},{\"lower_bound\":\"-Infinity\",\"name\":\"PendingRequestsMinusWorkers\",\"upper_bound\":\"Infinity\"},{\"lower_bound\":\"-Infinity\",\"name\":\"RawMaxMessageAge\",\"upper_bound\":\"Infinity\"},{\"lower_bound\":\"-Infinity\",\"name\":\"MeanMaxMessageAge\",\"upper_bound\":\"Infinity\"}],\"name\":\"25a7877c-9de1-4fbd-a427-2396bd7ec2dc\",\"version\":0}";
				
		//"158.37.63.86", 32757, "admin", "nebulous"
		Publisher publisher = new Publisher("controlPlanePublisher",
				topic, true, true);		
		Connector client = new Connector("controlPlaneClient", new ConnectorHandler() {
			public void onReady(AtomicReference<Context> context) {
				LOGGER.info("Optimiser-controller connected to ActiveMQ");
			}
		}, List.of(publisher), List.of(), true, true,new StaticExnConfig(brokerIP, port, brokerUser, brokerpassword));
		client.start();
		Thread.sleep(1000);
		publisher.send(Map.of("body", payload), APP_ID, false);
		
	}
	

	
	@Test
	void sendMetricModelToClusterOnDevEnv() throws Exception
	{
		String APP_ID = "81ae613e-4c87-4db4-8126-c0d6e456d3a7";
		String user ="bridge-"+APP_ID;
		//String NEBULOUS_CONTROL_PLANE_PASSWORD = APP_ID;
		String NEBULOUS_CONTROL_PLANE_PASSWORD = "supersecret";
		LOGGER.info("Starting controlPlaneClient");
		
		Publisher localPublisher = new Publisher("appClusterPublisher", "eu.nebulouscloud.ui.dsl.metric_model",
				true, true);
		Connector localConnector = new Connector("appClusterClient", new ConnectorHandler() {
		//}, List.of(localPublisher), List.of(), false, false,new StaticExnConfig("158.37.63.86", 32757, user, NEBULOUS_CONTROL_PLANE_PASSWORD));
		//}, List.of(localPublisher), List.of(), false, false,new StaticExnConfig("158.37.63.86", 32757, "admin", "nebulous"));
		//}, List.of(localPublisher), List.of(), false, false,new StaticExnConfig("localhost", 61616, "admin", "nebulous"));
		//}, List.of(localPublisher), List.of(), false, false,new StaticExnConfig("52.201.251.8", 30356, user, NEBULOUS_CONTROL_PLANE_PASSWORD));
		}, List.of(localPublisher), List.of(), false, false,new StaticExnConfig("158.37.63.86", 32757, "admin", "nebulous"));
		//}, List.of(localPublisher), List.of(), false, false,new StaticExnConfig("52.201.251.8", 30356, "admin", "nebulous"));
		
		localConnector.start();
		
		while(true)
		{
			localPublisher.send(Map.of("hola", "hola"), APP_ID, false);
			LOGGER.warn("Send");
			Thread.sleep(2000);
		}
		
	}
	
	@Test
	void listenMetricModelOnControlPlane() throws Exception
	{
		//AMQP: 5672:32757
		//ALL:  61616:31313
		LOGGER.info("Starting controlPlaneClient");
		String APP_ID = "81ae613e-4c87-4db4-8126-c0d6e456d3a7";
		String user ="bridge-"+APP_ID;
		Consumer controlPlaneConsummer = new Consumer("monitoring", "eu.nebulouscloud.ui.dsl.metric_model", new Handler() {

			@Override
			public void onMessage(String key, String address, Map body, Message message, Context context) {
				LOGGER.info(body.toString());
			}
		}, true, true);
		
		Connector localConnector = new Connector("appClusterClient", new ConnectorHandler() {		
		//}, List.of(), List.of(controlPlaneConsummer), false, false,new StaticExnConfig("158.37.63.86", 32757, "admin", "nebulous"));
		//}, List.of(), List.of(controlPlaneConsummer), false, false,new StaticExnConfig("158.37.63.86", 32757, "bridge-3a87ca21-f869-4a96-8939-074023b3eadd", "vpwfDz7PJ14GZd"));
		
		}, List.of(), List.of(controlPlaneConsummer), false, false,new StaticExnConfig("158.37.63.86", 32757, "admin", "nebulous"));
		//}, List.of(), List.of(controlPlaneConsummer), false, false,new StaticExnConfig("184.72.76.206", 30356, "admin", "nebulous"));
		//}, List.of(), List.of(controlPlaneConsummer), false, false,new StaticExnConfig("184.72.76.206", 30356,null,null));
		//}, List.of(), List.of(controlPlaneConsummer), false, false,new StaticExnConfig("localhost", 61616,null,null));
		//}, List.of(), List.of(controlPlaneConsummer), false, false,new StaticExnConfig("localhost", 61616,"admin","vpwfDz7PJ14GZd"));
		localConnector.start();
		
		while(true)
		{
			Thread.sleep(2000);
		}
		
	}
}


