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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import iu.jwt.spi.Init;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

@SuppressWarnings("javadoc")
public class JwtSpiTest {

	static {
		Init.init();
	}

	@Test
	void testInstance() {
		assertInstanceOf(JwtSpi.class, Init.SPI);
	}
	
	@Test
	void testBuilder() {
		assertInstanceOf(JwtBuilder.class, WebToken.builder());
	}

	@Test
	void testJsonb() {
		assertSame(TokenJsonb.get(), WebToken.jsonb());
	}

	@Test
	void testEqualsJsonb() {
		try (final var mockTokenJsonb = mockStatic(TokenJsonb.class)) {
			WebToken.jsonbConfig();
			mockTokenJsonb.verify(() -> TokenJsonb.jsonbConfig());
		}
	}

	@Test
	void testRegister() {
		final var adapter = mock(JsonbAdapter.class);
		final var serializer = mock(JsonbSerializer.class);
		final var deserializer = mock(JsonbDeserializer.class);
		try (final var mockTokenJsonb = mockStatic(TokenJsonb.class)) {
			WebToken.registerAdapter(adapter);
			mockTokenJsonb.verify(() -> TokenJsonb.registerAdapter(adapter));
			WebToken.registerSerializer(serializer);
			mockTokenJsonb.verify(() -> TokenJsonb.registerSerializer(serializer));
			WebToken.registerDeserializer(deserializer);
			mockTokenJsonb.verify(() -> TokenJsonb.registerDeserializer(deserializer));
		}
	}

	@Test
	void testOf() {
		final var role = IdGenerator.generateId();
		final var claims = IuJson.object().add("roles", role).build();

		assertArrayEquals(new String[] { role }, WebToken.of(claims).getClaim("roles", String[].class));
		assertArrayEquals(new String[] { role },
				WebToken.of(claims.toString()).getClaim("roles", String[].class));

		assertEquals("expected JsonObject or String; found null",
				assertThrows(IllegalArgumentException.class, () -> WebToken.of(null)).getMessage());
		assertEquals("expected JsonObject or String; found java.lang.Integer",
				assertThrows(IllegalArgumentException.class, () -> WebToken.of(1)).getMessage());
		assertEquals("expected JsonObject or String; found " + IuJsonProperties.class.getName(),
				assertThrows(IllegalArgumentException.class,
						() -> WebToken.of(IuJsonProperties.of(claims, WebToken.jsonb()))).getMessage());
		assertThrows(JsonbException.class, () -> WebToken.of("[]"));
		assertThrows(JsonbException.class, () -> WebToken.of("not json"));

		// point in time claims are validated
		final var expired = IuJson.object().add("exp", Instant.now().minusSeconds(60L).getEpochSecond()).build();
		assertEquals("Token is expired",
				assertThrows(IllegalArgumentException.class, () -> WebToken.of(expired)).getMessage());

		assertEquals("expected a JSON object; found null",
				assertThrows(IllegalArgumentException.class, () -> WebToken.of("null")).getMessage());

		// a point in time claim that doesn't convert is rejected, not read as absent
		for (final var invalid : List.of("{\"exp\":\"x\"}", "{\"exp\":1e30}", "{\"iat\":true}", "{\"nbf\":{}}")) {
			assertEquals("Invalid point in time claim",
					assertThrows(IllegalArgumentException.class, () -> WebToken.of(invalid)).getMessage(), invalid);
			assertEquals("Invalid point in time claim", assertThrows(IllegalArgumentException.class,
					() -> WebToken.of(IuJson.parse(invalid).asJsonObject())).getMessage(), invalid);
		}
	}

	@Test
	void testVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		final var verified = mock(Jwt.class);
		try (final var mockJwt = mockStatic(Jwt.class)) {
			mockJwt.when(() -> Jwt.verify(jwt, issuerKey)).thenReturn(verified);
			assertEquals(verified, WebToken.verify(jwt, issuerKey));
		}
	}

	@Test
	void testDecryptAndVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		final var decryptKey = mock(WebKey.class);
		final var verified = mock(Jwt.class);
		try (final var mockJwt = mockStatic(Jwt.class)) {
			mockJwt.when(() -> Jwt.decryptAndVerify(jwt, issuerKey, decryptKey)).thenReturn(verified);
			assertEquals(verified, WebToken.decryptAndVerify(jwt, issuerKey, decryptKey));
		}
	}

}
