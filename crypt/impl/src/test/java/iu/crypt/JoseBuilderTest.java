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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;
import iu.crypt.Jose.Extension;

@SuppressWarnings("javadoc")
public class JoseBuilderTest {

	private static class Builder extends JoseBuilder<Builder> {
		private Builder(Algorithm algorithm) {
			super(algorithm);
		}
	}

	private static Builder jose() {
		return new Builder(Algorithm.RSA_OAEP);
	}

	@Test
	public void testEmpty() {
		final var values = jose().values();
		assertEquals(Algorithm.RSA_OAEP, values.get("alg", Algorithm.class));
	}

	@Test
	public void testWellKnown() {
		final var uri = mock(URI.class);
		assertEquals(uri, jose().wellKnown(uri).values().get("jku", URI.class));
	}

	@Test
	public void testKey() {
		final var key = WebKey.ephemeral(Algorithm.RSA_OAEP);
		assertEquals(key.wellKnown().toString(), jose().wellKnown(key).values().get("jwk", WebKey.class).toString());
	}

	@Test
	public void testKeyNotWellKnown() {
		final var key = WebKey.ephemeral(Algorithm.RSA_OAEP);
		final var builder = jose().key(key);
		assertSame(key, builder.key());
		assertNull(builder.values().get("jwk", WebKey.class));
		assertSame(key, new JoseBuilder<>(builder).key());
	}

	@Test
	public void testType() {
		final var type = IdGenerator.generateId();
		assertEquals(type, jose().type(type).values().get("typ", String.class));
	}

	@Test
	public void testContentType() {
		final var contentType = IdGenerator.generateId();
		assertEquals(contentType, jose().contentType(contentType).values().get("cty", String.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCrit() {
		final var crit = IuTest.rand(Param.class).name;
		assertEquals(crit, jose().crit(crit).values().get("crit", String[].class)[0]);
		final var id = IdGenerator.generateId();

		assertEquals("must understand extension " + id,
				assertThrows(NullPointerException.class, () -> jose().crit(id).values().get("crit", String[].class))
						.getMessage());

		final var ext = mock(Extension.class);
		when(ext.type()).thenReturn(String.class);
		Jose.register(id, ext);
		assertEquals(id, jose().crit(id).values().get("crit", String[].class)[0]);
	}

}
