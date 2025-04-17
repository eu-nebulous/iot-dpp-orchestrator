package eut.nebulouscloud.iot_dpp.persistor;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class MessageDataExtractionUtils {
	static Logger LOGGER = LoggerFactory.getLogger(MessageDataExtractionUtils.class);
	static Pattern addresPattern = Pattern.compile("\\{ADDRESS\\|(.+)\\|(.+)\\}");
	static Pattern bodyPattern = Pattern.compile("\\{BODY\\|([^}]+)\\}");
	static Pattern bodyKVPatternFull = Pattern.compile("\\{BODY\\|(.+)\\|(.+)\\|(.*)\\}");
	static Pattern bodyKVPatternLite = Pattern.compile("\\{BODY\\|(.+)\\|(.+)\\}");
	static Pattern timePattern = Pattern.compile("^BODY\\|(.+)\\|(.+)$");
	static Pattern acceptorPattern = Pattern.compile("^(.+)\\|(.+)\\|(AND|OR)$");

	public static String regexTransform(String text, String matchExpression, String resultExpression) {
		try {
			// Pattern matchgroupPattern = Pattern.compile("\\$[1-9]+");
			Pattern matchExpressionPattern = Pattern.compile(matchExpression);
			// Pattern matchPattern = Pattern.compile(matchExpression);
			Matcher m = matchExpressionPattern.matcher(text);
			if (m.matches()) {
				String result = resultExpression;
				for (int i = 1; i < m.groupCount() + 1; i++) {
					result = result.replaceAll("\\$" + (i), m.group(i));
				}
				return result;

			} else {
				return text;
			}
		} catch (Exception ex) {
			LOGGER.error("Couldn't extract apply regex transform to '{}' matchExpression:'{}' resultExpression:'{}'",
					text, matchExpression, resultExpression, ex);
			return text;
		}

	}

	public static String evalStringExpression(String expression, String address, String bodyContext) {
		String finalExpression = expression;
		Matcher addressPatternMatcher = addresPattern.matcher(expression);
		while (addressPatternMatcher.find()) {
			String regex = addressPatternMatcher.group(1);
			String repl = addressPatternMatcher.group(2);
			String res = regexTransform(address, regex, repl);
			if (res != null) {
				finalExpression = finalExpression.replace(addressPatternMatcher.group(0), res);
			}
		}
		Matcher bodyPatternMatcher = bodyPattern.matcher(expression);
		Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
				.addOptions(Option.ALWAYS_RETURN_LIST);
		while (bodyPatternMatcher.find()) {
			String jsonpathExpr = bodyPatternMatcher.group(1);
			try {
				List res = JsonPath.using(conf).parse(bodyContext).read(jsonpathExpr);
				if (res != null && !res.isEmpty()) {
					finalExpression = finalExpression.replace(bodyPatternMatcher.group(0), res.get(0).toString());
				}
			} catch (Exception ex) {
				LOGGER.error("Couldn't eval JsonPath '{}' on body '{}' resultExpression:'{}'", jsonpathExpr,
						bodyContext, ex);
			}
		}
		return finalExpression;
	}

	private static boolean isJsonpath(String expr) {
		return expr.startsWith("$");
	}

	public static List<Pair<String, String>> evalKVExpression(String expression, String address, String body) {

		Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
				.addOptions(Option.ALWAYS_RETURN_LIST);
		Matcher bodyKVPatternFullMatcher = bodyKVPatternFull.matcher(expression);
		while (bodyKVPatternFullMatcher.find()) {
			try {
				String jsonpathContextExpression = bodyKVPatternFullMatcher.group(1);
				String keyExpression = bodyKVPatternFullMatcher.group(2);
				String valueExpression = bodyKVPatternFullMatcher.group(3);
				String bodyContext = "$".equals(jsonpathContextExpression) ? body
						: JsonPath.using(conf).parse(body).read(jsonpathContextExpression).toString();

				List keys = null;
				if (isJsonpath(keyExpression)) {
					keys = JsonPath.using(conf).parse(bodyContext).read(keyExpression);
				}
				List values = null;
				if (isJsonpath(valueExpression)) {
					values = JsonPath.using(conf).parse(bodyContext).read(valueExpression);
				}
				if (keys == null && values == null) {
					return new LinkedList<Pair<String, String>>();
				}
				List<Pair<String, String>> result = new LinkedList<Pair<String, String>>();
				if (keys == null && values != null) {
					values.forEach(v -> result.add(new Pair<String, String>(keyExpression, v.toString())));
					return result;
				}
				if (keys != null && values == null) {
					keys.forEach(k -> result.add(new Pair<String, String>(k.toString(), valueExpression)));
					return result;
				}
				List<String> keysStr = (List<String>) keys.stream().map(k -> k.toString()).collect(Collectors.toList());
				List<String> valuesStr = (List<String>) values.stream().map(v -> v.toString())
						.collect(Collectors.toList());

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
					result.add(new Pair<String, String>(keysStr.get(i), valuesStr.get(i)));
				}
				return result;

			} catch (Exception ex) {
				LOGGER.error("", ex);
			}
		}
		Matcher bodyKVPatternLiteMatcher = bodyKVPatternLite.matcher(expression);
		while (bodyKVPatternLiteMatcher.find()) {
			try {
				String jsonpathContextExpression = bodyKVPatternLiteMatcher.group(1);
				String keyValueExpression = bodyKVPatternLiteMatcher.group(2);
				String bodyContext = "$".equals(jsonpathContextExpression) ? body
						: JsonPath.using(conf).parse(body).read(jsonpathContextExpression).toString();
				List values = JsonPath.using(conf).parse(bodyContext).read(keyValueExpression);
				List<Pair<String, String>> result = new LinkedList<Pair<String, String>>();
				if (values.size() > 0) {

					for (Object value : values) {
						if (value instanceof Map) {
							((Map) value).forEach((k, v) -> {
								result.add(new Pair<String, String>(k.toString(), v.toString()));
							});
						}
					}
				}
				return result;
			} catch (Exception ex) {
				LOGGER.error("", ex);

			}
		}
		return new LinkedList<Pair<String, String>>();
	}

	public static Double evalNumericExpression(String expression, String bodyContext) {
		return Double.parseDouble(evalStringExpression(expression, "", bodyContext));
	}

	public static Date evalTimeExpression(String expression, String bodyContext) {
		Matcher timePatternMatcher = timePattern.matcher(expression);
		Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
				.addOptions(Option.ALWAYS_RETURN_LIST);
		while (timePatternMatcher.find()) {
			String jsonpathExpr = timePatternMatcher.group(1);
			String dateFormatExpr = timePatternMatcher.group(2);
			try {
				List res = JsonPath.using(conf).parse(bodyContext).read(jsonpathExpr);
				if (res != null && !res.isEmpty()) {

					if ("TIMESTAMP".equals(dateFormatExpr)) {
						return new Date(Long.parseLong(res.get(0).toString()));

					} else {
						SimpleDateFormat formatter = new SimpleDateFormat(dateFormatExpr);
						return formatter.parse(res.get(0).toString());

					}
				}
			} catch (Exception ex) {
				LOGGER.error("Couldn't eval JsonPath '{}' on body '{}' resultExpression:'{}'", jsonpathExpr,
						bodyContext, ex);
			}
		}
		return null;
	}

	public static boolean evalAcceptorExpression(String expression, String address, String bodyContext) {
		Matcher acceptorPatternMatcher = acceptorPattern.matcher(expression);
		Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
				.addOptions(Option.ALWAYS_RETURN_LIST);
		while (acceptorPatternMatcher.find()) {
			String addressRegex = acceptorPatternMatcher.group(1);
			boolean addressMatched = address.matches(addressRegex);
			String bodyJsonPath = acceptorPatternMatcher.group(2);
			boolean bodyMatched = false;
			try {
				List res = JsonPath.using(conf).parse(bodyContext).read(bodyJsonPath);
				bodyMatched = !res.isEmpty() && res.get(0)!=null;
			} catch (Exception ex) {
				LOGGER.error("", ex);
			}
			String joiner = acceptorPatternMatcher.group(3);
			if ("AND".equals(joiner))
				return bodyMatched && addressMatched;
			if ("OR".equals(joiner))
				return bodyMatched || addressMatched;

		}
		return false;

	}

}
