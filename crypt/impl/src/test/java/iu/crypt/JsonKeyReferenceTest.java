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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class JsonKeyReferenceTest {

	@Test
	public void testKeyId() {
		final var id = IdGenerator.generateId();
		final var ref = new JsonKeyReference<>(IuJsonProperties.builder().put("kid", id).build());
		assertEquals(id, ref.getKeyId());
	}

	@Test
	public void testAlg() {
		for (final var algorithm : Algorithm.values())
			assertEquals(algorithm,
					new JsonKeyReference<>(IuJsonProperties.builder().put("alg", algorithm).build()).getAlgorithm());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEqualsHashCodeRepresents() {
		final var uri = URI.create(IdGenerator.generateId());
		final var cert = mock(X509Certificate.class);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.getCertificateChain(uri)).thenReturn(new X509Certificate[] { cert });
			final var ref = new JsonKeyReference(IuJsonProperties.builder().put("x5u", uri).build());
			assertTrue(ref.hashCode() != 0);
			for (var i = 0; i < 4; i++)
				for (final var algorithm : Algorithm.values()) {
					final var id = IdGenerator.generateId();
					final var json = IuJsonProperties.builder().put("kid", id).put("alg", algorithm).build();

					final var keyRef = new JsonKeyReference(json);
					assertNotEquals(keyRef, ref);
					assertTrue(keyRef.represents(ref));

					assertEquals(keyRef, new JsonKeyReference(json));

					final var id2 = IdGenerator.generateId();
					final var json2 = IuJsonProperties.builder().put("kid", id2).put("alg", algorithm).build();
					final var keyRef2 = new JsonKeyReference(json2);
					assertNotEquals(keyRef, keyRef2);
					assertNotEquals(keyRef2, keyRef);
					assertTrue(keyRef2.represents(ref));
					assertFalse(keyRef2.represents(keyRef));

					final var json3 = IuJsonProperties.builder().put("kid", id).put("alg",
							Algorithm.values()[ThreadLocalRandom.current().nextInt(Algorithm.values().length)])
							.build();
					final var keyRef3 = new JsonKeyReference(json3);
					if (keyRef.getAlgorithm() != keyRef3.getAlgorithm()) {
						assertNotEquals(keyRef, keyRef3);
						assertNotEquals(keyRef3, keyRef);
						assertTrue(keyRef3.represents(ref));
						assertFalse(keyRef3.represents(keyRef));
					}
				}
		}
	}

}
