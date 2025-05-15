package eut.nebulouscloud.iot_dpp.persistor;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jayway.jsonpath.DocumentContext;

/**
 * A utility class for extracting key-value pairs from JSON documents using JsonPath expressions.
 * 
 * This class supports two extraction patterns:
 * 
 * 1. Full Pattern: BODY|context|key|value
 *    ----------------------------------
 *    Components:
 *    - context: JsonPath expression to navigate to the target JSON node
 *    - key: Literal string or JsonPath to extract keys
 *    - value: Literal string or JsonPath to extract values
 * 
 *    Key-Value Extraction Rules:
 *    - If key is literal and value is JsonPath: Creates one pair with literal key and last value from JsonPath
 *    - If both are literal strings: Creates one pair with exact key and value
 *    - If key is JsonPath and value is literal: Creates pairs for each key with the literal value
 *    - If both are JsonPath: Creates pairs matching keys and values. If counts differ, repeats shorter list
 * 
 *    Example:
 *    <pre>
 *    KeyValueExtractor extractor = new KeyValueExtractor("BODY|$.data|$.keys|$.values");
 *    </pre>
 * 
 * 2. Lite Pattern: BODY|context|keyValue
 *    ----------------------------------
 *    Components:
 *    - context: JsonPath expression to navigate to the target JSON node
 *    - keyValue: JsonPath expression that returns a map-like structure
 * 
 *    Example:
 *    <pre>
 *    KeyValueExtractor extractor = new KeyValueExtractor("BODY|$.data|$.keyValuePairs");
 *    </pre>
 * 
 * The class automatically determines the appropriate extraction method based on the provided pattern.
 */
public class JSONKeyValueExtractor {

	/** Pattern for matching full extraction format: BODY|context|key|value */
	static Pattern bodyKVPatternFull = Pattern.compile("^BODY\\|(.+)\\|([^\\|].+)\\|(.*)$");
	/** Pattern for matching lite extraction format: BODY|context|keyValue */
	static Pattern bodyKVPatternLite = Pattern.compile("^BODY\\|(.+)\\|(.+)$");
	
	static Pattern addresPattern = Pattern.compile("^ADDRESS\\|(.+)\\|(.+)");
	
	RegexTransform addressRegexTransform;

	/** Stores the parts of a full pattern match */
	String[] bodyKVPatternParts;
	/** Stores the parts of a lite pattern match */
	String[] bodyKVPatternLiteParts;

	/**
	 * Constructs a KeyValueExtractor with the specified expression.
	 * The expression must match either the full or lite pattern format.
	 *
	 * @param expression The extraction expression to parse
	 * @throws Exception if the expression doesn't match any supported pattern
	 */
	public JSONKeyValueExtractor(String expression) throws Exception {
		
		if(expression.startsWith("BODY"))
		{
			Matcher bodyKVPatternFullMatcher = bodyKVPatternFull.matcher(expression);
			while (bodyKVPatternFullMatcher.find()) {
				bodyKVPatternParts = new String[] {bodyKVPatternFullMatcher.group(1),bodyKVPatternFullMatcher.group(2),bodyKVPatternFullMatcher.group(3)};			
			}
			if (this.bodyKVPatternParts == null) {
				Matcher bodyKVPatternLiteMatcher = bodyKVPatternLite.matcher(expression);
				while (bodyKVPatternLiteMatcher.find()) {
					this.bodyKVPatternLiteParts = new String[] {bodyKVPatternLiteMatcher.group(1),bodyKVPatternLiteMatcher.group(2)};
				}
			}
		}
		if(expression.startsWith("ADDRESS"))
		{
			Matcher addressPatternMatcher = addresPattern.matcher(expression);
			while (addressPatternMatcher.find()) {
				String regex = addressPatternMatcher.group(1);
				String repl = addressPatternMatcher.group(2);
				addressRegexTransform =new RegexTransform(regex, repl);
			}
		}
		
		

		if (bodyKVPatternParts == null && bodyKVPatternLiteParts == null && addresPattern == null)
			throw new Exception("Invalid extraction '"+expression+"' pattern format. Must match either '" + bodyKVPatternFull.pattern() + "' or '" + bodyKVPatternLite.pattern() + "' or '"+addresPattern.pattern()+"' ");
	}

	/**
	 * Extracts key-value pairs from the provided JsonPath document context.
	 * The extraction method (full or lite) is determined by the pattern used during initialization.
	 *
	 * @param bodyJsonpath The JsonPath document context to extract from
	 * @return A map containing the extracted key-value pairs
	 * @throws Exception 
	 */
	public Map<String, String> extract(String address,DocumentContext bodyJsonpath) throws Exception {
		if(addressRegexTransform!=null)
		{
			String res = addressRegexTransform.eval(address);
			String[] parts = res.split(":");
			if(parts.length!=2)
			{
				throw new Exception("Expected two components");
			}
			return Map.of(parts[0],parts[1]);
		}
			
		if (bodyKVPatternParts != null)
			return extractFull(bodyJsonpath);
		return extractLite(bodyJsonpath);
	}

	/**
	 * Extracts key-value pairs using the lite pattern format.
	 * This method expects the keyValue expression to return a map-like structure.
	 *
	 * @param bodyJsonpath The JsonPath document context to extract from
	 * @return A map containing the extracted key-value pairs
	 */
	private Map<String, String> extractLite(DocumentContext bodyJsonpath) {

		try {
			String jsonpathContextExpression = bodyKVPatternLiteParts[0];
			String keyValueExpression = bodyKVPatternLiteParts[1];
			DocumentContext bodyContext = "$".equals(jsonpathContextExpression) ? bodyJsonpath
					: bodyJsonpath.read(jsonpathContextExpression);

			List values = bodyContext.read(keyValueExpression);
			Map result = new HashMap<String, String>();
			if (values.size() > 0) {

				for (Object value : values) {
					if (value instanceof Map) {
						((Map) value).forEach((k, v) -> {
							result.put(k.toString(), v.toString());
						});
					}
				}
			}
			return result;
		} catch (Exception ex) {
			// LOGGER.error("", ex);

		}
		return new HashMap<String, String>();

	}

	/**
	 * Extracts key-value pairs using the full pattern format.
	 *
	 * @param bodyJsonpath The JsonPath document context to extract from
	 * @return A map containing the extracted key-value pairs
	 */
	private Map<String, String> extractFull(DocumentContext bodyJsonpath) {

		String jsonpathContextExpression = bodyKVPatternParts[0];
		String keyExpression = bodyKVPatternParts[1];
		String valueExpression = bodyKVPatternParts[2];
		DocumentContext bodyContext = "$".equals(jsonpathContextExpression) ? bodyJsonpath
				: bodyJsonpath.read(jsonpathContextExpression);

		if(!isJsonpath(keyExpression) && !isJsonpath(valueExpression)) {
			return new HashMap<String, String>() {{put(keyExpression, valueExpression);}};
		}
		List keys = null;
		if (isJsonpath(keyExpression)) {
			keys = bodyContext.read(keyExpression);
		}
		List values = null;
		if (isJsonpath(valueExpression)) {
			values = bodyContext.read(valueExpression);
		}
		if (keys == null && values == null) {
			return new HashMap<String, String>();
		}
		Map result = new HashMap<String, String>();
		if (keys == null && values != null) {
			values.forEach(v -> result.put(keyExpression, v.toString()));
			return result;
		}
		if (keys != null && values == null) {
			keys.forEach(k -> result.put(k.toString(), valueExpression));
			return result;
		}
		List<String> keysStr = (List<String>) keys.stream().map(k -> k.toString()).collect(Collectors.toList());
		List<String> valuesStr = (List<String>) values.stream().map(v -> v.toString()).collect(Collectors.toList());

		if (keys.size() != values.size()) {
			List<String> b = keys.size() < values.size() ? keysStr : valuesStr;
			int c = keys.size() < values.size() ? values.size() - keys.size() : keys.size() - values.size();
			String fill = keys.size() < values.size() ? keyExpression : valueExpression;
			if (b.size() == 1)
				fill = b.get(0);
			for (int i = 0; i < c; i++)
				b.add(fill);
		}
		for (int i = 0; i < keysStr.size(); i++) {
			result.put(keysStr.get(i), valuesStr.get(i));
		}
		return result;

	}

	/**
	 * Checks if a given expression is a JsonPath expression.
	 * A JsonPath expression starts with '$'.
	 *
	 * @param expr The expression to check
	 * @return true if the expression is a JsonPath expression, false otherwise
	 */
	private boolean isJsonpath(String expr) {
		return expr.startsWith("$");
	}

}
