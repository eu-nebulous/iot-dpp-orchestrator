package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.InitialContext;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	final String monitoredTopicPrefix;
	final int queryIntervalMS;
	final String localActiveMQURL;
	final String localActiveMQUser;
	final String localActiveMQPassword;

	public QueuesMonitoringProcess(String monitoredTopicPrefix, int queryIntervalMS, String activemqURL, String activemqUser,
			String activemqPassword, QueuesMonitoringPluginConsumer consumer) {
		this.monitoredTopicPrefix = monitoredTopicPrefix;
		this.queryIntervalMS = queryIntervalMS;
		this.localActiveMQURL = activemqURL;
		this.localActiveMQUser = activemqUser;
		this.localActiveMQPassword = activemqPassword;
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
				String addressName = (String) queue;
				if (addressName.startsWith(monitoredTopicPrefix)) {
					ret.add(queue.toString());
				}else
				{
					LOGGER.info("Ignore queue "+queue );
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
				if (addressName.startsWith(monitoredTopicPrefix)) {
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
		LOGGER.info("Connecting to {}", localActiveMQURL);
		connectionFactory = new ActiveMQConnectionFactory(localActiveMQURL);
		connection = connectionFactory.createQueueConnection(localActiveMQUser, localActiveMQPassword);
		session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
		managementQueue = session.createQueue("activemq.management");
		connection.start();
		requestor = new QueueRequestor(session, managementQueue);
	}

	private Integer getMessageCount(String queue) throws Exception {
		Message m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + queue, "getMessageCount");
		Message reply = requestor.request(m);
		if (JMSManagementHelper.hasOperationSucceeded(reply)) {
			return (Integer) JMSManagementHelper.getResult(reply, Integer.class);
		} else {
			LOGGER.error("Can't query message count for queue " + queue);
			return null;
		}
	}

	private Long getOldestMessageAge(String queue) throws Exception {

		Message m = session.createMessage();
		m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + queue, "getFirstMessageAge");
		Message reply = requestor.request(m);
		if (JMSManagementHelper.hasOperationSucceeded(reply)) {
			return (Long) JMSManagementHelper.getResult(reply, Long.class);
		} else {
			LOGGER.error("Can't query message age for queue " + queue);
			return null;
		}

	}

	private Integer getConsumersCount(String queue) throws Exception {

		Message m = session.createMessage();
		m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + queue, "getConsumerCount");
		Message reply = requestor.request(m);
		if (JMSManagementHelper.hasOperationSucceeded(reply)) {
			return (Integer) JMSManagementHelper.getResult(reply, Integer.class);
		} else {
			LOGGER.error("Can't query consumerCount for queue " + queue);
			return null;
		}

	}

	private Integer getGroupCount(String queue) throws Exception {

		Message m = session.createMessage();
		m = session.createMessage();
		JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + queue, "getGroupCount");
		Message reply = requestor.request(m);
		if (JMSManagementHelper.hasOperationSucceeded(reply)) {
			return (Integer) JMSManagementHelper.getResult(reply, Integer.class);
		} else {
			LOGGER.error("Can't query getGroupCount for queue " + queue);
			return null;
		}

	}

	private List<QueuesMonitoringMessage> collectMetrics() throws Exception {

		List<QueuesMonitoringMessage> ret = new LinkedList<QueuesMonitoringMessage>();
		List<String> queues = getQueuesNames();

		for (String queue : queues) {
			QueuesMonitoringMessage message = new QueuesMonitoringMessage(queue);
			message.messageCount = getMessageCount(queue);
			message.consumersCount = getConsumersCount(queue);
			message.groupCount = getGroupCount(queue);
			if (message.messageCount > 0) {
				message.maxMessageAge = getOldestMessageAge(queue);
			}
			LOGGER.info("Collected metrics for queue {}:{}", queue, message);
			ret.add(message);
		}
		return ret;
	}

	@Override
	public void run() {

		System.setProperty("java.naming.factory.initial",
				"org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");

		while (true) {
			try {

				LOGGER.info("QueuesMonitoringProcess loop");
				if (connection == null)
					connect();
				
				List<QueuesMonitoringMessage> metrics = collectMetrics();
				if(consumer!=null)
				{
					consumer.consume(metrics);	
				}else
				{
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
