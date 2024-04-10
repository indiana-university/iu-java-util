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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.crypt.IuCryptTestCase;
import edu.iu.crypt.WebCryptoHeader.Extension;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.JsonString;

@SuppressWarnings("javadoc")
public class JwsTest extends IuCryptTestCase {

	@Test
	public void testRFC8037_A_4() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		final var jwk = WebKey.parse("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\n"
				+ "   \"d\":\"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A\",\n"
				+ "   \"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}");
		assertEquals("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}",
				jwk.wellKnown().toString());

		final var jws = WebSignature.builder(Algorithm.EDDSA).compact().key(jwk).sign("Example of Ed25519 signing");
		final var compact = jws.compact();
		assertEquals("eyJhbGciOiJFZERTQSJ9.RXhhbXBsZSBvZiBFZDI1NTE5IHNpZ25pbmc.hgyY0il_MGCj"
				+ "P0JzlnLWG1PPOt7-09PGcvMg3AIbQR6dWbhijcNR4ki4iylGjg5BhVsPt9g7sVvpAr_MuM0KAg", compact);

		final var fromCompact = WebSignedPayload.parse(compact);
		assertEquals("{\"alg\":\"EdDSA\"}", fromCompact.getSignatures().iterator().next().getHeader().toString());
		assertDoesNotThrow(() -> fromCompact.verify(jwk));

		final var wrongKey = WebKey.ephemeral(Algorithm.EDDSA);
		assertThrows(IllegalArgumentException.class, () -> fromCompact.verify(wrongKey));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAllTheSignatures() {
		final var extName = IdGenerator.generateId();
		final var ext = mock(Extension.class, CALLS_REAL_METHODS);
		when(ext.toJson(any())).thenAnswer(a -> IuJson.string((String) a.getArgument(0)));
		when(ext.fromJson(any())).thenAnswer(a -> ((JsonString) a.getArgument(0)).getString());
		Jose.register(extName, ext);

		final Queue<Jwk> keys = new ArrayDeque<>();
		final var algorithmIterator = IuIterable
				.filter(IuIterable.iter(Algorithm.values()), a -> a.use.equals(Use.SIGN)).iterator();

		var algorithm = algorithmIterator.next();
		final var jwsBuilder = WebSignature.builder(algorithm);

		var first = true;
		do {
			if (first)
				first = false;
			else
				jwsBuilder.next(algorithm = algorithmIterator.next());
			final var key = (Jwk) WebKey.ephemeral(algorithm);
			keys.add(key);
			jwsBuilder.key(key);
			jwsBuilder.param(extName, IdGenerator.generateId());
		} while (algorithmIterator.hasNext());

		final var data = new byte[16384];
		ThreadLocalRandom.current().nextBytes(data);
		final var jws = jwsBuilder.sign(data);
		final var serial = jws.toString();
		final var fromSerial = WebSignedPayload.parse(serial);
		keys.forEach(fromSerial::verify);
		fromSerial.getSignatures().forEach(a -> verify(ext).verify(a));
	}

	@Test
	public void testHeaderVerification() {
		final var p = IuJson.object().add("alg", "HS256").build();
		final var jose = new Jose(IuJson.object().add("alg", "HS384").build());
		assertThrows(IllegalArgumentException.class, () -> new Jws(p, jose, null));

		final var extName = ext();
		final var p2 = IuJson.object().add("alg", "HS256").add(extName, IdGenerator.generateId()).build();
		final var jose2 = new Jose(IuJson.object().add("alg", "HS256").add(extName, IdGenerator.generateId()).build());
		assertThrows(IllegalArgumentException.class, () -> new Jws(p2, jose2, null));
		assertDoesNotThrow(() -> new Jws(p2, new Jose(p2), null));
	}
}
