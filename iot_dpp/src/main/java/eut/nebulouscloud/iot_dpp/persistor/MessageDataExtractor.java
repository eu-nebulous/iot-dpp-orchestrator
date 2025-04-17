package eut.nebulouscloud.iot_dpp.persistor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class MessageDataExtractor {
	static Logger LOGGER = LoggerFactory.getLogger(MessageDataExtractor.class);


	private TimeExtractor timeExtractor;
	private MessageFilter filter;
	private KeyValueExtractor labelsExtractor;
	
	private static Configuration conf = Configuration.defaultConfiguration()
			.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL).addOptions(Option.ALWAYS_RETURN_LIST);

	MessageTransformerDefinition definition;

	public MessageDataExtractor(MessageTransformerDefinition definition) throws Exception {
		this.definition = definition;
		if (definition.dateTimeExpression != null)
			this.timeExtractor = new TimeExtractor(definition.dateTimeExpression);
		if (definition.filterExpression != null)
			this.filter = new MessageFilter(definition.filterExpression);
	}

	public Parser buildParser(String address, String body) {
		return new Parser(address, body);
	}

	private class Parser {
		String address;
		String bodyString;
		DocumentContext bodyJsonpath;

		public Parser(String address, String body) {
			this.address = address;
			this.bodyString = body;
			this.bodyJsonpath = JsonPath.using(conf).parse(body);

		}

		/*
		 * public boolean evalAcceptor() { if (definition.filterExpression == null)
		 * return true;
		 * 
		 * }
		 */

	}

}
