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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import edu.iu.IuProcess;
import edu.iu.crypt.PemEncoded;
import edu.iu.redis.IuRedisConfiguration;
import edu.iu.test.IuTestLogger;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.RedisURI.Builder;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.ConnectionPoolSupport;

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
			assertDoesNotThrow(() -> lettuceConnection.put("key".getBytes(), null));
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
	@SuppressWarnings("unchecked")
	public void testWithTrustedCert() throws Exception {
		IuTestLogger.allow("", Level.FINE);
		final var config = mock(IuRedisConfiguration.class);
		when(config.getHost()).thenReturn("localhost");
		when(config.getPort()).thenReturn("6379");
		when(config.getPassword()).thenReturn("securePassword");

		final var mockCert = mock(X509Certificate.class);
		when(config.getTrustedCert()).thenReturn(mockCert);

		final var tempFile = Files.createTempFile("test-trusted-cert-", ".pem");
		try (final var redisClientStaticMock = mockStatic(RedisClient.class);
				final var iuProcessMock = mockStatic(IuProcess.class);
				final var pemEncodedMock = mockStatic(PemEncoded.class)) {

			iuProcessMock.when(IuProcess::createTempFile).thenReturn(tempFile);
			pemEncodedMock.when(() -> PemEncoded.print(any(PrintStream.class), any(X509Certificate.class)))
					.thenAnswer(inv -> null);

			final var mockClient = mock(RedisClient.class);
			when(mockClient.connect()).thenReturn(mock(StatefulRedisConnection.class));
			redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);

			final var lettuceConnection = new LettuceConnection(config);
			assertNotNull(lettuceConnection);
			verify(mockClient).setOptions(any(ClientOptions.class));
			assertDoesNotThrow(() -> lettuceConnection.close());
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testEnablesKeepAliveWithoutATrustedCert() {
		// the socket option is defense in depth against a silently dropped
		// connection, and unlike sslOptions must apply whether or not a cert is
		// configured, so setOptions can no longer be conditional on one being present
		IuTestLogger.allow("", Level.FINE);
		final var config = mock(IuRedisConfiguration.class);
		when(config.getHost()).thenReturn("localhost");
		when(config.getPort()).thenReturn("6379");
		when(config.getPassword()).thenReturn("securePassword");

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final var mockClient = mock(RedisClient.class);
			when(mockClient.connect()).thenReturn(mock(StatefulRedisConnection.class));
			redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);

			final var lettuceConnection = new LettuceConnection(config);
			assertNotNull(lettuceConnection);

			final var optionsCaptor = ArgumentCaptor.forClass(ClientOptions.class);
			verify(mockClient).setOptions(optionsCaptor.capture());
			assertTrue(optionsCaptor.getValue().getSocketOptions().isKeepAlive());

			assertDoesNotThrow(() -> lettuceConnection.close());
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testConfiguresAnIdleValidationSweepInsteadOfTrustingIsOpen() {
		IuTestLogger.allow("", Level.FINE);
		final var config = mock(IuRedisConfiguration.class);
		when(config.getHost()).thenReturn("localhost");
		when(config.getPort()).thenReturn("6379");
		when(config.getPassword()).thenReturn("securePassword");

		try (final var redisClientStaticMock = mockStatic(RedisClient.class);
				final var poolSupportMock = mockStatic(ConnectionPoolSupport.class)) {
			final var mockClient = mock(RedisClient.class);
			redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);

			final var mockPool = mock(GenericObjectPool.class);
			final ArgumentCaptor<GenericObjectPoolConfig<StatefulRedisConnection<String, String>>> poolConfigCaptor = //
					ArgumentCaptor.forClass(GenericObjectPoolConfig.class);
			final ArgumentCaptor<Predicate<StatefulRedisConnection<String, String>>> validatorCaptor = //
					ArgumentCaptor.forClass(Predicate.class);
			poolSupportMock.when(() -> ConnectionPoolSupport.createGenericObjectPool(any(Supplier.class),
					poolConfigCaptor.capture(), validatorCaptor.capture())).thenReturn(mockPool);

			assertNotNull(new LettuceConnection(config));

			final var poolConfig = poolConfigCaptor.getValue();
			assertTrue(poolConfig.getTestWhileIdle());
			assertEquals(Duration.ofSeconds(30), poolConfig.getTimeBetweenEvictionRuns());

			// delegates to isHealthy rather than the default isOpen() check, which would
			// report a silently dropped connection as healthy
			assertFalse(validatorCaptor.getValue().test(null));
		}
	}

	@Test
	public void testIsHealthyRejectsAMissingConnection() {
		assertFalse(LettuceConnection.isHealthy(null));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testIsHealthyAcceptsAPromptPong() throws Exception {
		final var connection = mock(StatefulRedisConnection.class);
		final var async = mock(RedisAsyncCommands.class);
		final var future = mock(RedisFuture.class);
		when(connection.async()).thenReturn(async);
		when(async.ping()).thenReturn(future);
		when(future.get(anyLong(), any(TimeUnit.class))).thenReturn("PONG");

		assertTrue(LettuceConnection.isHealthy(connection));

		// the future is always retired, even after a successful reply, so nothing is
		// left registered against a connection the pool may hand out again
		verify(future).cancel(true);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testIsHealthyRejectsAnUnexpectedReply() throws Exception {
		final var connection = mock(StatefulRedisConnection.class);
		final var async = mock(RedisAsyncCommands.class);
		final var future = mock(RedisFuture.class);
		when(connection.async()).thenReturn(async);
		when(async.ping()).thenReturn(future);
		when(future.get(anyLong(), any(TimeUnit.class))).thenReturn("not PONG");

		assertFalse(LettuceConnection.isHealthy(connection));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testIsHealthyTreatsATimeoutAsUnhealthy() throws Exception {
		// exactly the case this check exists for: a half-open socket a silently
		// dropped connection leaves behind never answers at all
		final var connection = mock(StatefulRedisConnection.class);
		final var async = mock(RedisAsyncCommands.class);
		final var future = mock(RedisFuture.class);
		when(connection.async()).thenReturn(async);
		when(async.ping()).thenReturn(future);
		when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());

		assertFalse(LettuceConnection.isHealthy(connection));
		verify(future).cancel(true);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testIsHealthyTreatsAnInterruptionAsUnhealthyAndRestoresTheFlag() throws Exception {
		final var connection = mock(StatefulRedisConnection.class);
		final var async = mock(RedisAsyncCommands.class);
		final var future = mock(RedisFuture.class);
		when(connection.async()).thenReturn(async);
		when(async.ping()).thenReturn(future);
		when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

		assertFalse(LettuceConnection.isHealthy(connection));
		assertTrue(Thread.interrupted());
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
