/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu;

import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Provides useful utility methods for low-level web client and server
 * interactions.
 */
public final class IuWebUtils {

	private static final Map<String, InetAddress> IP_CACHE = new IuCacheMap<>(Duration.ofSeconds(5L));

	private static char DQUOTE = 0x22;
	private static char HTAB = 0x09;
	private static char SP = 0x20;

	/**
	 * VCHAR = %x21-7E
	 * 
	 * @param c character
	 * @return true if c matches VCHAR ABNF rule; else false
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc5234#appendix-B">RFC-5234 Core
	 *      ABNF</a>
	 */
	static boolean vchar(char c) {
		return c >= 0x21 //
				&& c <= 0x7e;
	}

	/**
	 * ALPHA = %x41-5A / %x61-7A
	 * 
	 * @param c character
	 * @return true if c matches ALHPA ABNF rule; else false
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc5234#appendix-B">RFC-5234 Core
	 *      ABNF</a>
	 */
	static boolean alpha(char c) {
		return (c >= 0x41 //
				&& c <= 0x5a) //
				|| (c >= 0x61 //
						&& c <= 0x7a);
	}

	/**
	 * DIGIT = %x30-39
	 * 
	 * @param c character
	 * @return true if c matches DIGIT ABNF rule; else false
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc5234#appendix-B">RFC-5234 Core
	 *      ABNF</a>
	 */
	static boolean digit(char c) {
		return c >= 0x30 //
				&& c <= 0x39;
	}

	/**
	 * ctext = HTAB / SP / %x21-27 / %x2A-5B / %x5D-7E / obs-text
	 * 
	 * @param c character
	 * @return true if c matches ctext ABNF rule; else false
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static boolean ctext(char c) {
		return c == HTAB //
				|| c == SP //
				|| (c >= 0x21 // '!'-'''
						&& c <= 0x27) //
				|| (c >= 0x2A // '*'-'['
						&& c <= 0x5b) //
				|| (c >= 0x5d // ']'-'~'
						&& c <= 0x7e) //
				|| obsText(c);
	}

	/**
	 * obs-text = %x80-FF
	 * 
	 * @param c character
	 * @return true if c matches obs-text ABNF rule; else false
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static boolean obsText(char c) {
		return c >= 0x80 //
				&& c <= 0xff;
	}

	/**
	 * comment = "(" *( ctext / quoted-pair / comment ) ")"
	 * 
	 * @param s   input string
	 * @param pos position at start of comment
	 * @return end position after matching comment ABNF rule; returns pos if token68
	 *         was not matched
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static int comment(String s, final int pos) {
		if (s.charAt(pos) != '(')
			return pos;

		var p = pos + 1;
		int n;
		char c;
		do {
			c = s.charAt(p);

			if (ctext(c))
				p++;
			else if ((n = quotedPair(s, p)) > p //
					|| (n = comment(s, p)) > p)
				p = n;
			else if (c == ')')
				return p + 1;
			else
				return pos;

		} while (p < s.length());

		return pos;
	}

	/**
	 * token68 = 1*( ALPHA / DIGIT / "-" / "." / "_" / "~" / "+" / "/" ) *"="
	 * 
	 * @param s   input string
	 * @param pos position at start of token68 character
	 * @return end position after matching token68 ABNF rule; returns pos if token68
	 *         was not matched
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static int token68(String s, int pos) {
		if (pos >= s.length())
			return pos;

		char c;
		do {
			if (pos == s.length())
				return pos;
			c = s.charAt(pos++);
		} while (alpha(c) //
				|| digit(c) //
				|| "-._~+/".indexOf(c) != -1);
		pos--;

		do {
			if (pos == s.length())
				return pos;
			c = s.charAt(pos++);
		} while (c == '=');

		return pos - 1;
	}

	/**
	 * tchar = "!" / "#" / "$" / "%" / "&amp;" / "'" / "*" / "+" / "-" / "." / "^" /
	 * "_" / "`" / "|" / "~" / DIGIT / ALPHA
	 * 
	 * @param c character
	 * @return true if c matches obs-text ABNF rule; else false
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static boolean tchar(char c) {
		return alpha(c) //
				|| digit(c) //
				|| "!#$%&'*+-.^_`!~".indexOf(c) != -1;
	}

	/**
	 * token = 1*tchar
	 * 
	 * @param s   input string
	 * @param pos position at start of token character
	 * @return end position of a matching token ABNF rule; returns pos if token was
	 *         not matched
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static int token(String s, int pos) {
		if (pos >= s.length())
			return pos;

		char c;
		do {
			c = s.charAt(pos++);
			if (pos == s.length() //
					&& tchar(c))
				return pos;
		} while (tchar(c));

		return pos - 1;
	}

	/**
	 * BWS = OWS
	 * 
	 * <p>
	 * OWS = *( SP / HTAB )
	 * </p>
	 *
	 * <p>
	 * RWS = 1*( SP / HTAB )
	 * </p>
	 * 
	 * @param s   input string
	 * @param pos position at start of token character
	 * @return end position of a matching BWS ABNF rule
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static int bws(String s, int pos) {
		if (pos >= s.length())
			return pos;

		char c;
		do {
			c = s.charAt(pos++);
			if (pos == s.length() //
					&& (c == SP //
							|| c == HTAB))
				return pos;
		} while (c == SP //
				|| c == HTAB);

		return pos - 1;
	}

	/**
	 * 1*SP
	 * 
	 * @param s   input string
	 * @param pos position at start of token character
	 * @return end position of a matching sp ABNF rule
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static int sp(String s, int pos) {
		if (pos >= s.length())
			return pos;

		char c;
		do {
			c = s.charAt(pos++);
			if (pos == s.length() //
					&& (c == SP))
				return pos;
		} while (c == SP);

		return pos - 1;
	}

	/**
	 * product = token [ "/" product-version ]
	 * <p>
	 * product-version = token
	 * </p>
	 * 
	 * @param s   input string
	 * @param pos position at start of product expression
	 * @return end position of a matching product ABNF rule; pos if not matched
	 */
	static int product(String s, int pos) {
		final var token = token(s, pos);
		if (token == pos //
				|| s.length() == token //
				|| s.charAt(token) != '/')
			return token;

		final var version = token(s, token + 1);
		if (version == token + 1)
			return pos;
		else
			return version;
	}

	/**
	 * qdtext = HTAB / SP / "!" / %x23-5B / %x5D-7E / obs-text
	 * 
	 * @param c character
	 * @return true if c matches qdtext ABNF rule; else false
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static boolean qdtext(char c) {
		return c == HTAB //
				|| c == SP //
				|| c == '!' //
				|| (c >= 0x23 //
						&& c <= 0x5b) //
				|| (c >= 0x5d //
						&& c <= 0x7e) //
				|| obsText(c);
	}

	/**
	 * quoted-pair = "\" ( HTAB / SP / VCHAR / obs-text )
	 * 
	 * @param s   input string
	 * @param pos position at start of token character
	 * @return end position of a matching quoted-pair ABNF rule; pos if the rule was
	 *         not matched
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static int quotedPair(String s, int pos) {
		char c;
		if (pos < s.length() - 1 //
				&& s.charAt(pos) == '\\' //
				&& ((c = s.charAt(pos + 1)) == HTAB //
						|| c == SP //
						|| vchar(c) //
						|| obsText(c)))
			pos += 2;
		return pos;
	}

	/**
	 * quoted-string = DQUOTE *( qdtext / quoted-pair ) DQUOTE
	 * 
	 * @param s     input string
	 * @param start position at start of token character
	 * @return end position of a matching BWS ABNF rule
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      HTTP Semantics Collected ABNF</a>
	 */
	static int quotedString(String s, int start) {
		if (start >= s.length() - 1 //
				|| s.charAt(start) != DQUOTE)
			return start;

		var pos = start + 1;
		do {
			final var qp = quotedPair(s, pos);
			if (qp != pos)
				pos = qp;

			if (pos >= s.length())
				return start;

		} while (qdtext(s.charAt(pos++)));

		if (s.charAt(pos - 1) != DQUOTE)
			return start;
		else
			return pos;
	}

	/**
	 * Determines whether or not a string is composed entirely of non-whitespace
	 * visible ASCII characters, and at least minLength characters, but no longer
	 * than 1024.
	 * 
	 * @param s         string to check
	 * @param minLength minimum length
	 * @return true if all characters are visible ASCII
	 */
	public static boolean isVisibleAscii(String s, int minLength) {
		final var len = s.length();
		if (len < minLength //
				|| len > 1024)
			return false;

		for (var i = 0; i < len; i++)
			if (!vchar(s.charAt(i)))
				return false;

		return true;
	}

	/**
	 * Determines if a root {@link URI} encompasses a resource {@link URI}.
	 * 
	 * @param rootUri     root {@link URI}
	 * @param resourceUri resource {@link URI}
	 * @return {@link URI}
	 */
	public static boolean isRootOf(URI rootUri, URI resourceUri) {
		if (rootUri.equals(resourceUri))
			return true;

		if (!resourceUri.isAbsolute() //
				|| resourceUri.isOpaque() //
				|| !IuObject.equals(rootUri.getScheme(), resourceUri.getScheme()) //
				|| !IuObject.equals(rootUri.getAuthority(), resourceUri.getAuthority()))
			return false;

		final var root = rootUri.getPath();
		if (root.isEmpty())
			return true;

		final var resource = resourceUri.getPath();
		final var l = root.length();
		return resource.startsWith(root) //
				&& (root.charAt(l - 1) == '/' //
						|| resource.charAt(l) == '/');
	}

	/**
	 * Creates an authentication challenge sending to a client via the
	 * <strong>WWW-Authenticate</strong> header.
	 * 
	 * @param scheme authentication scheme to request
	 * @param params challenge attributes for informing the client of how to
	 *               authenticate
	 * @return authentication challenge
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9110">RFC-9110 Section
	 *      11.1</a>
	 */
	public static String createChallenge(String scheme, Map<String, String> params) {
		if (!params.containsKey("realm"))
			throw new IllegalArgumentException("missing realm");

		final var sb = new StringBuilder();
		// auth-scheme = token
		if (token(scheme, 0) != scheme.length())
			throw new IllegalArgumentException("invalid auth scheme");
		sb.append(scheme);

		sb.append(' ');
		var first = true;
		for (final var paramEntry : params.entrySet()) {
			// auth-param = token BWS "=" BWS ( token / quoted-string )
			final var key = paramEntry.getKey();
			final var value = paramEntry.getValue();

			if (token(key, 0) == key.length()) {
				for (int i = 0; i < value.length(); i++) {
					final var c = value.charAt(i);
					if (c < SP //
							&& c != HTAB)
						throw new IllegalArgumentException("invalid parameter value");
				}
			} else if (token68(key, 0) == key.length()) {
				if (!value.isEmpty())
					throw new IllegalArgumentException("invalid encoded parameter");
			} else
				throw new IllegalArgumentException("invalid auth param");

			if (first)
				first = false;
			else
				sb.append(" ");

			sb.append(key);
			if (!value.isEmpty())
				sb.append("=\"") //
						.append(paramEntry.getValue() //
								.replace("\\", "\\\\") //
								.replace("\"", "\\\"")) //
						.append("\"");
		}
		return sb.toString();
	}

	/**
	 * Parses challenge parameters from a <strong>WWW-Authenticate</strong> header.
	 * 
	 * @param wwwAuthenticate WWW-Authenticate header challenge value
	 * @return Parsed authentication challenge parameters
	 */
	public static Iterator<IuWebAuthenticationChallenge> parseAuthenticateHeader(String wwwAuthenticate) {
		// WWW-Authenticate = [ challenge *( OWS "," OWS challenge ) ]
		return new Iterator<IuWebAuthenticationChallenge>() {
			private int pos = 0;

			@Override
			public boolean hasNext() {
				return pos < wwwAuthenticate.length();
			}

			@Override
			public IuWebAuthenticationChallenge next() {
				// challenge = auth-scheme [ 1*SP ( token68 / #auth-param ) ]
				// auth-scheme = token
				final var endOfAuthScheme = token(wwwAuthenticate, pos);
				if (endOfAuthScheme == pos)
					throw new IllegalArgumentException("invalid auth-scheme at " + pos);

				final var authScheme = wwwAuthenticate.substring(pos, endOfAuthScheme);
				pos = sp(wwwAuthenticate, endOfAuthScheme);

				final Map<String, String> params = new LinkedHashMap<>();
				while (hasNext() && wwwAuthenticate.charAt(pos) != ',') {
					// auth-param = token BWS "=" BWS ( token / quoted-string )
					var endOfToken = token(wwwAuthenticate, pos);
					var eqOrStartOfNextToken = bws(wwwAuthenticate, endOfToken);
					if (endOfToken > pos //
							&& endOfToken < wwwAuthenticate.length() //
							&& eqOrStartOfNextToken < wwwAuthenticate.length() //
							&& wwwAuthenticate.charAt(eqOrStartOfNextToken) == '=') {
						final var name = wwwAuthenticate.substring(pos, endOfToken);
						pos = bws(wwwAuthenticate, eqOrStartOfNextToken + 1);

						var endOfValue = quotedString(wwwAuthenticate, pos);
						if (endOfValue == pos) {
							endOfValue = token(wwwAuthenticate, pos);
							if (endOfValue == pos)
								throw new IllegalArgumentException("expected quoted-string at " + pos);
							else
								params.put(name, wwwAuthenticate.substring(pos, endOfValue));
						} else
							params.put(name, wwwAuthenticate.substring(pos + 1, endOfValue - 1) //
									.replace("\\\\", "\\") //
									.replace("\\\"", "\""));

						pos = sp(wwwAuthenticate, endOfValue);

					} else {
						endOfToken = token68(wwwAuthenticate, pos);
						if (endOfToken == pos)
							throw new IllegalArgumentException("invalid auth-param at " + pos);
						else if (endOfToken < wwwAuthenticate.length())
							throw new IllegalArgumentException("expected SP at " + pos);
						params.put(wwwAuthenticate.substring(pos, endOfToken), "");
						pos = sp(wwwAuthenticate, endOfToken);
					}

					final var bws = bws(wwwAuthenticate, pos);
					if (bws < wwwAuthenticate.length() //
							&& wwwAuthenticate.charAt(bws) == ',')
						pos = bws;
				}

				final var realm = params.remove("realm");
				if (hasNext())
					pos = bws(wwwAuthenticate, pos + 1);

				return new IuWebAuthenticationChallenge() {
					@Override
					public String getAuthScheme() {
						return authScheme;
					}

					@Override
					public String getRealm() {
						return realm;
					}

					@Override
					public Map<String, String> getParameters() {
						return params;
					}
				};
			}
		};
	}

	/**
	 * Validates a user-agent header value.
	 * 
	 * <p>
	 * User-Agent = product *( RWS ( product / comment ) )
	 * </p>
	 * 
	 * @param userAgent user-agent header value
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9110.html#name-collected-abnf">RFC-9110
	 *      Appendix A</a>
	 */
	public static void validateUserAgent(String userAgent) {
		var pos = product(userAgent, 0);
		if (pos <= 0)
			throw new IllegalArgumentException();

		while (pos < userAgent.length()) {
			var n = bws(userAgent, pos);
			if (n <= pos)
				throw new IllegalArgumentException();
			else
				pos++;

			if ((n = product(userAgent, pos)) <= pos //
					&& (n = comment(userAgent, pos)) <= pos)
				throw new IllegalArgumentException();
			else
				pos = n;
		}
	}

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
	 * cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E (US-ASCII
	 * characters excluding CTLs, whitespace, DQUOTE, comma, semicolon, and
	 * backslash)
	 * 
	 * @param c character
	 * @return true of the character is in the cookie-octet character set.
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6265#section-4.1">RFC-6265
	 *      HTTP State Management, Section 4.1</a>
	 */
	static boolean cookieOctet(char c) {
		return c == 0x21 // '!'
				|| (c >= 0x23 // '#'-'+'
						&& c <= 0x2b) //
				|| (c >= 0x2d // '-'-':'
						&& c <= 0x3a) //
				|| (c >= 0x3c // '<'-'['
						&& c <= 0x5b) //
				|| (c >= 0x5d // ']'-'~'
						&& c <= 0x7e);
	}

	/**
	 * cookie-value = *cookie-octet / ( DQUOTE *cookie-octet DQUOTE )
	 * <p>
	 * cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E (US-ASCII
	 * characters excluding CTLs, whitespace, DQUOTE, comma, semicolon, and
	 * backslash)
	 * </p>
	 * 
	 * @param cookieHeaderValue Cookie header value
	 * @param pos               position at start of the cookie-value token
	 * 
	 * @return end position of the cookie value
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6265#section-4.1">RFC-6265
	 *      HTTP State Management, Section 4.1</a>
	 */
	static int cookieValue(String cookieHeaderValue, int pos) {
		final var len = cookieHeaderValue.length();
		if (pos >= len)
			return pos;

		var c = cookieHeaderValue.charAt(pos);

		final boolean quoted = c == DQUOTE;
		if (quoted) {
			pos++;
			if (pos >= len)
				throw new IllegalArgumentException("unterminated '\"' at " + pos);
			c = cookieHeaderValue.charAt(pos);
		}

		while (pos < len //
				&& cookieOctet(c)) {
			pos++;
			if (pos < len)
				c = cookieHeaderValue.charAt(pos);
		}

		if (quoted)
			if (pos >= len //
					|| cookieHeaderValue.charAt(pos) != DQUOTE)
				throw new IllegalArgumentException("unterminated '\"' at " + pos);
			else
				return pos + 1;
		else
			return pos;
	}

	/**
	 * Parses the cookie request header, returning an {@link IuHttpCookie} for each
	 * cookie sent with the request.
	 * 
	 * @param cookieHeaderValue {@code Cookie:} header value. This value MUST match
	 *                          the syntax defined for {@code cookie-string}
	 *                          <a href=
	 *                          "https://datatracker.ietf.org/doc/html/rfc6265#section-4.2.1">RFC-6265
	 *                          HTTP State Management, Section 4.2.1</a>
	 * @return Iterable of parsed cookies
	 */
	public static Iterable<HttpCookie> parseCookieHeader(String cookieHeaderValue) {
		final var trimmedCookieHeaderValue = cookieHeaderValue.trim();
		return IuIterable.of(() -> new Iterator<HttpCookie>() {
			private int pos = 0;

			@Override
			public boolean hasNext() {
				return pos < trimmedCookieHeaderValue.length();
			}

			@Override
			public HttpCookie next() {
				// cookie-header = "Cookie:" OWS cookie-string OWS
				// cookie-string = cookie-pair *( ";" SP cookie-pair )

				// cookie-pair = cookie-name "=" cookie-value
				// cookie-name = token
				// cookie-value = *cookie-octet / ( DQUOTE *cookie-octet DQUOTE )
				// cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E

				final var endOfCookieName = token(trimmedCookieHeaderValue, pos);
				if (endOfCookieName == pos)
					throw new IllegalArgumentException("invalid cookie-name at " + pos);

				if (endOfCookieName >= trimmedCookieHeaderValue.length() //
						|| trimmedCookieHeaderValue.charAt(endOfCookieName) != '=')
					throw new IllegalArgumentException("expected '=' at " + endOfCookieName);

				final var cookieName = trimmedCookieHeaderValue.substring(pos, endOfCookieName);
				pos = endOfCookieName + 1;

				final String cookieValue;
				final var startOfCookieValue = pos;
				pos = cookieValue(trimmedCookieHeaderValue, startOfCookieValue);
				if (pos == startOfCookieValue)
					cookieValue = "";
				else if (trimmedCookieHeaderValue.charAt(pos - 1) == DQUOTE)
					cookieValue = trimmedCookieHeaderValue.substring(startOfCookieValue + 1, pos - 1);
				else
					cookieValue = trimmedCookieHeaderValue.substring(startOfCookieValue, pos);

				if (pos + 1 < trimmedCookieHeaderValue.length()) {
					if (trimmedCookieHeaderValue.charAt(pos++) != ';')
						throw new IllegalArgumentException("expected ';' at " + (pos - 1));
					if (trimmedCookieHeaderValue.charAt(pos++) != SP)
						throw new IllegalArgumentException("expected ' ' at " + (pos - 1));
				}

				return new HttpCookie(cookieName, cookieValue);
			}
		});
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
