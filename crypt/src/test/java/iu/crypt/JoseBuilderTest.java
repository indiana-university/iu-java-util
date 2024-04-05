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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.crypt.IuCryptTestCase;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class JoseBuilderTest extends IuCryptTestCase {

	private static class Builder extends JoseBuilder<Builder> {
		@Override
		protected Builder next() {
			return this;
		}
	}

	@Test
	public void testJwkUri() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.ES256);
		assertThrows(NullPointerException.class, () -> builder.jwks(null));

		final var id = IdGenerator.generateId();
		builder.id(id);

		final var key = WebKey.builder().id(id).ephemeral(Algorithm.ES256).build().wellKnown();
		final var uri = uri(
				IuJson.object().add("keys", IuJson.array().add(IuJson.parse(key.toString()))).build().toString());
		builder.jwks(uri);
		builder.jwks(uri);
		assertEquals(key, new Jose(builder).wellKnown());

		assertThrows(IllegalStateException.class, () -> builder.jwks(uri("")));
	}

	@Test
	public void testJwk() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.PS384);
		assertThrows(NullPointerException.class, () -> builder.jwk(null));
		assertThrows(NullPointerException.class, () -> builder.jwk(null, false));

		final var key = WebKey.ephemeral(Algorithm.PS384);
		builder.jwk(key);
		builder.jwk(key);
		assertNull(new Jose(builder).getKey());
		assertEquals(key.wellKnown(), new Jose(new Builder().algorithm(Algorithm.PS384).jwk(key, false)).getKey());

		assertThrows(IllegalStateException.class, () -> builder.jwk(WebKey.ephemeral(Algorithm.PS384)));
	}

	@Test
	public void testType() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.HS512);
		final var type = IdGenerator.generateId();
		builder.type(type);
		assertThrows(IllegalStateException.class, () -> builder.type(""));
		builder.type(type);
		assertEquals(type, new Jose(builder).getType());
	}

	@Test
	public void testContentType() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.HS256);
		final var contentType = IdGenerator.generateId();
		builder.contentType(contentType);
		assertThrows(IllegalStateException.class, () -> builder.contentType(""));
		builder.contentType(contentType);
		assertEquals(contentType, new Jose(builder).getContentType());
	}

	@Test
	public void testPartyUInfo() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.ECDH_ES);

		final var uinfo = new byte[12];
		ThreadLocalRandom.current().nextBytes(uinfo);

		builder.apu(uinfo);
		assertThrows(IllegalStateException.class, () -> builder.apu(new byte[0]));
		builder.apu(uinfo);
		assertArrayEquals(uinfo, UnpaddedBinary.JSON.fromJson(builder.ext().get("apu")));

		assertThrows(IllegalArgumentException.class, () -> new Builder().algorithm(Algorithm.DIRECT).apu(uinfo));
	}

	@Test
	public void testPartyVInfo() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.ECDH_ES);

		final var vinfo = new byte[12];
		ThreadLocalRandom.current().nextBytes(vinfo);

		builder.apv(vinfo);
		assertThrows(IllegalStateException.class, () -> builder.apv(new byte[0]));
		builder.apv(vinfo);
		assertArrayEquals(vinfo, UnpaddedBinary.JSON.fromJson(builder.ext().get("apv")));

		assertThrows(IllegalArgumentException.class, () -> new Builder().algorithm(Algorithm.DIRECT).apv(vinfo));
	}

	@Test
	public void testInvalidExtension() {
		assertThrows(IllegalArgumentException.class, () -> new Builder().ext("enc", Encryption.A256GCM));
	}

	@Test
	public void testCrit() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.PBES2_HS384_A192KW);
		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
		builder.enc("p2c", IuJson.number(3072));
		builder.enc("p2s", IuJson.string(UnpaddedBinary.base64Url(IdGenerator.generateId().getBytes())));
		builder.crit("kid");
		assertThrows(IllegalStateException.class, () -> new Jose(builder));
		final var id = IdGenerator.generateId();
		assertEquals(id, new Jose(builder.id(id)).getKeyId());
	}

	@Test
	public void testKidMatch() {
		final var id = IdGenerator.generateId();
		final var builder = new Builder();
		builder.algorithm(Algorithm.DIRECT);
		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
		builder.id(id);
		builder.jwk(WebKey.builder().id(id).ephemeral(Encryption.A128GCM).build(), false);
		assertEquals(id, new Jose(builder).getKeyId());
		assertEquals(id, new Jose(builder).getKey().getId());
	}

	@Test
	public void testKidMismatch() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.DIRECT);
		builder.id("foo");
		builder.jwk(WebKey.builder().id("bar").ephemeral(Encryption.A128GCM).build(), false);
		assertThrows(IllegalStateException.class, () -> new Jose(builder));
	}

	@Test
	public void testCertAlone() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.RSA_OAEP_256);
		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
		builder.cert(CERT);
		assertEquals(CERT, new Jose(builder).getCertificateChain()[0]);
	}

	@Test
	public void testCertChainMatch() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.RSA_OAEP_256);
		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
		builder.cert(CERT);
		builder.x5t(CERT_S1);
		builder.x5t256(CERT_S256);
		builder.jwk(WebKey.builder().pem(CERT_TEXT).build(), false);
		assertEquals(CERT, new Jose(builder).getCertificateChain()[0]);
	}

	@Test
	public void testCertChainMismatch() {
		final var builder = new Builder();
		builder.algorithm(Algorithm.RSA_OAEP_256);
		builder.enc("enc", IuJson.string(Encryption.A128GCM.enc));
		builder.cert(CERT);
		assertThrows(IllegalArgumentException.class, () -> builder.x5t(CERT_S256));
		assertThrows(IllegalArgumentException.class, () -> builder.x5t256(CERT_S1));
		builder.jwk(WebKey.ephemeral(Algorithm.RSA_OAEP_256), false);
		assertThrows(IllegalStateException.class, () -> new Jose(builder));
	}

}
