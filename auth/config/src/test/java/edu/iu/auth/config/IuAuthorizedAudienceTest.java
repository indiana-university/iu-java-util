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
package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import iu.auth.config.AuthConfig;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class IuAuthorizedAudienceTest {

	@Test
	public void testOf() {
		final var authId = IdGenerator.generateId();
		final var audience = mock(IuAuthorizedAudience.class);
		try (final var mockAuthConfig = mockStatic(AuthConfig.class)) {
			mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizedAudience.class, "audience/" + authId))
					.thenReturn(audience);
			assertSame(audience, IuAuthorizedAudience.of(authId));
		}
	}

	@Test
	public void testJsonNullReturnsNull() {
		assertNull(IuAuthorizedAudience.JSON.fromJson(null));
	}

	@Test
	public void testJsonStringCallsOf() {
		try (final var mockAuthorizedAudience = mockStatic(IuAuthorizedAudience.class)) {
			final var id = IuJson.string(IdGenerator.generateId());
			IuAuthorizedAudience.JSON.fromJson(id);
			mockAuthorizedAudience.verify(() -> IuAuthorizedAudience.of(id.getString()));
		}
	}

	@Test
	public void testJsonObjectWraps() {
		try (final var mockIuJson = mockStatic(IuJson.class)) {
			final var o = mock(JsonObject.class);
			when(o.asJsonObject()).thenReturn(o);
			IuAuthorizedAudience.JSON.fromJson(o);
			mockIuJson.verify(() -> IuJson.wrap(eq(o), eq(IuAuthorizedAudience.class), any()));
		}
	}

}
