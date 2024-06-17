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
package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import edu.iu.auth.config.AuthConfig;
import iu.auth.config.IuAuthConfig;

@SuppressWarnings("javadoc")
public class JwtTestCase {

	private MockedStatic<AuthConfig> mockAuthConfig;
	private Map<String, IuAuthConfig> configs;
	private boolean sealed;

	@BeforeEach
	public void setup() {
		mockAuthConfig = mockStatic(AuthConfig.class);
		configs = new HashMap<>();
		mockAuthConfig.when(() -> AuthConfig.register(any())).then(a -> {
			assertFalse(sealed);

			final IuAuthConfig c = a.getArgument(0);
			configs.put(c.getRealm(), c);
			return null;
		});
		mockAuthConfig.when(() -> AuthConfig.seal()).then(a -> {
			sealed = true;
			return null;
		});
		mockAuthConfig.when(() -> AuthConfig.get(any(String.class))).thenAnswer(a -> {
			assertTrue(sealed);
			return Objects.requireNonNull(configs.get(a.getArguments()[0]));
		});
	}

	@AfterEach
	public void teardown() {
		mockAuthConfig.close();
		sealed = false;
		mockAuthConfig = null;
		configs = null;
	}

}
