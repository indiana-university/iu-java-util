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
package iu.redis.lettuce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.IuRuntimeEnvironment;
import edu.iu.crypt.PemEncoded;
import edu.iu.redis.IuRedisConfiguration;
import edu.iu.test.IuTestLogger;

@EnabledIf("iu.redis.lettuce.LettuceConnectionIT#isEnabled")
@SuppressWarnings("javadoc")
public class LettuceConnectionIT {
	private static IuRedisConfiguration config;

	public static boolean isEnabled() {
		return IuRuntimeEnvironment.envOptional("redis.host") != null;
	}

	@BeforeAll
	public static void setupClass() throws IOException {
		final var caCert = Path.of(IuRuntimeEnvironment.env("redis.cacert"));
		final var ca = PemEncoded.parse(Files.readString(caCert)).next().asCertificate();
		config = new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return IuRuntimeEnvironment.envOptional("redis.host");
			}

			@Override
			public String getPort() {
				return IuRuntimeEnvironment.env("redis.port");
			}

			@Override
			public String getPassword() {
				return IuRuntimeEnvironment.env("redis.password");
			}

			@Override
			public X509Certificate getTrustedCert() {
				return ca;
			}

		};
	}

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("", Level.FINE);
	}

	@Test
	public void testConnection() {
		final var connection = new LettuceConnection(config);
		assertNotNull(connection);
		byte[] key = "testKey".getBytes();
		byte[] value = "testValue".getBytes();
		connection.put(key, value, null);
		assertEquals(new String(value), new String(connection.get(key)));
	}
}
