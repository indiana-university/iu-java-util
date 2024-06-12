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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.JsonObject;

@SuppressWarnings({ "javadoc" })
public class JsonProxyTest {

	class Opaque {
	}

	interface JsonBackedInterface {
		String getFoo();

		boolean isNotThere();

		void unsupported();

		Opaque getOpaque();

		default String getBar() {
			return "baz";
		}

		default String addToBar(String s) {
			return s + getBar();
		}
	}

	interface JsonBackedInterfaceReference {
		JsonBackedInterface getData();
	}

	@Test
	public void testCanWrap() {
		assertTrue(JsonProxy.canWrap(IuJson.object().build(), JsonBackedInterfaceReference.class));
		assertFalse(JsonProxy.canWrap(IuJson.object().build(), Object.class));
		assertFalse(JsonProxy.canWrap(IuJson.object().build(), JsonObject.class));
	}

	@Test
	public void testProxyMethods() {
		final var foobar = IuJson.object().add("foo", "bar").build();
		final var data = IuJson.wrap(foobar, JsonBackedInterface.class);
		final var data2 = IuJson.wrap(IuJson.object().add("foo", "baz").build(), JsonBackedInterface.class);
		final var data3 = IuJson.wrap(IuJson.object().add("foo", "baz").build(), JsonBackedInterface.class);
		assertEquals("bar", data.getFoo());
		assertEquals(foobar.toString(), data.toString());
		assertEquals(foobar.hashCode(), data.hashCode());
		assertNotEquals(data, data2);
		assertNotEquals(data, null);
		assertNotEquals(data, new Object());
		assertNotEquals(data, Proxy.newProxyInstance(JsonBackedInterface.class.getClassLoader(),
				new Class<?>[] { JsonBackedInterface.class }, (proxy, method, args) -> {
					fail();
					return null;
				}));
		assertEquals(data3, data2);
		assertFalse(data.isNotThere());
		assertThrows(UnsupportedOperationException.class, data::unsupported);

		assertEquals(data, IuJson
				.wrap(IuJson.object().add("data", foobar).build(), JsonBackedInterfaceReference.class).getData());
	}

//	@Test
//	public void testWebKey() {
//		final var key = WebKey.ephemeral(Encryption.A128GCM);
//		assertEquals(key, JsonProxy
//				.wrap(IuJson.object().add("key", IuJson.parse(key.toString())).build(), JsonBackedInterface.class)
//				.getKey());
//	}
//
//	@Test
//	public void testRealm() {
//		final var realm = mock(Realm.class);
//		final var authId = IdGenerator.generateId();
//		try (final var mockRealm = mockStatic(Realm.class)) {
//			mockRealm.when(() -> Realm.of(authId)).thenReturn(realm);
//			assertSame(realm, JsonProxy.authConfigurationTransform(IuJson.string(authId), Realm.class).get());
//		}
//	}
//
//	@Test
//	public void testAudience() {
//		final var audience = mock(Audience.class);
//		final var authId = IdGenerator.generateId();
//		try (final var mockAudience = mockStatic(Audience.class)) {
//			mockAudience.when(() -> Audience.of(authId)).thenReturn(audience);
//			assertSame(audience, JsonProxy.authConfigurationTransform(IuJson.string(authId), Audience.class).get());
//		}
//	}
//
//	@Test
//	public void testAlgorithm() {
//		final var alg = IuTest.rand(Algorithm.class);
//		assertEquals(alg, JsonProxy.authConfigurationTransform(IuJson.string(alg.alg), Algorithm.class).get());
//	}
//
//	@Test
//	public void testEncryption() {
//		final var enc = IuTest.rand(Encryption.class);
//		assertEquals(enc, JsonProxy.authConfigurationTransform(IuJson.string(enc.enc), Encryption.class).get());
//	}
//
//	@Test
//	public void testGrantType() {
//		final var grantType = IuTest.rand(GrantType.class);
//		assertEquals(grantType,
//				JsonProxy.authConfigurationTransform(IuJson.string(grantType.parameterValue), GrantType.class).get());
//	}
//
//	@Test
//	public void testAuthMethod() {
//		final var authMethod = IuTest.rand(AuthMethod.class);
//		assertEquals(authMethod,
//				JsonProxy.authConfigurationTransform(IuJson.string(authMethod.parameterValue), AuthMethod.class).get());
//	}
//
//	@Test
//	public void testCredentialsAndGrantTypes() {
//		final var clientId = IdGenerator.generateId();
//		final var scope = IdGenerator.generateId();
//		final var clientConfig = IuJson.object() //
//				.add("scope", IuJson.array().add(scope)) //
//				.add("credentials", IuJson.array().add(IuJson.object() //
//						.add("grant_types", IuJson.array().add(IuJson.string("client_credentials"))) //
//				)) //
//				.build();
//		final var secretData = IuJson.object() //
//				.add("data", IuJson.object().add("data", IuJson.object() //
//						.add("client/" + clientId, clientConfig.toString())))
//				.build();
//
//		final var rb = mock(HttpRequest.Builder.class);
//
//		try (final var mockHttp = mockStatic(IuHttp.class)) {
//			mockHttp.when(() -> IuHttp.send(eq(URI.create(VAULT_ENDPOINT + "/data/" + VAULT_SECRET)), argThat(a -> {
//				assertDoesNotThrow(() -> a.accept(rb));
//				return true;
//			}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(secretData);
//
//			final var client = Client.of(clientId);
//			final var credentials = assertInstanceOf(Credentials.class, client.getCredentials().iterator().next());
//			assertEquals(GrantType.CLIENT_CREDENTALS, credentials.getGrantTypes().iterator().next());
//			assertEquals(scope, client.getScope().iterator().next());
//		}
//	}
//
//	@Test
//	public void testUnsupportedParamReturnType() {
//		final var value = mock(JsonValue.class);
//		final var paramType = mock(ParameterizedType.class);
//		when(paramType.getRawType()).thenReturn(Stream.class);
//		when(paramType.getActualTypeArguments()).thenReturn(new Class<?>[] { String.class });
//		assertTrue(JsonProxy.authConfigurationTransform(value, paramType).isEmpty());
//	}
//
	@Test
	public void testDefault() {
		final var data = IuJson.wrap(IuJson.object().build(), JsonBackedInterface.class);
		assertEquals("baz", data.getBar());
		assertEquals("shabaz", data.addToBar("sha"));
		final var data2 = IuJson.wrap(IuJson.object().add("bar", "foo").build(), JsonBackedInterface.class);
		assertEquals("foo", data2.getBar());
		assertEquals("snafoo", data2.addToBar("sna"));
	}

	@Test
	public void testOpaque() {
		final var data = IuJson.wrap(IuJson.object().build(), JsonBackedInterface.class);
		assertNull(data.getOpaque());
		final var data2 = IuJson.wrap(IuJson.object().add("opaque", "foo").build(), JsonBackedInterface.class);
		assertThrows(UnsupportedOperationException.class, data2::getOpaque);
	}

}
