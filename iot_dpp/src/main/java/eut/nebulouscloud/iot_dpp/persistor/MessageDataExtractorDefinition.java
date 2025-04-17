package eut.nebulouscloud.iot_dpp.persistor;

import java.util.List;

public class MessageDataExtractorDefinition {
	String filterExpression;
	String bucketExpression;
	String measurementExpression;
	List<String> tagExpressions;
	List<String> fieldExpressions;
	String dateTimeExpression;
}
