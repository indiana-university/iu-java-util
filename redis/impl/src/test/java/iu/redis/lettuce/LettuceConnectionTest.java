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
package iu.redis.lettuce;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.redis.IuRedisConfiguration;
import edu.iu.test.IuTestLogger;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.RedisURI.Builder;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

@SuppressWarnings("javadoc")
public class LettuceConnectionTest {

	private MockedStatic<RedisURI.Builder> builder;
	private RedisURI redisURI;

	@BeforeEach
	void setUp() {
		builder = mockStatic(RedisURI.Builder.class);
		redisURI = mock(RedisURI.class);
		Builder mockBuilder = mock(Builder.class, a -> a.getMethod().getName().startsWith("with") ? a.getMock() : null);
		when(mockBuilder.build()).thenReturn(redisURI);
		builder.when(() -> RedisURI.Builder.redis(any(String.class), any(Integer.class))).thenReturn(mockBuilder);

	}

	@AfterEach
	void tearDown() {
		builder.close();
		redisURI = null;
	}

	@Test
	public void testConfigurationRequired() {
		assertThrows(NullPointerException.class, () -> new LettuceConnection(null));
	}

	@Test
	public void testHostRequired() {
		assertThrows(NullPointerException.class, () -> new LettuceConnection(new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return null;
			}

			@Override
			public String getPort() {
				return "1234";
			}

			@Override
			public String getPassword() {
				return "password";
			}

			@Override
			public String getUsername() {
				return "username";
			}
		}));
	}

	@Test
	public void testPortRequired() {
		assertThrows(NullPointerException.class, () -> new LettuceConnection(new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return "localhost";
			}

			@Override
			public String getPort() {
				return null;
			}

			@Override
			public String getPassword() {
				return "password";
			}

			@Override
			public String getUsername() {
				return "username";
			}
		}));
	}

	@Test
	public void testPasswordRequired() {
		assertThrows(NullPointerException.class, () -> new LettuceConnection(new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return "localhost";
			}

			@Override
			public String getPort() {
				return "1234";
			}

			@Override
			public String getPassword() {
				return null;
			}

			@Override
			public String getUsername() {
				return "username";
			}
		}));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSuccessConnection() {
		IuTestLogger.allow("", Level.FINE);
		String mockHost = "localhost";
		String mockPort = "6379";
		String mockPassword = "securePassword";
		final var config = mock(IuRedisConfiguration.class);
		when(config.getHost()).thenReturn(mockHost);
		when(config.getPort()).thenReturn(mockPort);
		when(config.getPassword()).thenReturn(mockPassword);

		when(config.getUsername()).thenReturn("username");

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			StatefulRedisConnection<String, String> mockConnection = mock(StatefulRedisConnection.class);
			RedisCommands<String, String> mockCommands = mock(RedisCommands.class);
			final var mockClient = mock(RedisClient.class);
			when(mockClient.connect()).thenReturn(mockConnection);
			when(mockConnection.sync()).thenReturn(mockCommands);
			when(mockCommands.get(any())).thenReturn("value", (String) null);

			redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);
			LettuceConnection lettuceConnection = new LettuceConnection(config);
			assertNotNull(lettuceConnection);
			assertThrows(NullPointerException.class, () -> lettuceConnection.put(null, null, null));
			assertDoesNotThrow(() -> lettuceConnection.put("key".getBytes(), "value".getBytes(), null));
			assertDoesNotThrow(
					() -> lettuceConnection.put("key".getBytes(), "value".getBytes(), Duration.ofSeconds(10)));
			assertDoesNotThrow(
					() -> lettuceConnection.put("key".getBytes(), "value".getBytes(), Duration.ofSeconds(0)));
			assertDoesNotThrow(
					() -> lettuceConnection.put("key".getBytes(), "value".getBytes(), Duration.ofSeconds(-1)));
			assertDoesNotThrow(() -> lettuceConnection.put("key".getBytes(), "value".getBytes()));
			assertDoesNotThrow(() -> lettuceConnection.get("key".getBytes()));
			assertDoesNotThrow(() -> lettuceConnection.get("key".getBytes()));
			assertThrows(UnsupportedOperationException.class, () -> lettuceConnection.list());
			assertDoesNotThrow(() -> lettuceConnection.close());
		}

	}

	@Test
	public void testFailureConnection() {
		IuTestLogger.allow("", Level.FINE);
		String mockHost = "localhost";
		String mockPort = "6379";
		String mockPassword = "securePassword";
		final var config = mock(IuRedisConfiguration.class);
		when(config.getHost()).thenReturn(mockHost);
		when(config.getPort()).thenReturn(mockPort);
		when(config.getPassword()).thenReturn(mockPassword);

		when(config.getUsername()).thenReturn("username");

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final var mockClient = mock(RedisClient.class);
			when(mockClient.connect()).thenReturn(null);
			redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);
			LettuceConnection lettuceConnection = new LettuceConnection(config);
			assertNotNull(lettuceConnection);
			assertThrows(NullPointerException.class, () -> lettuceConnection.get("key".getBytes()));
			assertThrows(NullPointerException.class,
					() -> lettuceConnection.put("key".getBytes(), "value".getBytes(), null));
			assertDoesNotThrow(() -> lettuceConnection.close());
			assertDoesNotThrow(() -> lettuceConnection.close());
		}
	}

	@Test
	public void testCloseError() {
		IuTestLogger.allow("", Level.FINE);
		String mockHost = "localhost";
		String mockPort = "6379";
		String mockPassword = "securePassword";
		final var config = mock(IuRedisConfiguration.class);
		when(config.getHost()).thenReturn(mockHost);
		when(config.getPort()).thenReturn(mockPort);
		when(config.getPassword()).thenReturn(mockPassword);

		when(config.getUsername()).thenReturn("username");

		final var error = new RuntimeException();
		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final var mockClient = mock(RedisClient.class);
			when(mockClient.connect()).thenReturn(null);
			doThrow(error).when(mockClient).shutdown();
			redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);
			LettuceConnection lettuceConnection = new LettuceConnection(config);
			assertSame(error, assertThrows(RuntimeException.class, () -> lettuceConnection.close()));
		}
	}
}
