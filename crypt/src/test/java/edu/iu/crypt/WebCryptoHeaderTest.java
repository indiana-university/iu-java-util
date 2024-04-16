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
package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.crypt.WebCryptoHeader.Extension;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class WebCryptoHeaderTest extends IuCryptTestCase {

	private <T> void assertJson(Param param, T value) {
		final var json = param.json().toJson(value);
		final var fromJson = param.json().fromJson(json);
		assertTrue(IuObject.equals(value, fromJson), () -> value + " " + json);
	}

	@Test
	public void testVerifyRequiresAlgorithm() {
		final var header = mock(WebCryptoHeader.class);
		assertThrows(NullPointerException.class, () -> WebCryptoHeader.verify(header));
	}

	@Test
	public void testIsPresent() {
		final var header = mock(WebCryptoHeader.class);
		Param.ALGORITHM.isPresent(header);
		verify(header).getAlgorithm();
	}

	@Test
	public void testFromToJson() {
		assertJson(Param.ALGORITHM, IuTest.rand(Algorithm.class));
		assertJson(Param.CERTIFICATE_CHAIN, new X509Certificate[] { CERT });
		assertJson(Param.CERTIFICATE_SHA256_THUMBPRINT, CERT_S256);
		assertJson(Param.CERTIFICATE_THUMBPRINT, CERT_S1);
		assertJson(Param.CERTIFICATE_URI, uri(CERT_TEXT));
		assertJson(Param.CONTENT_TYPE, IdGenerator.generateId());
		assertJson(Param.CRITICAL_PARAMS, Set.of(IuTest.rand(Param.class).name));
		assertJson(Param.ENCRYPTION, IuTest.rand(Encryption.class));
		assertJson(Param.EPHEMERAL_PUBLIC_KEY, WebKey.ephemeral(Algorithm.ECDH_ES).wellKnown());
		assertJson(Param.INITIALIZATION_VECTOR, EphemeralKeys.rand(12));
		assertJson(Param.KEY, WebKey.ephemeral(Encryption.A128GCM));
		assertJson(Param.KEY_SET_URI, uri(WebKey.ephemeral(Algorithm.PS256).wellKnown().toString()));
		assertJson(Param.PARTY_UINFO, EphemeralKeys.rand(12));
		assertJson(Param.PARTY_VINFO, EphemeralKeys.rand(12));
		assertJson(Param.PASSWORD_COUNT, ThreadLocalRandom.current().nextInt(1024, 4096));
		assertJson(Param.PASSWORD_SALT, EphemeralKeys.rand(16));
		assertJson(Param.TAG, EphemeralKeys.rand(16));
		assertJson(Param.TYPE, IdGenerator.generateId());
		assertJson(Param.ZIP, "DEF");
	}

	@Test
	public void testUsedFor() {
		final var sig = Set.of(Param.ALGORITHM, Param.CERTIFICATE_CHAIN, Param.CERTIFICATE_SHA256_THUMBPRINT,
				Param.CERTIFICATE_THUMBPRINT, Param.CERTIFICATE_URI, Param.CONTENT_TYPE, Param.CRITICAL_PARAMS,
				Param.KEY, Param.KEY_ID, Param.KEY_SET_URI, Param.TYPE);

		for (final var param : Param.values()) {
			assertTrue(param.isUsedFor(Use.ENCRYPT));
			assertEquals(sig.contains(param), param.isUsedFor(Use.SIGN), param::name);
		}
	}

	@Test
	public void testIsNotPresent() {
		final var noJose = mock(WebCryptoHeader.class);
		for (final var param : Param.values())
			assertEquals(param == Param.CRITICAL_PARAMS, param.isPresent(noJose), param::name);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExtensionRestrictions() {
		final var ext = mock(Extension.class);
		final var id = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> WebCryptoHeader.register("alg", ext));
		WebCryptoHeader.register(id, ext);
		assertThrows(IllegalArgumentException.class, () -> WebCryptoHeader.register(id, ext));
	}

}
