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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.crypt.IuCryptTestCase;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebCryptoHeader.Extension;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class JoseTest extends IuCryptTestCase {

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

	@SuppressWarnings("unchecked")
	@Test
	public void testRegister() {
		final var ext = mock(Extension.class);
		assertThrows(IllegalArgumentException.class, () -> WebCryptoHeader.register("enc", ext));
		final var id = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> Jose.getExtension(id));
		WebCryptoHeader.register(id, ext);
		assertSame(ext, Jose.getExtension(id));
		assertThrows(IllegalArgumentException.class, () -> WebCryptoHeader.register(id, ext));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testWellKnown() {
		final var id = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.ES256).keyId(id).ephemeral().build();
		assertEquals(jwk.wellKnown(), jose(Algorithm.ES256).keyId(id)
				.wellKnown(uri(WebKey.asJwks(IuIterable.iter(jwk.wellKnown())))).build().wellKnown());
		assertEquals(jwk.wellKnown(), jose(Algorithm.ES256).keyId(id).key(jwk).build().wellKnown());
		assertEquals(CERT.getPublicKey(), jose(Algorithm.RS256).cert(CERT).build().wellKnown().getPublicKey());
	}

	@Test
	public void testAlgorithmValidation() throws ClassNotFoundException {
		assertThrows(NullPointerException.class, () -> jose(Algorithm.A192KW).build());
		assertThrows(IllegalArgumentException.class, () -> jose(Algorithm.ECDH_ES) //
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
		assertThrows(IllegalArgumentException.class, () -> jose(Algorithm.HS256).crit("kid").build());
		assertEquals(id, jose(Algorithm.HS256).crit("kid").keyId(id).build().getKeyId());
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
		assertThrows(IllegalArgumentException.class, () -> jose(Algorithm.HS256).crit(name).build());
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
		final var jose = jose(Algorithm.HS512).key(WebKey.ephemeral(Algorithm.HS512)).build();
		final var fromJose = jose.toString();
		assertNotNull(jose.getKey().getKey());
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
		final var jose = jose(Algorithm.ES512).key(key).param(extName, value).build();
		final var fromJose = jose.toString();
		assertNotNull(jose.getKey().getPrivateKey());
		assertNull(new Jose(IuJson.parse(fromJose).asJsonObject()).getKey().getKey());
	}

//	@SuppressWarnings("unchecked")
//	@Test
//	public void testUnderstood() {
//		final var id = IdGenerator.generateId();
//		final var ext = mock(Extension.class);
//		JoseBuilder.register(id, ext);
//		assertFalse(Jose.isUnderstood("alg"));
//		assertTrue(Jose.isUnderstood("p2s"));
//		assertFalse(Jose.isUnderstood("foo"));
//		assertTrue(Jose.isUnderstood(id));
//	}
//
//
//	@Test
//	public void testJwkUri() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.ES256);
//		assertThrows(NullPointerException.class, () -> builder.wellKnown(null));
//
//		final var id = IdGenerator.generateId();
//		builder.keyId(id);
//
//		final var key = WebKey.builder(Type.EC_P256).keyId(id).ephemeral(Algorithm.ES256).build().wellKnown();
//		final var uri = uri(
//				IuJson.object().add("keys", IuJson.array().add(IuJson.parse(key.toString()))).build().toString());
//		builder.wellKnown(uri);
//		builder.wellKnown(uri);
//		assertEquals(key, new Jose(builder.toJson()).wellKnown());
//
//		assertThrows(IllegalStateException.class, () -> builder.jwks(uri("")));
//	}
//
//	@Test
//	public void testJwk() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.PS384);
//		assertThrows(NullPointerException.class, () -> builder.jwk(null));
//		assertThrows(NullPointerException.class, () -> builder.jwk(null, false));
//
//		final var key = WebKey.ephemeral(Algorithm.PS384);
//		builder.jwk(key);
//		builder.jwk(key);
//		assertNull(new Jose(builder).getKey());
//		assertEquals(key.wellKnown(), new Jose(new Builder().algorithm(Algorithm.PS384).jwk(key, false)).getKey());
//
//		assertThrows(IllegalStateException.class, () -> builder.jwk(WebKey.ephemeral(Algorithm.PS384)));
//	}
//
//	@Test
//	public void testType() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.HS512);
//		final var type = IdGenerator.generateId();
//		builder.type(type);
//		assertThrows(IllegalStateException.class, () -> builder.type(""));
//		builder.type(type);
//		assertEquals(type, new Jose(builder).getType());
//	}
//
//	@Test
//	public void testContentType() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.HS256);
//		final var contentType = IdGenerator.generateId();
//		builder.contentType(contentType);
//		assertThrows(IllegalStateException.class, () -> builder.contentType(""));
//		builder.contentType(contentType);
//		assertEquals(contentType, new Jose(builder).getContentType());
//	}
//
//	@Test
//	public void testPartyUInfo() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.ECDH_ES);
//
//		final var uinfo = new byte[12];
//		ThreadLocalRandom.current().nextBytes(uinfo);
//
//		builder.apu(uinfo);
//		assertThrows(IllegalStateException.class, () -> builder.apu(new byte[0]));
//		builder.apu(uinfo);
//		assertArrayEquals(uinfo, UnpaddedBinary.JSON.fromJson(builder.ext().get("apu")));
//
//		assertThrows(IllegalArgumentException.class, () -> new Builder().algorithm(Algorithm.DIRECT).apu(uinfo));
//	}
//
//	@Test
//	public void testPartyVInfo() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.ECDH_ES);
//
//		final var vinfo = new byte[12];
//		ThreadLocalRandom.current().nextBytes(vinfo);
//
//		builder.apv(vinfo);
//		assertThrows(IllegalStateException.class, () -> builder.apv(new byte[0]));
//		builder.apv(vinfo);
//		assertArrayEquals(vinfo, UnpaddedBinary.JSON.fromJson(builder.ext().get("apv")));
//
//		assertThrows(IllegalArgumentException.class, () -> new Builder().algorithm(Algorithm.DIRECT).apv(vinfo));
//	}
//
//	@Test
//	public void testInvalidExtension() {
//		assertThrows(IllegalArgumentException.class, () -> new Builder().ext("enc", Encryption.A256GCM));
//	}
//
//	@Test
//	public void testCrit() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.PBES2_HS384_A192KW);
//		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
//		builder.enc("p2c", IuJson.number(3072));
//		builder.enc("p2s", IuJson.string(UnpaddedBinary.base64Url(IdGenerator.generateId().getBytes())));
//		builder.crit("kid");
//		assertThrows(IllegalStateException.class, () -> new Jose(builder));
//		final var id = IdGenerator.generateId();
//		assertEquals(id, new Jose(builder.keyId(id)).getKeyId());
//	}
//
//	@Test
//	public void testKidMatch() {
//		final var id = IdGenerator.generateId();
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.DIRECT);
//		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
//		builder.keyId(id);
//		builder.jwk(WebKey.builder().keyId(id).ephemeral(Encryption.A128GCM).build(), false);
//		assertEquals(id, new Jose(builder).getKeyId());
//		assertEquals(id, new Jose(builder).getKey().getId());
//	}
//
//	@Test
//	public void testKidMismatch() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.DIRECT);
//		builder.keyId("foo");
//		builder.jwk(WebKey.builder().keyId("bar").ephemeral(Encryption.A128GCM).build(), false);
//		assertThrows(IllegalStateException.class, () -> new Jose(builder));
//	}
//
//	@Test
//	public void testCertAlone() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.RSA_OAEP_256);
//		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
//		builder.cert(CERT);
//		assertEquals(CERT, new Jose(builder).getCertificateChain()[0]);
//	}
//
//	@Test
//	public void testCertChainMatch() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.RSA_OAEP_256);
//		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
//		builder.cert(CERT);
//		builder.x5t(CERT_S1);
//		builder.x5t256(CERT_S256);
//		builder.jwk(WebKey.builder().pem(CERT_TEXT).build(), false);
//		assertEquals(CERT, new Jose(builder).getCertificateChain()[0]);
//	}
//
//	@Test
//	public void testCertChainMismatch() {
//		final var builder = new Builder();
//		builder.algorithm(Algorithm.RSA_OAEP_256);
//		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
//		builder.cert(CERT);
//		assertThrows(IllegalArgumentException.class, () -> builder.x5t(CERT_S256));
//		assertThrows(IllegalArgumentException.class, () -> builder.x5t256(CERT_S1));
//		builder.jwk(WebKey.ephemeral(Algorithm.RSA_OAEP_256), false);
//		assertThrows(IllegalStateException.class, () -> new Jose(builder));
//	}
//

}
