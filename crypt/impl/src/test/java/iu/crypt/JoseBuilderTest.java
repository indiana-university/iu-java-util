/*
 * Copyright © 2025 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;
import iu.crypt.Jose.Extension;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class JoseBuilderTest {

	private static class Builder extends JoseBuilder<Builder> {
		private Builder(Algorithm algorithm) {
			super(algorithm);
		}

		@Override
		protected JsonObject toJson() {
			return super.toJson();
		}
	}

	private static Builder jose() {
		return new Builder(Algorithm.RSA_OAEP);
	}

	@Test
	public void testEmpty() {
		assertEquals(IuJson.object().add("alg", Algorithm.RSA_OAEP.alg).build(), jose().toJson());
	}

	@Test
	public void testWellKnown() {
		final var uri = mock(URI.class);
		assertEquals(uri.toString(), jose().wellKnown(uri).toJson().getString("jku"));
	}

	@Test
	public void testKey() {
		final var key = WebKey.ephemeral(Algorithm.RSA_OAEP);
		assertEquals(key.wellKnown().toString(), jose().wellKnown(key).toJson().getJsonObject("jwk").toString());
	}

	@Test
	public void testKeyNotWellKnown() {
		final var key = WebKey.ephemeral(Algorithm.RSA_OAEP);
		final var builder = jose().key(key);
		assertSame(key, builder.key());
		assertFalse(jose().toJson().containsKey("jwk"));
		assertSame(key, jose().copy(builder).key());
	}

	@Test
	public void testType() {
		final var type = IdGenerator.generateId();
		assertEquals(type, jose().type(type).toJson().getString("typ"));
	}

	@Test
	public void testContentType() {
		final var contentType = IdGenerator.generateId();
		assertEquals(contentType, jose().contentType(contentType).toJson().getString("cty"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCrit() {
		final var crit = Set.of(IuTest.rand(Param.class).name);
		assertEquals(IuJson.array().add(crit.iterator().next()).build(),
				jose().crit(crit.toArray(String[]::new)).toJson().getJsonArray("crit"));
		final var id = IdGenerator.generateId();

		assertThrows(NullPointerException.class, () -> jose().crit(id).toJson().getJsonArray("crit"));

		final var ext = mock(Extension.class);
		Jose.register(id, ext);
		assertEquals(IuJson.array().add(id).build(), jose().crit(id).toJson().getJsonArray("crit"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testParams() {
		final var id = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> jose().param(id, ""));

		final var ext = mock(Extension.class);
		final var val = IdGenerator.generateId();
		when(ext.toJson(val)).thenReturn(IuJson.string(val));
		Jose.register(id, ext);

		final var jose = jose();
		assertEquals(val, jose.param(id, val).toJson().getString(id));
		verify(ext).validate(val, jose);

		assertThrows(UnsupportedOperationException.class,
				() -> jose().param("foo", "bar", IuJsonAdapter.of(String.class)));
	}

}
