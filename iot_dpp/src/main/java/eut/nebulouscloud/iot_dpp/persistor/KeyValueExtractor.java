package eut.nebulouscloud.iot_dpp.persistor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jayway.jsonpath.DocumentContext;

public class KeyValueExtractor {

	static Pattern bodyKVPatternFull = Pattern.compile("^BODY\\|(.+)\\|([^\\|].+)\\|(.*)$");
	static Pattern bodyKVPatternLite = Pattern.compile("^BODY\\|(.+)\\|(.+)$");

	String[] bodyKVPatternParts;
	String[] bodyKVPatternLiteParts;

	public KeyValueExtractor(String expression) throws Exception {
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

		if (bodyKVPatternParts == null && bodyKVPatternLiteParts == null)
			throw new Exception("Couldn't init");
	}

	public Map<String, String> extract(DocumentContext bodyJsonpath) {
		if (bodyKVPatternParts != null)
			return extractFull(bodyJsonpath);
		return extractLite(bodyJsonpath);
	}

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

	private Map<String, String> extractFull(DocumentContext bodyJsonpath) {

		String jsonpathContextExpression = bodyKVPatternParts[0];
		String keyExpression = bodyKVPatternParts[1];
		String valueExpression = bodyKVPatternParts[2];
		DocumentContext bodyContext = "$".equals(jsonpathContextExpression) ? bodyJsonpath
				: bodyJsonpath.read(jsonpathContextExpression);

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

	private static boolean isJsonpath(String expr) {
		return expr.startsWith("$");
	}

}
