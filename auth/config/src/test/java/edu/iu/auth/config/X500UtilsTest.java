/*
 * Copyright © 2026 Indiana University
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
package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class X500UtilsTest {

	@Test
	public void testRFC4514_4_A() {
		final var x500 = new X500Principal("UID=jsmith,DC=example,DC=net");
		final var parsed = X500Utils.parse(x500.getName());
		final var i = parsed.iterator();
		assertEquals(Map.of("UID", "jsmith"), i.next());
		assertEquals(Map.of("DC", "example"), i.next());
		assertEquals(Map.of("DC", "net"), i.next());
		assertFalse(i.hasNext());
		assertEquals("jsmith@example.net", X500Utils.getCommonName(x500));
	}

	@Test
	public void testRFC4514_4_B() {
		final var x500 = new X500Principal("OU=Sales+CN=J.  Smith,DC=example,DC=net");
		final var parsed = X500Utils.parse(x500.getName());
		final var i = parsed.iterator();
		assertEquals(Map.of("OU", "Sales", "CN", "J.  Smith"), i.next());
		assertEquals(Map.of("DC", "example"), i.next());
		assertEquals(Map.of("DC", "net"), i.next());
		assertFalse(i.hasNext());
		assertEquals("J.  Smith@example.net", X500Utils.getCommonName(x500));
	}

	@Test
	public void testRFC4514_4_C() {
		final var x500 = new X500Principal("CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net");
		final var parsed = X500Utils.parse(x500.getName());
		final var i = parsed.iterator();
		assertEquals(Map.of("CN", "James \"Jim\" Smith, III"), i.next());
		assertEquals(Map.of("DC", "example"), i.next());
		assertEquals(Map.of("DC", "net"), i.next());
		assertFalse(i.hasNext());
		assertEquals("James \"Jim\" Smith, III@example.net", X500Utils.getCommonName(x500));
	}

	@Test
	public void testRFC4514_4_D() {
		final var x500 = new X500Principal("CN=Before\\0dAfter,DC=example,DC=net");
		final var parsed = X500Utils.parse(x500.getName());
		final var i = parsed.iterator();
		assertEquals(Map.of("CN", "Before\rAfter"), i.next());
		assertEquals(Map.of("DC", "example"), i.next());
		assertEquals(Map.of("DC", "net"), i.next());
		assertFalse(i.hasNext());
		assertEquals("Before\rAfter@example.net", X500Utils.getCommonName(x500));
	}

	@Test
	public void testRFC4514_4_E() throws MalformedURLException, IOException {
		final var parsed = X500Utils.parse("1.3.6.1.4.1.1466.0=#04024869");
		final var i = parsed.iterator();
		assertEquals(Map.of("1.3.6.1.4.1.1466.0", "data:;base64,BAJIaQ=="), i.next());
		assertFalse(i.hasNext());
		assertNull(X500Utils.getCommonName(new X500Principal("1.3.6.1.4.1.1466.0=#04024869")));
	}

	@Test
	public void testRFC4514_4_F() throws MalformedURLException, IOException {
		final var parsed = X500Utils.parse("CN=Lu\\C4\\8Di\\C4\\87");
		final var i = parsed.iterator();
		assertEquals(Map.of("CN", "Lučić"), i.next());
		assertFalse(i.hasNext());
		assertEquals("Lučić", X500Utils.getCommonName(new X500Principal("CN=Lu\\C4\\8Di\\C4\\87")));
	}

	@Test
	public void testCanonicalUid() {
		assertEquals("FOO", X500Utils.getCommonName(new X500Principal("0.9.2342.19200300.100.1.1=FOO")));
	}

	@Test
	public void testEmpty() {
		assertNull(X500Utils.getCommonName(new X500Principal("")));
	}

	@Test
	public void testHyphenInKey() {
		assertEquals("bar", X500Utils.parse("f-o-o=bar").iterator().next().get("f-o-o"));
		assertEquals("bar", X500Utils.parse("f-0-o=bar").iterator().next().get("f-0-o"));
		assertEquals("bar", X500Utils.parse("f-0-o=bar\0").iterator().next().get("f-0-o"));
		assertEquals("#bar", X500Utils.parse("f-0-o=\\#bar").iterator().next().get("f-0-o"));
	}

	@Test
	public void testInvalidType() {
		assertEquals("expected ALPHA or DIGIT at 1",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse(".1.2.3=foo")).getMessage());
		assertEquals("expected DIGIT at 3",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("1.A.2.3=foo")).getMessage());
		assertEquals("unexpected at 2",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("0123.1.2.3=foo"))
						.getMessage());
		assertEquals("expected EQUALS at 4",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("foo bar")).getMessage());
		assertEquals("expected PLUS or COMMA at 8",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("foo=bar;")).getMessage());
		assertEquals("expected EQUALS at 2",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("f~oo=bar")).getMessage());
	}

	@Test
	public void testEscapes() {
		assertEquals("foo\\\"bar\\", X500Utils.getCommonName(new X500Principal("cn=foo\\\\\\\"bar\\\\")));
		assertEquals("foo\\\"bar\\", X500Utils.getCommonName(new X500Principal("cn=foo\\\\\\\"bar\\\\")));
		assertEquals("expected HEX at 5",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("cn=\\a")).getMessage());
		assertEquals("unexpected at 5",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("cn=\\g")).getMessage());
		assertEquals("unexpected SP at 7",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("cn=foo ")).getMessage());
		assertEquals("expected PLUS or COMMA at 7",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("cn=foo\0\0")).getMessage());
	}

	@Test
	public void testHexString() {
		assertEquals("foobar", X500Utils.parse("cn=#1606666f6f626172").iterator().next().get("cn"));
		assertEquals("data:;base64,Fg==", X500Utils.parse("cn=#16").iterator().next().get("cn"));
		assertEquals("data:;base64,Fv7t", X500Utils.parse("cn=#16feed").iterator().next().get("cn"));
		assertEquals("expected HEX at 5",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("cn=#x")).getMessage());
		assertEquals("expected HEX at 6",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("cn=#ax")).getMessage());
		assertEquals("expected <stringchar> or SHARP at 4",
				assertThrows(IllegalArgumentException.class, () -> X500Utils.parse("cn= #ax")).getMessage());
	}

}
