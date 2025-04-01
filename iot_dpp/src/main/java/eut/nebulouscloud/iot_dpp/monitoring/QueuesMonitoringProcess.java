package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.InitialContext;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.server.JMSServerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin.QueuesMonitoringPluginConsumer;

public class QueuesMonitoringProcess implements Runnable {
	Logger LOGGER = LoggerFactory.getLogger(QueuesMonitoringProcess.class);
	QueueConnection connection = null;
	ActiveMQConnectionFactory connectionFactory;
	InitialContext initialContext = null;
	QueueSession session = null;
	Queue managementQueue;
	QueueRequestor requestor;
	public QueuesMonitoringPluginConsumer consumer;
	final ActiveMQServer server;
	final String monitoredQueueRegex;
	final int queryIntervalMS;
	private JMSServerManager jmsServer;

	public QueuesMonitoringProcess(ActiveMQServer server, String monitoredQueueRegex, int queryIntervalMS, QueuesMonitoringPluginConsumer consumer) {
		this.server = server;
		
		this.monitoredQueueRegex = monitoredQueueRegex;
		this.queryIntervalMS = queryIntervalMS;
		this.consumer = consumer;
	}

	/**
	 * Retrieves server name from consumer reference.
	 * 
	 * @param consumer
	 * @return
	 */
	private String getServerName(ServerConsumer consumer) {
		// TODO: Find a better way of retrieving server name rather than parsing the
		// string representation of the consumer object.
		return consumer.toString().split("server=ActiveMQServerImpl::name=")[1].split("]")[0];
	}

	private List<String> getQueuesNames() throws Exception {
		
		Message m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "getQueueNames");
		Message reply = requestor.request(m);
		List<String> ret = new LinkedList<String>();
		boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
		if (success) {
			Object[] queues = (Object[]) JMSManagementHelper.getResult(reply);

			for (Object queue : queues) {
				String queueName = (String) queue;
				if (queueName.matches(monitoredQueueRegex)) {
					ret.add(queue.toString());
				} else {
					LOGGER.info("Ignore queue " + queue+" "+monitoredQueueRegex);
				}

			}
		}
		return ret;
	}


	private List<String> getAddressesNames() throws Exception {
		Message m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "getAddressNames");
		Message reply = requestor.request(m);
		List<String> ret = new LinkedList<String>();
		boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
		if (success) {
			Object[] addresses = (Object[]) JMSManagementHelper.getResult(reply);

			for (Object address : addresses) {
				String addressName = (String) address;
				if (addressName.startsWith(monitoredQueueRegex)) {
					ret.add(address.toString());
				}

			}
		}
		return ret;
	}

	// https://activemq.apache.org/hello-world
	// https://activemq.apache.org/components/artemis/documentation/1.5.3/using-jms.html
	// https://activemq.apache.org/components/artemis/documentation/latest/using-jms
	// https://activemq.apache.org/components/artemis/documentation/javadocs/javadoc-latest/org/apache/activemq/artemis/api/core/management/QueueControl.html#browse(int,int)

	private void connect() throws Exception {
		initialContext = new InitialContext();
		LOGGER.info("Connecting to local activemq");
		connectionFactory = new ActiveMQConnectionFactory( "vm://0");
        connection = connectionFactory.createQueueConnection();
        session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
		managementQueue = session.createQueue("activemq.management");
		connection.start();
		requestor = new QueueRequestor(session, managementQueue);
	}
	
	private long getLongMetricFromQueue(String queue,String operation, long defaultValue)  
	{
		try {
		Message m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + queue, operation);
		Message reply = requestor.request(m);
		if (JMSManagementHelper.hasOperationSucceeded(reply)) {
			
			Long ret=  (Long) JMSManagementHelper.getResult(reply, Long.class);
			return ret!=null?ret:defaultValue;
			
		} else {
			LOGGER.error("Can't execute "+operation+" on queue " + queue);
			return defaultValue;
		}
		}
		catch(Exception ex)
		{
			LOGGER.error("Exception executing operation "+operation+" on queue " + queue,ex);
			return defaultValue;
		}
	}

	private int getIntegerMetricFromQueue(String queue,String operation, int defaultValue)  
	{
		try {
		Message m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + queue, operation);
		Message reply = requestor.request(m);
		if (JMSManagementHelper.hasOperationSucceeded(reply)) {
			
			Integer ret=  (Integer) JMSManagementHelper.getResult(reply, Integer.class);
			return ret!=null?ret:defaultValue;
			
		} else {
			LOGGER.error("Can't execute "+operation+" on queue " + queue);
			return defaultValue;
		}
		}
		catch(Exception ex)
		{
			LOGGER.error("Exception executing operation "+operation+" on queue " + queue,ex);
			return defaultValue;
		}
	}
	private long getMessageCount(String queue)  {
		return getLongMetricFromQueue(queue, "getMessageCount",-1l);	
	}
	
	
	
	
	private long getMessagesAdded(String queue) {
		return getLongMetricFromQueue(queue, "getMessagesAdded",-1l);
	}

	private long getOldestMessageAge2(String queue)  {
		return getLongMetricFromQueue(queue, "getFirstMessageAge",-1l);
	}
	
	private long getOldestMessageAge(String queue) {
		
		try {
			Message m = session.createMessage();
			m = session.createMessage();
			JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + queue, "listDeliveringMessagesAsJSON");
			Message reply = requestor.request(m);
			if (JMSManagementHelper.hasOperationSucceeded(reply)) {		
				String ret =  (String)JMSManagementHelper.getResult(reply, String.class);
				Long timestamp = ((Long)new ObjectMapper().readValue(ret, Map.class).get("timestamp"));				
				return ret!=null?(new Date().getTime()-timestamp):-1;
				
			} else {
				LOGGER.error("Can't query message age for queue " + queue);
				return -1l;
			}
		} catch (Exception ex) {
			return -1l;
		}

	}
	
	

	private int getConsumersCount(String queue) {
		return getIntegerMetricFromQueue(queue, "getConsumerCount",-1);		
	}

	private int getGroupCount(String queue) {
		return getIntegerMetricFromQueue(queue, "getGroupCount",-1);	
	}

	Map<String,QueuesMonitoringMessage> previousMetrics = new HashMap<String, QueuesMonitoringMessage>();
	private List<QueuesMonitoringMessage> collectMetrics() throws Exception {

		List<QueuesMonitoringMessage> ret = new LinkedList<QueuesMonitoringMessage>();
		List<String> queues = getQueuesNames();

		for (String queue : queues) {
			QueuesMonitoringMessage message = new QueuesMonitoringMessage(queue);
			message.time = new Date();
			message.messageCount = getMessageCount(queue);
			message.consumersCount = getConsumersCount(queue);
			message.groupCount = getGroupCount(queue);
			if (message.messageCount > 0) {
				message.maxMessageAge = getOldestMessageAge2(queue);
			}
			
			message.messagesAdded = getMessagesAdded(queue);
			
			if(previousMetrics.containsKey(queue))
			{
				long previousMessagesAdded = previousMetrics.get(queue).messagesAdded;
				message.messagesAddedFrequency = (message.messagesAdded-previousMessagesAdded)/((message.time.getTime() - previousMetrics.get(queue).time.getTime())/1000.0);
				
			}else
			{
				message.messagesAddedFrequency = 0.0;
			}
			previousMetrics.put(queue, message);
			LOGGER.info("Collected metrics for queue {}:{}", queue, message);
			ret.add(message);
		}
		return ret;
	}

	@Override
	public void run() {

		while (server != null && !server.isActive()) {
			LOGGER.info("Wait for server activation");

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.setProperty("java.naming.factory.initial",
				"org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");

		while (true) {
			try {

				LOGGER.info("QueuesMonitoringProcess loop");
				if (connection == null)
					connect();

				List<QueuesMonitoringMessage> metrics = collectMetrics();
				if (consumer != null) {
					consumer.consume(metrics);
				} else {
					LOGGER.warn("QueuesMonitoringProcess doesn't have a consumer to consume collected metrics");
				}

				LOGGER.info("Waiting {} for next round", queryIntervalMS);
				Thread.sleep(queryIntervalMS);

			} catch (Exception ex) {
				LOGGER.error("", ex);
				try {

					try {
						connection.close();
					} catch (Exception ex4) {
					}
					try {
						connectionFactory.close();
					} catch (Exception ex4) {
					}
					try {
						initialContext.close();
					} catch (Exception ex4) {
					}
					try {
						session.close();
					} catch (Exception ex4) {
					}
					try {
						requestor.close();
					} catch (Exception ex4) {
					}
					connection = null;
					connectionFactory = null;
					initialContext = null;
					session = null;
					requestor = null;
					Thread.sleep(3000);
				} catch (Exception ex2) {
				}

			}

		}

	}

}
