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
package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
class SessionDetailTest {
	private Map<String, JsonValue> attributes;
	private Session session;
	private SessionDetailInterface sessionDetail;

	interface SessionDetailInterface {
		String getGivenName();

		void setGivenName(String givenName);

		boolean isNotThere();

		void unsupported();

		void unsupported(String value);

		void setUnsupported();

		void setUnsupported(String value, String value2);
	}

	@BeforeEach
	void setUp() {
		attributes = new HashMap<>();
		session = Mockito.mock(Session.class);
		sessionDetail = (SessionDetailInterface) Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(),
				new Class[] { SessionDetailInterface.class },
				new SessionDetail(attributes, session, IuJsonAdapter::of));
	}

	@Test
	void testInvokeWithHashCode() throws Throwable {
		assertEquals(System.identityHashCode(sessionDetail), sessionDetail.hashCode());
	}

	@Test
	void testInvokeWithEquals() throws Throwable {
		assertEquals(sessionDetail, sessionDetail);
		assertNotEquals(sessionDetail, new Object());
	}

	@Test
	void testInvokeWithToString() throws Throwable {
		assertEquals(attributes.toString(), sessionDetail.toString());
	}

	@Test
	void testInvokeWithIsMethod() throws Throwable {
		attributes.put("notThere", JsonValue.TRUE);
		assertTrue(sessionDetail.isNotThere());
	}

	@Test
	void testInvokeWithGetMethod() throws Throwable {
		attributes.put("givenName", IuJson.string("foo"));
		assertEquals("foo", sessionDetail.getGivenName());
	}

	@Test
	void testInvokeWithSetMethodExistingAttribute() throws Throwable {
		attributes.put("givenName", IuJson.string("foo"));
		sessionDetail.setGivenName("foo");
		assertEquals(IuJson.string("foo"), attributes.get("givenName"));
		verify(session, never()).setChanged(true);
	}

	@Test
	void testInvokeWithSetMethodNullNoChangeAttribute() throws Throwable {
		sessionDetail.setGivenName(null);
		assertFalse(attributes.containsKey("givenName"));
		verify(session, never()).setChanged(true);
	}

	@Test
	void testInvokeWithSetMethodForNonMatchAttributeValue() throws Throwable {
		attributes.put("givenName", IuJson.string("foo"));
		sessionDetail.setGivenName("bar");
		assertEquals(IuJson.string("bar"), attributes.get("givenName"));
		verify(session).setChanged(true);
	}

	@Test
	void testInvokeWithSetMethod() throws Throwable {
		sessionDetail.setGivenName("bar");
		assertEquals(IuJson.string("bar"), attributes.get("givenName"));
		verify(session).setChanged(true);
	}

	@Test
	void testInvokeWithSetMethodRemoveAttribute() throws Throwable {
		attributes.put("givenName", IuJson.string("foo"));
		sessionDetail.setGivenName(null);
		assertFalse(attributes.containsKey("givenName"));
		verify(session).setChanged(true);
	}

	@Test
	void testInvokeWithUnsupportedMethod() {
		assertThrows(UnsupportedOperationException.class, () -> sessionDetail.unsupported());
		assertThrows(UnsupportedOperationException.class, () -> sessionDetail.unsupported(null));
		assertThrows(UnsupportedOperationException.class, () -> sessionDetail.setUnsupported());
		assertThrows(UnsupportedOperationException.class, () -> sessionDetail.setUnsupported(null, null));
	}
}
