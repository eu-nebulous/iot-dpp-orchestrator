package eut.nebulouscloud.iot_dpp.persistor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.DocumentContext;

public class MessageFilter {
	static Pattern acceptorPattern = Pattern.compile("^(.+)\\|(.+)\\|(AND|OR)$");
	String acceptorAddressRegex;
	String acceptorBodyJsonpath;
	boolean acceptorJoinerIsAnd;

	public MessageFilter(String acceptorExpression) throws Exception {
		Matcher acceptorPatternMatcher = acceptorPattern.matcher(acceptorExpression);
		boolean init = false;
		while (acceptorPatternMatcher.find()) {
			acceptorAddressRegex = acceptorPatternMatcher.group(1);
			acceptorBodyJsonpath = acceptorPatternMatcher.group(2);
			String acceptorJoiner = acceptorPatternMatcher.group(3);
			switch (acceptorJoiner) {
			case "AND":
				acceptorJoinerIsAnd = true;
				break;
			case "OR":
				acceptorJoinerIsAnd = false;
				break;
			default:
				throw new Exception("Unknown joiner " + acceptorJoiner);
			}
			init = true;
		}
		if (!init)
			throw new Exception("Couldn't the parser");
	}

	public boolean eval(String address, DocumentContext bodyJsonpath) {
		boolean bodyMatched = false;
		try {
			List res = bodyJsonpath.read(acceptorBodyJsonpath);
			bodyMatched = !res.isEmpty() && res.get(0) != null;
		} catch (Exception ex) {
			// LOGGER.error("", ex);
		}
		boolean addressMatched = address.matches(acceptorAddressRegex);

		if (acceptorJoinerIsAnd)
			return bodyMatched && addressMatched;
		else
			return bodyMatched || addressMatched;

	}
}
