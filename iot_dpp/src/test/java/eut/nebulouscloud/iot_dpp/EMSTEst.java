package eut.nebulouscloud.iot_dpp;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.ser1.stomp.Client;
import net.ser1.stomp.Listener;

public class EMSTEst {
	static Logger LOGGER = LoggerFactory.getLogger(EMSTEst.class);

	public static void main(String[] args) {
		try {
		String emsURL = "tcp://localhost:61613";
		String emsUser = "artemis";
		String emsPassword = "artemis";
		String topic = "atesttopic";
		System.out.println("xxxxxxxxxx");
		Client c = new Client( "localhost", 61613, "artemis", "artemis" );
		  c.addErrorListener( new Listener() {
			    public void message( Map header, String message ) {
			    	System.out.println(header);
			    	System.out.println(message);
			    }
			  });
		  
		  c.subscribe(topic,
				  new Listener() {
				
				@Override
				public void message(Map arg0, String arg1) {
					System.out.println(arg0);
					System.out.println(arg1);
					System.out.println("---");
					// TODO Auto-generated method stub
					
				}});


		while (true) {
			try {
				c.send(topic, "hello");
			} catch (Exception ex) {
				LOGGER.error("", ex);
			}
			LOGGER.warn("hoof");
			Thread.sleep(1000);
		}
		}catch(Exception ex)
		{
			LOGGER.error("", ex);
		}

	}

}
