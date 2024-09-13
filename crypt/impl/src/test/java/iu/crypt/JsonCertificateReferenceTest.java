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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.cert.X509Certificate;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.IuCryptTestCase;
import edu.iu.crypt.PemEncoded;

@SuppressWarnings("javadoc")
public class JsonCertificateReferenceTest extends IuCryptTestCase {

	@Test
	public void testCert() {
		final var ref = new JsonCertificateReference<>(IuJson.object().add("x5c",
				IuJsonAdapter.of(X509Certificate[].class, PemEncoded.CERT_JSON).toJson(new X509Certificate[] { CERT }))
				.build());
		assertEquals(CERT, ref.getCertificateChain()[0]);
	}
	
	@Test
	public void testCertUri() {
		final var ref = new JsonCertificateReference<>(IuJson.object().add("x5u", uri(CERT_TEXT).toString()).build());
		assertNull(ref.getCertificateChain());
		assertEquals(CERT, ref.verifiedCertificateChain()[0]);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEqualsHashCode() {
		final var a = IuJson.object() //
				.add("x5c", IuJson.array().add(PemEncoded.CERT_JSON.toJson(CERT))) //
				.add("x5u", uri(CERT_TEXT).toString()) //
				.add("x5t", UnpaddedBinary.base64Url(CERT_S1)) //
				.add("x5t#S256", UnpaddedBinary.base64Url(CERT_S256)) //
				.build();
		final var b = IuJson.object() //
				.add("x5c", IuJson.array().add(PemEncoded.CERT_JSON.toJson(ANOTHER_CERT))) //
				.add("x5u", uri(ANOTHER_CERT_TEXT).toString()) //
				.add("x5t", UnpaddedBinary.base64Url(ANOTHER_CERT_S1)) //
				.add("x5t#S256", UnpaddedBinary.base64Url(ANOTHER_CERT_S256)) //
				.build();

		final var ao = new JsonCertificateReference(a);
		final var bo = new JsonCertificateReference(b);
		assertNotEquals(ao, null);
		assertNotEquals(ao.hashCode(), bo.hashCode());

		for (var i = 1; i < 16; i++)
			for (var j = 1; j < 16; j++) {
				final var ai = IuJson.object();
				if ((i & 1) == 1)
					ai.add("x5c", a.get("x5c"));
				if ((i & 2) == 2)
					ai.add("x5u", a.get("x5u"));
				if ((i & 4) == 4)
					ai.add("x5t", a.get("x5t"));
				if ((i & 8) == 8)
					ai.add("x5t#S256", a.get("x5t#S256"));
				final var ac = new JsonCertificateReference(ai.build());

				final var bj = IuJson.object();
				if ((j & 1) == 1)
					bj.add("x5c", b.get("x5c"));
				if ((j & 2) == 2)
					bj.add("x5u", b.get("x5u"));
				if ((j & 4) == 4)
					bj.add("x5t", b.get("x5t"));
				if ((j & 8) == 8)
					bj.add("x5t#S256", b.get("x5t#S256"));
				final var bc = new JsonCertificateReference(bj.build());

				assertEquals(ac, new JsonCertificateReference(IuJson.parse(ac.toString()).asJsonObject()));
				assertEquals(bc, new JsonCertificateReference(IuJson.parse(bc.toString()).asJsonObject()));
				assertNotEquals(ac, bc);
				assertNotEquals(bc, ac);
				assertTrue(ac.represents(ao));
				assertTrue(bc.represents(bo));
				assertEquals(ac.represents(bc), bc.represents(ac));
			}
	}

}
