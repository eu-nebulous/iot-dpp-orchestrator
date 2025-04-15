package eut.nebulouscloud.iot_dpp.persistor;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;

public class DataPersistorPlugin  implements ActiveMQServerMessagePlugin {

	@Override
	public void afterSend(ServerSession session, Transaction tx, Message message, boolean direct,
			boolean noAutoCreateQueue, RoutingStatus result) throws ActiveMQException {
		// TODO Auto-generated method stub
		ActiveMQServerMessagePlugin.super.afterSend(session, tx, message, direct, noAutoCreateQueue, result);
	}
	
}
