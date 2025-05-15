package eut.nebulouscloud.iot_dpp.persistor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.DocumentContext;

public class MessageFilter {
	static Pattern filterDefinitionPattern = Pattern.compile("^(.+)\\|(.+)\\|(AND|OR)$");
	String addressRegex;
	String bodyJsonpath;
	boolean joinerIsAnd;

	public MessageFilter(String filterDefinition) throws Exception {
		Matcher filterDefinitionMatcher = filterDefinitionPattern.matcher(filterDefinition);
		boolean init = false;
		while (filterDefinitionMatcher.find()) {
			addressRegex = filterDefinitionMatcher.group(1);
			bodyJsonpath = filterDefinitionMatcher.group(2);
			String joiner = filterDefinitionMatcher.group(3);
			switch (joiner) {
			case "AND":
				joinerIsAnd = true;
				break;
			case "OR":
				joinerIsAnd = false;
				break;
			default:
				throw new Exception("Unknown joiner " + joiner);
			}
			init = true;
		}
		if (!init)
			throw new Exception("Couldn't the parser");
	}

	public boolean eval(String address, DocumentContext bodyContext) {
		boolean bodyMatched = false;
		try {
			List res = bodyContext.read(bodyJsonpath);
			bodyMatched = !res.isEmpty() && res.get(0) != null;
		} catch (Exception ex) {
			// LOGGER.error("", ex);
		}
		boolean addressMatched = address.matches(addressRegex);

		if (joinerIsAnd)
			return bodyMatched && addressMatched;
		else
			return bodyMatched || addressMatched;

	}
}
