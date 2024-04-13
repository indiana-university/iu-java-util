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
package iu.auth.jwt;

import java.io.ByteArrayOutputStream;
import java.nio.CharBuffer;
import java.security.Principal;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.x500.X500Principal;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;

/**
 * Provides basic inspection utilities supporting X500 principal use.
 */
public class PrincipalUtils {

	/**
	 * Maps required X500 OID values to standard short names
	 */
	public static Map<String, String> X500_OIDMAP = Map.of( //
			"2.5.4.3", "CN", //
			"2.5.4.7", "L", //
			"2.5.4.8", "ST", //
			"2.5.4.10", "O", //
			"2.5.4.11", "OU", //
			"2.5.4.6", "C", //
			"2.5.4.9", "STREET", //
			"0.9.2342.19200300.100.1.25", "DC", //
			"0.9.2342.19200300.100.1.1", "UID");

	/**
	 * Determines the common name of a principal.
	 * 
	 * @param principal principal
	 * @return parsed CN value from {@link X500Prinipcal}; else
	 *         {@link Principal#getName()}
	 */
	public static String getCommonName(Principal principal) {
		if (principal instanceof X500Principal) {
			final var x500 = (X500Principal) principal;
			final var name = x500.getName(X500Principal.CANONICAL);

			String cn = null;
			final var nameBuilder = new StringBuilder();
			for (final var rdn : parse(name)) {
				cn = IuObject.first(cn, rdn.get("cn"), rdn.get("uid"));
				final var dc = rdn.get("dc");
				if (dc != null) {
					if (nameBuilder.length() == 0)
						nameBuilder.append('@');
					else
						nameBuilder.append('.');
					nameBuilder.append(dc);
				}
			}
			IuObject.convert(cn, a -> nameBuilder.insert(0, a));
			return nameBuilder.length() == 0 ? null : nameBuilder.toString();
		} else
			return principal.getName();
	}

	/**
	 * Parses an X.500 Distinguished Name (DN)
	 * 
	 * <p>
	 * The response is an iterable of Relative Distiguished Names mappings as
	 * defined by <a href="https://datatracker.ietf.org/doc/html/rfc4514">RFC-4514
	 * LDAP</a>.
	 * </p>
	 * 
	 * @param name serialized X.500 DN
	 * @return parsed DN
	 */
	public static Iterable<Map<String, String>> parse(String name) {
		if (name.isEmpty())
			return IuIterable.empty();

		final Queue<Map<String, String>> parsedDN = new ArrayDeque<>();
		Map<String, String> rdn = new LinkedHashMap<>();
		parsedDN.offer(rdn);

		var buf = CharBuffer.wrap(name);
		final Supplier<Character> next = () -> buf.hasRemaining() ? buf.get() : '\0';
		while (buf.hasRemaining()) {
			// type = keystring / numericoid
			final var startOfAttributeType = buf.position();
			var c = next.get();
			if (digit(c))
				do { // numericoid = number 1*( DOT number )
					if (c == '.') {
						c = next.get();
						if (!digit(c))
							throw new IllegalArgumentException("expected DIGIT at " + buf.position());
					}

					// number = DIGIT / ( LDIGIT 1*DIGIT )
					final var startOfNumber = buf.position();
					final var ldigit = ldigit(c);
					do {
						if (buf.position() > startOfNumber && !ldigit)
							throw new IllegalArgumentException("unexpected at " + buf.position());

						c = next.get();
					} while (digit(c));

				} while (c == '.');
			else if (alpha(c))
				do // keystring = ALPHA *keychar
					c = next.get();
				while (keychar(c));
			else
				throw new IllegalArgumentException("expected ALPHA or DIGIT at " + buf.position());

			// typedValue = type EQUALS value
			final var attributeType = name.substring(startOfAttributeType, buf.position() - 1);
			if (c != '=')
				throw new IllegalArgumentException("expected EQUALS at " + buf.position());

			// value = string / hexstring
			final String value;
			c = next.get();
			// string = [ ( leadchar / pair )
			// [ *( stringchar / pair ) ( trailchar / pair ) ] ]
			if (leadchar(c) //
					|| c == '\\') {
				final var valueBuilder = new StringBuilder();
				final var pendingBytes = new ByteArrayOutputStream();
				final Consumer<Object> append = a -> {
					if (pendingBytes.size() > 0) {
						valueBuilder.append(IuText.utf8(pendingBytes.toByteArray()));
						pendingBytes.reset();
					}
					if (a != null)
						valueBuilder.append(a);
				};

				do {
					if (c == '\\') {
						// pair = ESC ( ESC / special / hexpair )
						c = next.get();
						if (c == '\\' || special(c)) {
							// replace <ESC><ESC> with <ESC>;
							// replace <ESC><special> with <special>;
							append.accept(c);
							c = next.get();
						} else if (hexchar(c)) {
							// hexpair = HEX HEX
							var hexval = Integer.parseInt(Character.toString(c), 0x10) * 0x10;
							c = next.get();
							if (!hexchar(c))
								throw new IllegalArgumentException("expected HEX at " + buf.position());
							else
								hexval += Integer.parseInt(Character.toString(c), 0x10);
							pendingBytes.write(hexval);

							c = next.get();
						} else
							throw new IllegalArgumentException("unexpected at " + buf.position());
					} else {
						final var l = c;
						c = next.get();
						if (!stringchar(c) //
								&& c != '\\' //
								&& !trailchar(l))
							throw new IllegalArgumentException("unexpected SP at " + buf.position());
						append.accept(l);
					}
				} while (stringchar(c) || c == '\\');

				append.accept(null);
				value = valueBuilder.toString();

			} else if (c == '#') {
				// hexstring = SHARP 1*hexpair
				c = next.get();
				if (!hexchar(c))
					throw new IllegalArgumentException("expected HEX at " + buf.position());

				// If in <hexstring> form, a BER representation can be obtained from
				// converting each <hexpair> of the <hexstring> to the octet indicated
				// by the <hexpair>
				ByteArrayOutputStream hexString = new ByteArrayOutputStream();
				do {
					var hexval = Integer.parseInt(Character.toString(c), 0x10) * 0x10;
					c = next.get();
					if (!hexchar(c))
						throw new IllegalArgumentException("expected HEX at " + buf.position());
					hexval += Integer.parseInt(Character.toString(c), 0x10);
					hexString.write(hexval);

					c = next.get();
				} while (hexchar(c));

				final var ber = hexString.toByteArray();
				if (ber.length <= 2 //
						|| ber[0] != 0x16 //
						|| ber[1] <= 0)
					value = "data:;base64," + IuText.base64(ber);
				else
					// IA5String: BER-encoded type 0x16 ASCII string
					value = IuText.ascii(Arrays.copyOfRange(ber, 2, ber[1] + 2));

			} else
				throw new IllegalArgumentException("expected <stringchar> or SHARP at " + buf.position());

			rdn.put(attributeType, value);

			if (c == ',') {
				// dn = [ rdn *( COMMA rdn ) ]
				rdn = new LinkedHashMap<>();
				parsedDN.offer(rdn);
			} else if (c != '+' //
					&& (c != '\0' //
							|| buf.hasRemaining()))
				throw new IllegalArgumentException("expected PLUS or COMMA at " + buf.position());
			// rdn = typedValue *( PLUS typedValue )
		}

		return parsedDN;
	}

	private static boolean alpha(char c) {
		// ALPHA = %x41-5A / %x61-7A ; "A"-"Z" / "a"-"z"
		// ALPHA = <any ASCII alphabetic character>
		// ; (decimal 65-90 and 97-122)
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c < 'z');
	}

	private static boolean digit(char c) {
		// DIGIT = %x30 / LDIGIT ; "0"-"9"
		// DIGIT = <any ASCII decimal digit>
		// ; (decimal 48-57)
		return c == '0' || ldigit(c);
	}

	private static boolean ldigit(char c) {
		// LDIGIT = %x31-39 ; "1"-"9"
		return c >= '1' && c <= '9';
	}

	private static boolean hexchar(char c) {
		// HEX = DIGIT / %x41-46 / %x61-66 ; "0"-"9" / "A"-"F" / "a"-"f"
		// hexchar = DIGIT / "A" / "B" / "C" / "D" / "E" / "F"
		// / "a" / "b" / "c" / "d" / "e" / "f"
		return digit(c) //
				|| (c >= 'A' && c <= 'F') //
				|| (c >= 'a' && c <= 'f');
	}

	private static boolean keychar(char c) {
		// keychar = ALPHA / DIGIT / HYPHEN
		return alpha(c) || digit(c) || c == '-';
	}

	private static boolean leadchar(char c) {
		// leadchar = LUTF1 / UTFMB
		// LUTF1 = %x01-1F / %x21 / %x24-2A / %x2D-3A /
		// %x3D / %x3F-5B / %x5D-7F
		return trailchar(c) && c != '#';
	}

	private static boolean trailchar(char c) {
		// trailchar = TUTF1 / UTFMB
		// TUTF1 = %x01-1F / %x21 / %x23-2A / %x2D-3A /
		// %x3D / %x3F-5B / %x5D-7F
		return stringchar(c) && c != ' ';
	}

	private static boolean stringchar(char c) {
		// stringchar = SUTF1 / UTFMB
		// SUTF1 = %x01-21 / %x23-2A / %x2D-3A /
		// %x3D / %x3F-5B / %x5D-7F
		return !escaped(c) && c != '\0' && c != '\\';
	}

	private static boolean escaped(char c) {
		// escaped = DQUOTE / PLUS / COMMA / SEMI / LANGLE / RANGLE
		return "\"+,;<>".indexOf(c) != -1;
	}

	private static boolean special(char c) {
		// special = escaped / SPACE / SHARP / EQUALS
		return escaped(c) //
				|| " #=".indexOf(c) != -1;
	}

	private PrincipalUtils() {
	}
}
