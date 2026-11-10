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
package iu.jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.WebToken;
import edu.iu.test.IuTestLogger;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * Verifies that token claims convert as {@link TokenJsonb} does while another
 * JSON-B instance, with none of its conversions, has a call in progress on the
 * thread: as when an application's getter reads a token while the application's
 * own JSON-B instance serializes its entity.
 */
@SuppressWarnings("javadoc")
public class ForeignJsonbCallTest {

	private static final ThreadLocal<Object> RESULT = new ThreadLocal<>();
	private static final ThreadLocal<Supplier<?>> PENDING = new ThreadLocal<>();

	/**
	 * Has no token conversions, and runs a {@link Supplier} it writes or reads.
	 */
	private static final Jsonb FOREIGN = JsonbBuilder.newBuilder("iu.client.jsonb.IuJsonbProvider")
			.withConfig(new JsonbConfig()
					.withSerializers(IuJsonAdapter.<Supplier<?>>typedSerializer(Supplier.class, (supplier, generator, context) -> {
						RESULT.set(supplier.get());
						generator.write("done");
					}))
					.withDeserializers(IuJsonAdapter.<Supplier<?>>typedDeserializer(Supplier.class, (parser, context, type) -> {
						final var supplier = PENDING.get();
						RESULT.set(supplier.get());
						return supplier;
					})))
			.build();

	/**
	 * Runs within a serialization, and again within a deserialization, by a JSON-B
	 * instance with no token conversions.
	 */
	@SuppressWarnings("unchecked")
	private static <T> T foreign(Supplier<T> supplier) {
		try {
			assertEquals("\"done\"", FOREIGN.toJson(supplier, Supplier.class));
			final var written = (T) RESULT.get();
			assertNotNull(written);

			RESULT.remove();
			PENDING.set(supplier);
			assertSame(supplier, FOREIGN.fromJson("\"done\"", Supplier.class));
			assertNotNull(RESULT.get());
			return written;
		} finally {
			RESULT.remove();
			PENDING.remove();
		}
	}

	private final URI issuer = URI.create("test:" + IdGenerator.generateId());
	private final URI audience = URI.create("test:" + IdGenerator.generateId());
	private final URI audience2 = URI.create("test:" + IdGenerator.generateId());
	private final String subject = IdGenerator.generateId();
	private final String nonce = IdGenerator.generateId();
	private final Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	private final Instant expires = issuedAt.plusSeconds(60L);

	@BeforeEach
	void setup() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
		IuTestLogger.allow("iu.jwt.spi.Init", Level.CONFIG);
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
	}

	private WebToken build() {
		return WebToken.builder().jti().iss(issuer).aud(audience, audience2).sub(subject).iat().nbf(issuedAt)
				.exp(expires).nonce(nonce).claim("at", issuedAt, Instant.class).build();
	}

	private void assertClaims(WebToken token) {
		assertNotNull(token.getTokenId());
		assertEquals(issuer, token.getIssuer());
		assertEquals(subject, token.getSubject());
		assertEquals(nonce, token.getNonce());
		assertEquals(expires, token.getExpires());
		assertEquals(issuedAt, token.getNotBefore());
		assertNotNull(token.getIssuedAt());
		assertEquals(issuedAt, token.getClaim("at", Instant.class));
		assertEquals(issuedAt.getEpochSecond(), token.getClaim("at", Long.class));
		final var aud = token.getAudience().iterator();
		assertEquals(audience, aud.next());
		assertEquals(audience2, aud.next());
		assertArrayEquals(new URI[] { audience, audience2 }, token.getClaim("aud", URI[].class));
		token.validateClaims(issuer, audience2, java.time.Duration.ofMinutes(2L));
	}

	@Test
	public void testVerify() {
		final var key = WebKey.ephemeral(Algorithm.ES256);
		final var jwt = build().sign("JWT", Algorithm.ES256, key);

		final var token = foreign(() -> {
			final var verified = WebToken.verify(jwt, key);
			assertClaims(verified);
			assertClaims(WebToken.verify(verified.sign("JWT", Algorithm.ES256, key), key));
			return verified;
		});
		assertClaims(token);

		// parsed outside the call, read within it
		final var parsed = WebToken.verify(jwt, key);
		foreign(() -> {
			assertClaims(parsed);
			return parsed;
		});
	}

	@Test
	public void testDecryptAndVerify() {
		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var encryptKey = WebKey.ephemeral(Encryption.A256GCM);
		final var jwt = build().signAndEncrypt("JWT", Algorithm.EDDSA, key, Algorithm.DIRECT, Encryption.A256GCM,
				encryptKey);

		assertClaims(foreign(() -> {
			final var verified = WebToken.decryptAndVerify(jwt, key, encryptKey);
			assertClaims(verified);
			assertClaims(WebToken.decryptAndVerify(verified.signAndEncrypt("JWT", Algorithm.EDDSA, key,
					Algorithm.DIRECT, Encryption.A256GCM, encryptKey), key, encryptKey));
			return verified;
		}));
	}

	@Test
	public void testBuild() {
		final var key = WebKey.ephemeral(Algorithm.HS256);
		final var token = foreign(() -> {
			final var built = build();
			assertClaims(built);
			assertClaims(WebToken.verify(built.sign("JWT", Algorithm.HS256, key), key));
			return built;
		});
		assertClaims(token);
		assertClaims(WebToken.verify(token.sign("JWT", Algorithm.HS256, key), key));
	}

	@Test
	public void testOf() {
		final var json = IuJson.object().add("iss", issuer.toString()).add("sub", subject)
				.add("aud", IuJson.array().add(audience.toString()).add(audience2.toString())).add("nonce", nonce)
				.add("jti", IdGenerator.generateId()).add("iat", issuedAt.getEpochSecond())
				.add("nbf", issuedAt.getEpochSecond()).add("exp", expires.getEpochSecond())
				.add("at", issuedAt.getEpochSecond()).build();

		assertClaims(foreign(() -> {
			final var fromText = WebToken.of(json.toString());
			assertClaims(fromText);
			return fromText;
		}));
		assertClaims(foreign(() -> {
			final var fromObject = WebToken.of(json);
			assertClaims(fromObject);
			return fromObject;
		}));

		final var fromText = WebToken.of(json.toString());
		final var fromObject = WebToken.of(json);
		foreign(() -> {
			assertClaims(fromText);
			assertClaims(fromObject);
			return fromText;
		});
	}

}
