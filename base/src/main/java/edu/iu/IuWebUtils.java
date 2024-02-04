package edu.iu;

import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Provides useful utility methods for low-level web client and server
 * interactions.
 */
public final class IuWebUtils {

	private static final Map<String, InetAddress> IP_CACHE = new IuCacheMap<>(Duration.ofSeconds(5L));

	/**
	 * Parses a query string.
	 * 
	 * @param queryString query string
	 * @return {@link Map}
	 */
	public static Map<String, ? extends Iterable<String>> parseQueryString(String queryString) {
		final Map<String, Queue<String>> parsedParameterValues = new LinkedHashMap<>();

		var startOfName = queryString.startsWith("?") ? 1 : 0;
		if (startOfName == queryString.length())
			return parsedParameterValues;

		var endOfName = queryString.indexOf('=', startOfName);
		var endOfValue = queryString.indexOf('&', startOfName);

		int startOfValue;
		if (endOfValue == -1)
			endOfValue = queryString.length();

		if (endOfName == -1 //
				|| endOfName > endOfValue)
			endOfName = startOfValue = endOfValue;
		else
			startOfValue = endOfName + 1;

		while (true) {
			final var name = queryString.substring(startOfName, endOfName);

			var values = parsedParameterValues.get(name);
			if (values == null)
				parsedParameterValues.put(name, values = new ArrayDeque<>());

			if (endOfValue == queryString.length()) {
				final var value = queryString.substring(startOfValue);
				values.offer(IuException.unchecked(() -> URLDecoder.decode(value, "UTF-8")));
				endOfName = -1;
				break;
			}

			final var value = queryString.substring(startOfValue, endOfValue);
			values.offer(IuException.unchecked(() -> URLDecoder.decode(value, "UTF-8")));

			startOfName = endOfValue + 1;
			endOfName = queryString.indexOf('=', startOfName);
			endOfValue = queryString.indexOf('&', startOfName);
			if (endOfValue == -1)
				endOfValue = queryString.length();

			if (endOfName == -1 //
					|| endOfName > endOfValue)
				endOfName = startOfValue = endOfValue;
			else
				startOfValue = endOfName + 1;
		}

		return parsedParameterValues;
	}

	/**
	 * Creates a query string from a map.
	 * 
	 * @param params {@link Map} of parameter values
	 * @return query string
	 */
	public static String createQueryString(Map<String, ? extends Iterable<String>> params) {
		final var queryString = new StringBuilder();
		for (final var paramEntry : params.entrySet())
			for (final var paramValue : paramEntry.getValue()) {
				if (queryString.length() > 0)
					queryString.append('&');
				queryString.append(IuException.unchecked(() -> URLEncoder.encode(paramEntry.getKey(), "UTF-8")));
				queryString.append("=").append(IuException.unchecked(() -> URLEncoder.encode(paramValue, "UTF-8")));
			}
		return queryString.toString();
	}

	/**
	 * Parses a header value composed of key/value pairs separated by semicolon ';'.
	 * 
	 * @param headerValue header value
	 * @return {@link Map} of header elements
	 */
	public static Map<String, String> parseHeader(String headerValue) {
		int semicolon = headerValue.indexOf(';');
		if (semicolon == -1)
			return Collections.singletonMap("", headerValue);

		final Map<String, String> parsedHeader = new LinkedHashMap<>();
		parsedHeader.put("", headerValue.substring(0, semicolon));

		while (semicolon < headerValue.length()) {
			final var start = semicolon + 1;
			final var eq = headerValue.indexOf('=', start + 1);

			semicolon = headerValue.indexOf(';', start + 1);
			if (semicolon == -1)
				semicolon = headerValue.length();

			if (eq == -1 || eq > semicolon)
				parsedHeader.put(headerValue.substring(start, semicolon).trim(), "");
			else {
				final var elementName = headerValue.substring(start, eq).trim();
				final String elementValue = headerValue.substring(eq + 1, semicolon).trim();
				parsedHeader.put(elementName, elementValue);
			}
		}

		return parsedHeader;
	}

	/**
	 * Validates and normalizes case for an HTTP header name.
	 * 
	 * <p>
	 * Follows each hyphen '-' character with an upper case character; converts
	 * other characters {@link Character#toLowerCase(char) to lower case}
	 * </p>
	 * 
	 * @param headerName HTTP header name
	 * @return {@link String}
	 * @throws IllegalArgumentException If the name contains non-alphabetic
	 *                                  characters other than hyphen '-', or if the
	 *                                  name begins or ends with a hyphen.
	 */
	public static String normalizeHeaderName(String headerName) throws IllegalArgumentException {
		if (!headerName.matches("\\p{Alpha}+(\\-\\p{Alpha}+?)*"))
			throw new IllegalArgumentException("Invalid header name " + headerName);

		final var sb = new StringBuilder(headerName.toLowerCase());
		sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
		for (int i = headerName.indexOf('-') + 1; //
				i != 0; //
				i = headerName.indexOf('-', i + 1) + 1)
			sb.setCharAt(i, Character.toUpperCase(sb.charAt(i)));

		return sb.toString();
	}

	/**
	 * Describes an HTTP status code.
	 * 
	 * @param statusCode HTTP status code
	 * @return {@link String}
	 */
	public static String describeStatus(int statusCode) {
		switch (statusCode) {
		case 100:
			return statusCode + " CONTINUE";
		case 101:
			return statusCode + " SWITCHING PROTOCOLS";
		case 200:
			return statusCode + " OK";
		case 201:
			return statusCode + " CREATED";
		case 202:
			return statusCode + " ACCEPTED";
		case 203:
			return statusCode + " NON AUTHORITATIVE INFORMATION";
		case 204:
			return statusCode + " NO CONTENT";
		case 205:
			return statusCode + " RESET CONTENT";
		case 206:
			return statusCode + " PARTIAL CONTENT";
		case 300:
			return statusCode + " MULTIPLE CHOICES";
		case 301:
			return statusCode + " MOVED PERMANENTLY";
		case 302:
			return statusCode + " FOUND";
		case 303:
			return statusCode + " SEE OTHER";
		case 304:
			return statusCode + " NOT MODIFIED";
		case 305:
			return statusCode + " USE PROXY";
		case 307:
			return statusCode + " TEMPORARY REDIRECT";
		case 400:
			return statusCode + " BAD REQUEST";
		case 401:
			return statusCode + " UNAUTHORIZED";
		case 402:
			return statusCode + " PAYMENT REQUIRED";
		case 403:
			return statusCode + " FORBIDDEN";
		case 404:
			return statusCode + " NOT FOUND";
		case 405:
			return statusCode + " METHOD NOT ALLOWED";
		case 406:
			return statusCode + " NOT ACCEPTABLE";
		case 407:
			return statusCode + " PROXY AUTHENTICATION REQUIRED";
		case 408:
			return statusCode + " REQUEST TIMEOUT";
		case 409:
			return statusCode + " CONFLICT";
		case 410:
			return statusCode + " GONE";
		case 411:
			return statusCode + " LENGTH REQUIRED";
		case 412:
			return statusCode + " PRECONDITION FAILED";
		case 413:
			return statusCode + " REQUEST ENTITY TOO LARGE";
		case 414:
			return statusCode + " REQUEST URI TOO LONG";
		case 415:
			return statusCode + " UNSUPPORTED MEDIA TYPE";
		case 416:
			return statusCode + " REQUESTED RANGE NOT SATISFIABLE";
		case 417:
			return statusCode + " EXPECTATION FAILED";
		case 500:
			return statusCode + " INTERNAL SERVER ERROR";
		case 501:
			return statusCode + " NOT IMPLEMENTED";
		case 502:
			return statusCode + " BAD GATEWAY";
		case 503:
			return statusCode + " SERVICE UNAVAILABLE";
		case 504:
			return statusCode + " GATEWAY TIMEOUT";
		case 505:
			return statusCode + " HTTP VERSION NOT SUPPORTED";
		default:
			return statusCode + " UNKNOWN";
		}
	}

	/**
	 * Resolves and caches the {@link InetAddress IP address} for a host name.
	 * 
	 * @param hostname host name
	 * @return resolved {@link InetAddress}
	 */
	public static InetAddress getInetAddress(String hostname) {
		var addr = IP_CACHE.get(hostname);
		if (addr != null)
			return addr;

		addr = IuException.unchecked(() -> InetAddress.getByName(hostname));
		IP_CACHE.put(hostname, addr);

		return addr;
	}

	/**
	 * Determines whether or not an IP address is included in a CIDR range.
	 * 
	 * @param address address
	 * @param range   CIDR range
	 * @return true if the range includes the address; else false
	 */
	public static boolean isInetAddressInRange(InetAddress address, String range) {
		byte[] hostaddr = address.getAddress();
		int lastSlash = range.lastIndexOf('/');

		byte[] rangeaddr;
		int maskbits;
		if (lastSlash == -1) {
			rangeaddr = getInetAddress(range).getAddress();
			maskbits = rangeaddr.length * 8;
		} else {
			rangeaddr = getInetAddress(range.substring(0, lastSlash)).getAddress();
			maskbits = Integer.parseInt(range.substring(lastSlash + 1));
		}

		for (int i = 0; i < rangeaddr.length; i++) {
			if (maskbits >= 8) {
				maskbits -= 8;
				if (hostaddr[i] != rangeaddr[i])
					return false;
			} else if (maskbits > 0) {
				int mask = ~((1 << (8 - maskbits)) - 1);
				return (hostaddr[i] & mask) == (rangeaddr[i] & mask);
			} else
				break;
		}

		return true;
	}

	private IuWebUtils() {
	}

}
