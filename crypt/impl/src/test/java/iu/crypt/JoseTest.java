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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import iu.crypt.Jose.Extension;

@SuppressWarnings("javadoc")
public class JoseTest extends CryptImplTestCase {

	private static class Builder extends JoseBuilder<Builder> {
		private Builder(Algorithm algorithm) {
			super(algorithm);
		}

		private Jose build() {
			return new Jose(toJson());
		}
	}

	private static Builder jose(Algorithm algorithm) {
		return new Builder(algorithm);
	}

	@Test
	public void testJson() {
		final var o = jose(Algorithm.HS256).build();
		assertEquals(o, CryptJsonAdapters.JOSE.fromJson(CryptJsonAdapters.JOSE.toJson(o)));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testRegister() {
		final var ext = mock(Extension.class);
		assertThrows(IllegalArgumentException.class, () -> Jose.register("enc", ext));
		final var id = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> Jose.getExtension(id));
		Jose.register(id, ext);
		assertSame(ext, Jose.getExtension(id));
		assertThrows(IllegalArgumentException.class, () -> Jose.register(id, ext));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExtensionDefaults() {
		final var ext = mock(Extension.class, CALLS_REAL_METHODS);
		assertDoesNotThrow(() -> ext.validate(null, null));
		assertDoesNotThrow(() -> ext.verify((WebCryptoHeader) null));
		assertDoesNotThrow(() -> ext.verify((WebSignature) null));
		assertDoesNotThrow(() -> ext.verify(null, null));
	}

	@Test
	public void testAlgorithmValidation() throws ClassNotFoundException {
		assertThrows(NullPointerException.class, () -> jose(Algorithm.A192KW).build());
		assertThrows(NullPointerException.class, () -> jose(Algorithm.ECDH_ES) //
				.param(Param.ENCRYPTION, Encryption.A128GCM) //
				.build());
		assertInstanceOf(Class.forName("java.security.interfaces.XECPublicKey"), jose(Algorithm.ECDH_ES) //
				.param(Param.ENCRYPTION, Encryption.A128GCM) //
				.param(Param.EPHEMERAL_PUBLIC_KEY, WebKey.ephemeral(Algorithm.ECDH_ES)) //
				.build().<WebKey>getExtendedParameter("epk").getPublicKey());
	}

	@Test
	public void testKeyId() {
		final var id = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> jose(Algorithm.HS256).crit("kid").build());
		assertEquals(id, jose(Algorithm.HS256).crit("kid").keyId(id).build().getKeyId());
	}

	@Test
	public void testFromHeaders() {
		final var prot = IuJson.object().add("enc", Encryption.A128GCM.enc).build();
		final var shared = IuJson.object().add("zip", "DEF").build();
		final var perRecip = IuJson.object().add("alg", Algorithm.HS256.alg).build();
		try (final var mockJose = mockConstruction(Jose.class, (mock, context) -> {
			assertEquals(IuJson.object().add("enc", Encryption.A128GCM.enc).add("zip", "DEF")
					.add("alg", Algorithm.HS256.alg).build(), context.arguments().get(0));
		})) {
			final var fromHeaders = Jose.from(prot, shared, perRecip);
			assertSame(mockJose.constructed().get(0), fromHeaders);
		}
	}

	@Test
	public void testFromPerRecipNoShared() {
		final var prot = IuJson.object().add("enc", Encryption.A128GCM.enc).build();
		final var perRecip = IuJson.object().add("zip", "DEF").add("alg", Algorithm.HS256.alg).build();
		try (final var mockJose = mockConstruction(Jose.class, (mock, context) -> {
			assertEquals(IuJson.object().add("enc", Encryption.A128GCM.enc).add("zip", "DEF")
					.add("alg", Algorithm.HS256.alg).build(), context.arguments().get(0));
		})) {
			final var fromHeaders = Jose.from(prot, null, perRecip);
			assertSame(mockJose.constructed().get(0), fromHeaders);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCriticalExtended() {
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> jose(Algorithm.HS256).crit(name));

		final var ext = mock(Extension.class);
		when(ext.toJson(value)).thenReturn(IuJson.string(value));
		when(ext.fromJson(IuJson.string(value))).thenReturn(value);
		Jose.register(name, ext);
		assertThrows(NullPointerException.class, () -> jose(Algorithm.HS256).crit(name).build());
		assertEquals(value, jose(Algorithm.HS256).crit(name).param(name, value).build().getExtendedParameter(name));
	}

	@Test
	public void testType() {
		final var type = IdGenerator.generateId();
		assertEquals(type,
				jose(Algorithm.DIRECT).param(Param.ENCRYPTION, Encryption.A192GCM).type(type).build().getType());
	}

	@Test
	public void testContentType() {
		final var contentType = IdGenerator.generateId();
		assertEquals(contentType, jose(Algorithm.HS512).contentType(contentType).build().getContentType());
	}

	@Test
	public void testToString() {
		final var jose = jose(Algorithm.HS512).wellKnown(WebKey.ephemeral(Algorithm.HS512)).build();
		final var fromJose = jose.toString();
		assertNull(jose.getKey().getKey());
		assertNull(new Jose(IuJson.parse(fromJose).asJsonObject()).getKey().getKey());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testToJson() {
		final var extName = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var ext = mock(Extension.class);
		when(ext.toJson(value)).thenReturn(IuJson.string(value));
		when(ext.fromJson(IuJson.string(value))).thenReturn(value);
		Jose.register(extName, ext);

		final var key = WebKey.ephemeral(Algorithm.ES512);
		final var jose = jose(Algorithm.ES512).wellKnown(key).param(extName, value).build();
		final var fromJose = jose.toString();
		assertNull(jose.getKey().getPrivateKey()); // JOSE never holds secret/private keys
		assertNull(new Jose(IuJson.parse(fromJose).asJsonObject()).getKey().getKey());
	}

	@Test
	public void testJustProtected() {
		final var j = IuJson.object().add("alg", Algorithm.ES384.alg).build();
		assertEquals(new Jose(j), Jose.from(j, null, null));
		assertNull(new Jose(j).toJson(a -> false));
	}

	@Test
	public void testWellKnown() {
		final var key = WebKey.ephemeral(Algorithm.ES512);
		final var jose = jose(Algorithm.ES512).wellKnown(key).build();
		assertEquals(key.wellKnown(), jose.wellKnown());
	}


	@Test
	public void testECDHParams() {
		final var epk = WebKey.ephemeral(Algorithm.ECDH_ES).wellKnown();
		final var jose = jose(Algorithm.ECDH_ES).param(Param.ENCRYPTION, Encryption.A128GCM)
				.param(Param.EPHEMERAL_PUBLIC_KEY, epk).build();
		assertEquals(Encryption.A128GCM.enc, jose.extendedParameters().getString("enc"));
		assertEquals(epk, new Jwk(jose.extendedParameters().getJsonObject("epk")));
	}
}
