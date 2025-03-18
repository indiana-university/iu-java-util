/*
 * Copyright © 2025 Indiana University
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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Function;

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
	 * Parses the cookie request header, returning an {@link HttpCookie} for each
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
	 * Parses the cookie request header, returning an {@link HttpCookie} for each
	 * cookie sent with the request.
	 * 
	 * @param forwardedHeaderValue {@code Forwarded:} header value. This value MUST
	 *                             match the syntax defined for {@code Forwarded} at
	 *                             <a href=
	 *                             "https://datatracker.ietf.org/doc/html/rfc7239#section-4">RFC-7238
	 *                             Forwarded HTTP Extension, Section 4</a>
	 * @return forwarded header value
	 */
	public static IuForwardedHeader parseForwardedHeader(String forwardedHeaderValue) {
		// Forwarded = 1#forwarded-element
		//
		// forwarded-element =
		// [ forwarded-pair ] *( ";" [ forwarded-pair ] )
		//
		// forwarded-pair = token "=" value
		// value = token / quoted-string
		//
		// token = <Defined in [RFC7230], Section 3.2.6>
		// quoted-string = <Defined in [RFC7230], Section 3.2.6>
		final var parsedHeader = new IuForwardedHeader() {
			String by;
			String xfor;
			String host;
			String proto;

			@Override
			public String getBy() {
				return by;
			}

			@Override
			public String getFor() {
				return xfor;
			}

			@Override
			public String getHost() {
				return host;
			}

			@Override
			public String getProto() {
				return proto;
			}
		};

		for (var pos = 0; pos < forwardedHeaderValue.length();) {
			final var endOfName = token(forwardedHeaderValue, pos);
			if (pos == endOfName)
				throw new IllegalArgumentException("expected token at " + pos);

			if (endOfName >= forwardedHeaderValue.length() //
					|| forwardedHeaderValue.charAt(endOfName) != '=')
				throw new IllegalArgumentException("expected '=' at " + endOfName);

			final var name = forwardedHeaderValue.substring(pos, endOfName);
			pos = endOfName + 1;

			final String value;
			if (pos >= forwardedHeaderValue.length())
				value = "";
			else {
				int endOfValue;
				if (forwardedHeaderValue.charAt(pos) == DQUOTE) {
					endOfValue = quotedString(forwardedHeaderValue, pos);
					if (endOfValue == pos)
						throw new IllegalArgumentException("unterminated '\"' at " + pos);
					else
						value = forwardedHeaderValue.substring(pos + 1, endOfValue - 1);
				} else {
					endOfValue = token(forwardedHeaderValue, pos);
					value = forwardedHeaderValue.substring(pos, endOfValue);
				}
				pos = endOfValue;
			}

			if (name.equalsIgnoreCase("by"))
				parsedHeader.by = IuObject.once(parsedHeader.by, value);
			else if (name.equalsIgnoreCase("for"))
				parsedHeader.xfor = IuObject.once(parsedHeader.xfor, value);
			else if (name.equalsIgnoreCase("host"))
				parsedHeader.host = IuObject.once(parsedHeader.host, value);
			else if (name.equalsIgnoreCase("proto"))
				parsedHeader.proto = IuObject.once(parsedHeader.proto, value);
			else
				throw new IllegalArgumentException("unexpected name " + name);

			if (pos < forwardedHeaderValue.length()) {
				if (forwardedHeaderValue.charAt(pos) == ';')
					pos++;
				else
					throw new IllegalArgumentException("expected ';' at " + pos);
			}
		}

		return parsedHeader;
	}

	/**
	 * Parses a decimal octet, as defined for an IPv4address.
	 * 
	 * @param nodeId      {@link #parseNodeIdentifier(String) node identifier}
	 * @param originalPos start position
	 * @return end position if matching; else returns start position
	 */
	static int decOctet(String nodeId, final int originalPos) {
		final var len = nodeId.length();
		if (originalPos >= len)
			return originalPos;

		var c1 = nodeId.charAt(originalPos);
		if (!digit(c1))
			return originalPos;

		var pos = originalPos + 1;
		char c2;
		if (pos >= len // dec-octet = DIGIT ; 0-9
				|| !digit(c2 = nodeId.charAt(pos)) //
				|| c1 == 0x30)
			return pos;

		char c3;
		if (++pos >= len // / %x31-39 DIGIT ; 10-99
				|| c1 > 0x32 //
				|| !digit(c3 = nodeId.charAt(pos)) // / "1" 2DIGIT ; 100-199
				|| (c1 == 0x32 // / "2" %x30-34 DIGIT ; 200-249
						&& c2 > 0x35 //
						|| (c2 == 0x35 // / "25" %x30-35 ; 250-255
								&& c3 > 0x35) //
				))
			return pos;
		else
			return pos + 1;
	}

	/**
	 * Parses an IPV4Address for {@link #parseNodeIdentifier(String)}.o
	 * 
	 * @param nodeId      {@link #parseNodeIdentifier(String) node identifier}
	 * @param originalPos start position
	 * @return end position if matching; else returns start position
	 */
	static int parseIPv4Address(String nodeId, final int originalPos) {
		final var len = nodeId.length();
		var pos = originalPos;
		for (var i = 0; i < 3; i++) {
			final var dot = decOctet(nodeId, pos);
			if (dot == pos //
					|| dot >= len //
					|| nodeId.charAt(dot) != '.')
				return originalPos;
			pos = dot + 1;
		}

		final var dot = decOctet(nodeId, pos);
		if (dot == pos)
			return originalPos;
		else
			return dot;
	}

	/**
	 * Determines if a character is a HEXDIG character.
	 * <p>
	 * HEXDIG = DIGIT / "A" / "B" / "C" / "D" / "E" / "F"
	 * </p>
	 * 
	 * @param c character
	 * @return true if the character is matches the HEXDIG ABNF rules from <a href=
	 *         "https://datatracker.ietf.org/doc/html/rfc5234#page-13">RFC-5234</a>;
	 *         else false
	 */
	static boolean hexdig(char c) {
		return digit(c) //
				|| (c >= 0x41 //
						&& c <= 0x46) //
				|| (c >= 0x61 //
						&& c <= 0x66) //
		;
	}

	/**
	 * Parses an h16 (16-bit hexadecimal number).
	 * 
	 * @param nodeId      {@link #parseNodeIdentifier(String) node identifier}
	 * @param originalPos start position
	 * @return end position if matching; else returns start position
	 */
	static int h16(String nodeId, final int originalPos) {
		final var len = nodeId.length();
		for (var i = 0; i < 4; i++) {
			final var pos = originalPos + i;
			if (pos >= len //
					|| !hexdig(nodeId.charAt(pos)))
				return pos;
		}
		return originalPos + 4;
	}

	/**
	 * Parses an ls32 (least significant 32-bits), either as an
	 * {@link #h16(String, int) h16 pair} or {@link #parseIPv4Address(String, int)}.
	 * 
	 * @param nodeId      {@link #parseNodeIdentifier(String) node identifier}
	 * @param originalPos start position
	 * @return end position if matching; else returns start position
	 */
	static int ls32(String nodeId, final int originalPos) {
		final var len = nodeId.length();
		final var h16a = h16(nodeId, originalPos);
		if (h16a == originalPos //
				|| h16a >= len)
			return originalPos;

		final var c = nodeId.charAt(h16a);
		if (c == ':') {
			final var pos = h16a + 1;
			final var h16b = h16(nodeId, pos);
			if (h16b == pos)
				return originalPos;
			else
				return h16b;
		} else if (c == '.')
			return parseIPv4Address(nodeId, originalPos);
		else
			return originalPos;
	}

	/**
	 * Parses an IPV6Address for {@link #parseNodeIdentifier(String)}.
	 * 
	 * @param nodeId      {@link #parseNodeIdentifier(String) node identifier}
	 * @param originalPos start position
	 * @return end position if matching; else returns start position
	 */
	static int parseIPv6Address(String nodeId, final int originalPos) {
		final var len = nodeId.length();

		var pos = h16(nodeId, originalPos);
		if (pos >= len)
			return originalPos;

		var rel = pos == originalPos;
		if (rel) // starts with relative marker '::'
			if (len <= originalPos + 1 //
					|| nodeId.charAt(originalPos) != ':' //
					|| nodeId.charAt(originalPos + 1) != ':')
				return originalPos;
			else
				pos = originalPos + 1;

		if (nodeId.charAt(pos) != ':')
			return originalPos;
		else
			pos++;

		// read up to five more ( h16 ":" ), non-terminal
		// or at least one non-leading relative marker
		for (var i = 0; i < 5; i++) {
			if (pos >= len)
				return originalPos;

			// / [ *5( h16 ":" ) h16 ] "::" h16
			final var next = h16(nodeId, pos);
			final char c;
			if (next >= len //
					|| (c = nodeId.charAt(next)) == ']')
				if (rel)
					return next;
				else
					return originalPos;

			if (next == pos //
					&& c == ':')
				if (rel) // may observe exactly one relative marker '::'
					return originalPos;
				else
					rel = true;
			else if (c != ':')
				return originalPos;

			pos = next + 1;

			if (i < 4 // must have all 8 segments or relative marker
					&& !rel) // IPv6address = 6( h16 ":" ) ls32
				continue;

			// / "::" 5( h16 ":" ) ls32
			// / [ h16 ] "::" 4( h16 ":" ) ls32
			// / [ *1( h16 ":" ) h16 ] "::" 3( h16 ":" ) ls32
			// / [ *2( h16 ":" ) h16 ] "::" 2( h16 ":" ) ls32
			// / [ *3( h16 ":" ) h16 ] "::" h16 ":" ls32
			// / [ *4( h16 ":" ) h16 ] "::" ls32
			final var ls32 = ls32(nodeId, pos);
			if (ls32 >= len //
					|| nodeId.charAt(ls32) == ']')
				return ls32;
		}

		return originalPos;
	}

	/**
	 * Parses an obfnode or obfport for {@link #parseNodeIdentifier(String)}.
	 * 
	 * @param nodeId      {@link #parseNodeIdentifier(String) node identifier}
	 * @param originalPos start position
	 * @return end position if matching; else returns start position
	 */
	static int obf(String nodeId, final int originalPos) {
		final var len = nodeId.length();

		char c;
		int pos;
		for (pos = originalPos; pos < len //
				&& (alpha(c = nodeId.charAt(pos)) //
						|| digit(c) //
						|| c == '.' //
						|| c == '_' //
						|| c == '-'); pos++)
			;

		if (pos - originalPos <= 1)
			return originalPos;
		else
			return pos;
	}

	/**
	 * Determines whether or not a nodename string, i.e., from an HTTP header,
	 * contains a valid IPv4 address.
	 * 
	 * @param nodename nodename string
	 * @return true if nodename is a string that contains a valid IPv4 address; else
	 *         false
	 * @see #parseNodeIdentifier(String)
	 */
	public static boolean isIPv4Address(String nodename) {
		return nodename != null //
				&& !nodename.isEmpty() //
				&& parseIPv4Address(nodename, 0) == nodename.length();
	}

	/**
	 * Determines whether or not a nodename string, i.e., from an HTTP header,
	 * contains a valid IPv6 address.
	 * 
	 * @param nodename nodename string
	 * @return true if nodename is a string that contains a valid IPv6 address; else
	 *         false
	 * @see #parseNodeIdentifier(String)
	 */
	public static boolean isIPv6Address(String nodename) {
		return nodename != null //
				&& !nodename.isEmpty() //
				&& parseIPv6Address(nodename, 0) == nodename.length();
	}

	/**
	 * Determines whether or not a nodename string, i.e., from an HTTP header,
	 * contains a valid IP address.
	 * 
	 * @param nodename nodename string
	 * @return true if nodename is a string that contains a valid IP address; else
	 *         false
	 * @see #parseNodeIdentifier(String)
	 */
	public static boolean isIPAddress(String nodename) {
		return isIPv4Address(nodename) || isIPv6Address(nodename);
	}

	/**
	 * Parses a node identifier.
	 * <p>
	 * The node identifier is defined by the ABNF syntax as:
	 * </p>
	 * 
	 * <pre>
	 * node = nodename [ ":" node-port ]
	 * nodename = IPv4address / "[" IPv6address "]" / "unknown" / obfnode
	 * 
	 * IPv4address = dec-octet "." dec-octet "." dec-octet "." dec-octet
	 * dec-octet   = DIGIT                 ; 0-9
	 *             / %x31-39 DIGIT         ; 10-99
	 *             / "1" 2DIGIT            ; 100-199
	 *             / "2" %x30-34 DIGIT     ; 200-249
	 *             / "25" %x30-35          ; 250-255
	 *
	 * IPv6address =                            6( h16 ":" ) ls32
	 *             /                       "::" 5( h16 ":" ) ls32
	 *             / [               h16 ] "::" 4( h16 ":" ) ls32
	 *             / [ *1( h16 ":" ) h16 ] "::" 3( h16 ":" ) ls32
	 *             / [ *2( h16 ":" ) h16 ] "::" 2( h16 ":" ) ls32
	 *             / [ *3( h16 ":" ) h16 ] "::"    h16 ":"   ls32
	 *             / [ *4( h16 ":" ) h16 ] "::"              ls32
	 *             / [ *5( h16 ":" ) h16 ] "::"              h16
	 *             / [ *6( h16 ":" ) h16 ] "::"
	 * ls32        = ( h16 ":" h16 ) / IPv4address
	 *             ; least-significant 32 bits of address
	 * h16         = 1*4HEXDIG
	 *             ; 16 bits of address represented in hexadecimal
	 *             
	 * obfnode = "_" 1*(ALPHA / DIGIT / "." / "_" / "-")
	 * 
	 * node-port = port / obfport
	 * port = 1*5DIGIT
	 * obfport = "_" 1*(ALPHA / DIGIT / "." / "_" / "-")
	 * </pre>
	 * 
	 * @param nodeId <a href=
	 *               "https://datatracker.ietf.org/doc/html/rfc7239#section-6">Node
	 *               identifier</a>
	 * @return {@link InetSocketAddress}
	 * @see #digit(char)
	 * @see #alpha(char)
	 */
	public static InetSocketAddress parseNodeIdentifier(String nodeId) {
		return parseNodeIdentifier(nodeId, a -> {
			throw new IllegalArgumentException("unknown obfnode");
		}, a -> {
			throw new IllegalArgumentException("unknown obfport");
		});
	}

	/**
	 * Implements {@link #parseNodeIdentifier(String nodeId)} with obfuscated node
	 * and port lookups.
	 * 
	 * @param nodeId               <a href=
	 *                             "https://datatracker.ietf.org/doc/html/rfc7239#section-6">Node
	 *                             identifier</a>
	 * @param obfuscatedNodeLookup Resovles an obfuscated node name (obfnode) to
	 *                             {@link InetAddress}
	 * @param obfuscatedPortLookup Resovles an obfuscated node port (obfport) number
	 * @return {@link InetSocketAddress}
	 * @see #digit(char)
	 * @see #alpha(char)
	 */
	public static InetSocketAddress parseNodeIdentifier(final String nodeId,
			Function<String, InetAddress> obfuscatedNodeLookup, Function<String, Integer> obfuscatedPortLookup) {
		if (nodeId == null || nodeId.isEmpty())
			return null;

		final var len = nodeId.length();
		final int endOfName;
		InetAddress obfnode = null;
		{
			var pos = parseIPv4Address(nodeId, 0);
			if (pos == 0) {
				var c = nodeId.charAt(0);
				if (c == '[') {
					pos = parseIPv6Address(nodeId, 1);
					if (pos == 1 //
							|| pos >= len)
						throw new IllegalArgumentException("invalid IPv6 address");
					else
						pos++;
				} else if (c == '_') {
					pos = obf(nodeId, 0);
					if (pos == 0)
						throw new IllegalArgumentException("invalid obfnode");
					else
						obfnode = obfuscatedNodeLookup.apply(nodeId.substring(0, pos));
				} else if (nodeId.startsWith("unknown"))
					return null;
				else
					throw new IllegalArgumentException("invalid nodename");
			}
			endOfName = pos;
		}

		final int port;
		if (endOfName >= len)
			port = 0;
		else if (nodeId.charAt(endOfName) == ':') {
			final var tail = nodeId.substring(endOfName + 1);
			if (tail.isEmpty())
				throw new IllegalArgumentException("empty node-port");
			else if (tail.charAt(0) == '_') {
				final var endOfObfport = obf(tail, 0);
				if (endOfObfport == 0)
					throw new IllegalArgumentException("invalid obfport");
				else
					port = obfuscatedPortLookup.apply(tail.substring(0, endOfObfport));
			} else {
				final var portlen = tail.length();
				if (portlen > 5)
					throw new IllegalArgumentException("invalid node-port");
				for (var i = 0; i < portlen; i++)
					if (!digit(tail.charAt(i)))
						throw new IllegalArgumentException("invalid port");
				port = Integer.parseInt(tail);
			}
		} else
			throw new IllegalArgumentException("expected ':' or end of node");

		return new InetSocketAddress(Objects.requireNonNullElseGet(obfnode, () -> {
			String name;
			if (nodeId.charAt(0) == '[') // trim brackets from IPv6
				name = nodeId.substring(1, endOfName - 1);
			else
				name = nodeId.substring(0, endOfName);
			return getInetAddress(name);
		}), port);
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
