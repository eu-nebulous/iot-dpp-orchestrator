package eut.nebulouscloud.iot_dpp.persistor;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.influxdb.client.domain.WritePrecision;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class MessageDataExtractor {
	static Logger LOGGER = LoggerFactory.getLogger(MessageDataExtractor.class);


	
	private MessageFilter filter;
	private StringExtractor bucketExtractor;
	private StringExtractor measurementExtractor;	
	private List<JSONKeyValueExtractor> fieldExtractors;
	private List<JSONKeyValueExtractor> tagExtractors;
	private TimeExtractor timeExtractor;
	public static Configuration conf = Configuration.defaultConfiguration()
			.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL).addOptions(Option.ALWAYS_RETURN_LIST);

	MessageDataExtractorDefinition definition;

	public MessageDataExtractor(MessageDataExtractorDefinition definition) throws Exception {
		this.definition = definition;
		
		if (definition.filterExpression != null){
			this.filter = new MessageFilter(definition.filterExpression);
		}
		else{
			throw new Exception("Filter expression is required");
		}
		if (definition.bucketExpression != null){
			this.bucketExtractor = new StringExtractor(definition.bucketExpression);
		}
		else{
			throw new Exception("Bucket expression is required");	
		}
		if (definition.measurementExpression != null){
			this.measurementExtractor = new StringExtractor(definition.measurementExpression);
		}
		else{
			throw new Exception("Measurement expression is required");
		}
		if (definition.tagExpressions != null){
			this.tagExtractors = new ArrayList<>();
			for (String tagExpression : definition.tagExpressions) {
				this.tagExtractors.add(new JSONKeyValueExtractor(tagExpression));
			}
		}else
		{
			this.tagExtractors = new ArrayList<>();
		}
	
		if(definition.fieldExpressions != null){
			this.fieldExtractors = new ArrayList<>();
			for (String fieldExpression : definition.fieldExpressions) {
				this.fieldExtractors.add(new JSONKeyValueExtractor(fieldExpression));
			}
		}else{
			this.fieldExtractors = new ArrayList<>();
		}

		if (definition.dateTimeExpression != null)
			this.timeExtractor = new TimeExtractor(definition.dateTimeExpression);
	}
	
	class Point
	{
		
		public Point(String bucket, com.influxdb.client.write.Point point) {
			super();
			this.bucket = bucket;
			this.point = point;
		}
		String bucket;
		com.influxdb.client.write.Point point;
		
	}

	public  Point extract(String address, DocumentContext bodyJsonpath) throws Exception {				
		
		if (!filter.eval(address, bodyJsonpath)){
			LOGGER.info("Message filtered out - Address: {},  Body: {}, Filter Expression: {},", 
				address, 				
				bodyJsonpath.jsonString(),
				definition.filterExpression);
			return null;
		}
		String measurement = measurementExtractor.extract(address, bodyJsonpath);
		com.influxdb.client.write.Point point = com.influxdb.client.write.Point.measurement(measurement);

		String bucket = bucketExtractor.extract(address, bodyJsonpath);
		

		
		HashMap<String, String> tags = new HashMap<>();
		for (JSONKeyValueExtractor tagExtractor : tagExtractors) {
			tags.putAll(tagExtractor.extract(address,bodyJsonpath));
		}
		for (Map.Entry<String, String> tag : tags.entrySet()) {
			point.addTag(tag.getKey(), tag.getValue());
		}
		HashMap<String, String> fields = new HashMap<>();
		for(JSONKeyValueExtractor fieldExtractor : fieldExtractors){
			fields.putAll(fieldExtractor.extract(address,bodyJsonpath));
		}
		for (Map.Entry<String, String> field : fields.entrySet()) {
			try {
				point.addField(field.getKey(), Double.valueOf(field.getValue()));
			}catch(Exception ex)
			{
				point.addField(field.getKey(), field.getValue());	
			}
			
		}
		if(timeExtractor != null){
			point.time(timeExtractor.extract(bodyJsonpath).getTime(), WritePrecision.MS);
		}else{
			point.time(new Date().getTime(), WritePrecision.MS);
		}
		
		return new Point(bucket,point);
	}


}
