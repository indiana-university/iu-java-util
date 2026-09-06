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
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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

import edu.iu.IuDataStoreEntry;
import edu.iu.IuProcess;
import edu.iu.IuText;
import edu.iu.crypt.PemEncoded;
import edu.iu.redis.IuRedisConfiguration;
import edu.iu.test.IuTestLogger;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.RedisURI.Builder;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.CommandArgs;
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

	/** Data key for {@code "key"}, under the prefix {@link #config()} supplies. */
	private static final String DATA_KEY = "iu:d:{a2V5}";

	/** Write time key for {@code "key"}. */
	private static final String MODIFIED_KEY = "iu:m:{a2V5}";

	/** Answers the connection detail every test needs and none is about. */
	private static IuRedisConfiguration config() {
		final var config = mock(IuRedisConfiguration.class);
		when(config.getHost()).thenReturn("localhost");
		when(config.getPort()).thenReturn("6379");
		when(config.getPassword()).thenReturn("securePassword");
		when(config.getKeyPrefix()).thenReturn("iu");
		return config;
	}

	/** Renders scan arguments as the command line they would be sent as. */
	private static String command(ScanArgs args) {
		final var command = new CommandArgs<>(StringCodec.UTF8);
		args.build(command);
		return command.toCommandString();
	}

	/** Answers a connection that responds to every command through {@code commands}. */
	@SuppressWarnings("unchecked")
	private static StatefulRedisConnection<String, byte[]> connection(RedisCommands<String, byte[]> commands) {
		final var connection = mock(StatefulRedisConnection.class);
		when(connection.sync()).thenReturn(commands);
		return connection;
	}

	/**
	 * Stubs the client the connection under test will create, so that it hands out
	 * {@code connection} whatever codec it is asked for.
	 */
	@SuppressWarnings("rawtypes")
	private RedisClient client(MockedStatic<RedisClient> redisClientStaticMock,
			StatefulRedisConnection<String, byte[]> connection) {
		final var mockClient = mock(RedisClient.class);
		doReturn(connection).when(mockClient).connect(any(RedisCodec.class));
		redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);
		return mockClient;
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
		final var config = config();
		when(config.getUsername()).thenReturn("username");

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final RedisCommands<String, byte[]> mockCommands = mock(RedisCommands.class);
			client(redisClientStaticMock, connection(mockCommands));
			when(mockCommands.get(any())).thenReturn("value".getBytes(), (byte[]) null);

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
			assertDoesNotThrow(() -> lettuceConnection.close());
		}

	}

	@Test
	@SuppressWarnings("unchecked")
	public void testKeysAreNamespacedAndExact() {
		IuTestLogger.allow("", Level.FINE);
		final var config = config();

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final RedisCommands<String, byte[]> mockCommands = mock(RedisCommands.class);
			client(redisClientStaticMock, connection(mockCommands));

			// a key no byte of which is valid UTF-8: the encoding that reached Redis
			// before was a lossy decode of this, and could not be read back
			final var key = new byte[] { (byte) 0x80, (byte) 0xff, (byte) 0xfe };
			final var name = IuText.base64Url(key);
			assertArrayEquals(key, IuText.base64Url(name));

			try (final var connection = new LettuceConnection(config)) {
				connection.put(key, "value".getBytes(), Duration.ofSeconds(10L));
				verify(mockCommands).setex(eq("iu:d:{" + name + "}"), eq(10L), any());

				connection.get(key);
				verify(mockCommands).get("iu:d:{" + name + "}");
			}
		}
	}

	@Test
	public void testFailureConnection() {
		IuTestLogger.allow("", Level.FINE);
		final var config = config();
		when(config.getUsername()).thenReturn("username");

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			client(redisClientStaticMock, null);
			LettuceConnection lettuceConnection = new LettuceConnection(config);
			assertNotNull(lettuceConnection);
			assertThrows(NullPointerException.class, () -> lettuceConnection.get("key".getBytes()));
			assertThrows(NullPointerException.class, () -> lettuceConnection.lastModified("key".getBytes()));
			assertThrows(NullPointerException.class, () -> lettuceConnection.list());
			assertThrows(NullPointerException.class,
					() -> lettuceConnection.put("key".getBytes(), "value".getBytes(), null));
			assertDoesNotThrow(() -> lettuceConnection.close());
			assertDoesNotThrow(() -> lettuceConnection.close());
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testPutStampsTheWriteTimeAfterTheValue() {
		IuTestLogger.allow("", Level.FINE);
		final var config = config();

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final RedisCommands<String, byte[]> mockCommands = mock(RedisCommands.class);
			client(redisClientStaticMock, connection(mockCommands));

			try (final var connection = new LettuceConnection(config)) {
				final var order = inOrder(mockCommands);
				final var stamp = ArgumentCaptor.forClass(byte[].class);

				final var before = System.currentTimeMillis();
				connection.put("key".getBytes(), "value".getBytes(), Duration.ofSeconds(10L));
				final var after = System.currentTimeMillis();

				// the value first, then its write time, and both under the value's own
				// expiration so the stamp cannot outlive what it describes
				order.verify(mockCommands).setex(eq(DATA_KEY), eq(10L), aryEq("value".getBytes()));
				order.verify(mockCommands).setex(eq(MODIFIED_KEY), eq(10L), stamp.capture());
				assertTrue(Long.parseLong(IuText.ascii(stamp.getValue())) >= before);
				assertTrue(Long.parseLong(IuText.ascii(stamp.getValue())) <= after);

				// an entry stored without an expiration is stamped without one too
				connection.put("key".getBytes(), "value".getBytes(), null);
				order.verify(mockCommands).set(eq(DATA_KEY), aryEq("value".getBytes()));
				order.verify(mockCommands).set(eq(MODIFIED_KEY), any());

				// one command, so no stamp is left behind to be read as a later value's
				connection.put("key".getBytes(), null);
				order.verify(mockCommands).del(DATA_KEY, MODIFIED_KEY);
			}
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testLastModified() {
		IuTestLogger.allow("", Level.FINE);
		final var config = config();

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final RedisCommands<String, byte[]> mockCommands = mock(RedisCommands.class);
			client(redisClientStaticMock, connection(mockCommands));

			// stored to millisecond resolution, so the stamp read back is exact
			final var written = Instant.now().truncatedTo(ChronoUnit.MILLIS);
			when(mockCommands.get(MODIFIED_KEY)) //
					.thenReturn(IuText.ascii(Long.toString(written.toEpochMilli())), null,
							IuText.ascii("not a timestamp"));

			try (final var connection = new LettuceConnection(config)) {
				assertThrows(NullPointerException.class, () -> connection.lastModified(null));

				assertEquals(written, connection.lastModified("key".getBytes()));

				// no stamp: the write time is unknown, which does not mean the value is
				// absent -- an entry written before this store began stamping reads the
				// same way
				assertNull(connection.lastModified("key".getBytes()));

				// only reachable if something else owns the companion key; an unknown
				// write time beats failing the freshness check outright
				assertNull(connection.lastModified("key".getBytes()));
			}
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testWithTrustedCert() throws Exception {
		IuTestLogger.allow("", Level.FINE);
		final var config = config();

		final var mockCert = mock(X509Certificate.class);
		when(config.getTrustedCert()).thenReturn(mockCert);

		final var tempFile = Files.createTempFile("test-trusted-cert-", ".pem");
		try (final var redisClientStaticMock = mockStatic(RedisClient.class);
				final var iuProcessMock = mockStatic(IuProcess.class);
				final var pemEncodedMock = mockStatic(PemEncoded.class)) {

			iuProcessMock.when(IuProcess::createTempFile).thenReturn(tempFile);
			pemEncodedMock.when(() -> PemEncoded.print(any(PrintStream.class), any(X509Certificate.class)))
					.thenAnswer(inv -> null);

			final var mockClient = client(redisClientStaticMock, connection(mock(RedisCommands.class)));

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
		final var config = config();

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final var mockClient = client(redisClientStaticMock, connection(mock(RedisCommands.class)));

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
		final var config = config();

		try (final var redisClientStaticMock = mockStatic(RedisClient.class);
				final var poolSupportMock = mockStatic(ConnectionPoolSupport.class)) {
			final var mockClient = mock(RedisClient.class);
			redisClientStaticMock.when(() -> RedisClient.create(redisURI)).thenReturn(mockClient);

			final var mockPool = mock(GenericObjectPool.class);
			final ArgumentCaptor<GenericObjectPoolConfig<StatefulRedisConnection<String, byte[]>>> poolConfigCaptor = //
					ArgumentCaptor.forClass(GenericObjectPoolConfig.class);
			final ArgumentCaptor<Predicate<StatefulRedisConnection<String, byte[]>>> validatorCaptor = //
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
		final var config = config();
		when(config.getUsername()).thenReturn("username");

		final var error = new RuntimeException();
		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final var mockClient = client(redisClientStaticMock, null);
			doThrow(error).when(mockClient).shutdown();
			LettuceConnection lettuceConnection = new LettuceConnection(config);
			assertSame(error, assertThrows(RuntimeException.class, () -> lettuceConnection.close()));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testListScansOnlyThisStore() {
		IuTestLogger.allow("", Level.FINE);
		final var config = config();

		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final RedisCommands<String, byte[]> mockCommands = mock(RedisCommands.class);
			client(redisClientStaticMock, connection(mockCommands));

			// SCAN promises a key present throughout is returned at least once, not
			// exactly once, so a2V5 arrives on both pages and must be listed once
			final var page1 = mock(KeyScanCursor.class);
			when(page1.getKeys()).thenReturn(List.of(DATA_KEY, "iu:d:{dHdv}"));
			when(page1.isFinished()).thenReturn(false);

			final var page2 = mock(KeyScanCursor.class);
			when(page2.getKeys()).thenReturn(List.of(DATA_KEY, "iu:d:{}"));
			when(page2.isFinished()).thenReturn(true);

			final var scanArgs = ArgumentCaptor.forClass(ScanArgs.class);
			when(mockCommands.scan(scanArgs.capture())).thenReturn(page1);
			when(mockCommands.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(page2);

			try (final var connection = new LettuceConnection(config)) {
				final var listed = new ArrayList<>();
				connection.list().forEach(listed::add);
				assertEquals(3, listed.size());

				final var names = listed.stream().map(e -> ((IuDataStoreEntry) e).getName()).collect(toList());
				assertEquals(List.of("a2V5", "dHdv", ""), names);

				// the write times, and everything else sharing the database, are excluded
				// server-side rather than filtered after transfer; COUNT is sent because
				// Redis otherwise defaults to ten keys per round trip
				assertEquals(command(new ScanArgs().match("iu:d:{*}").limit(1000L)),
						command(scanArgs.getValue()));

				// nothing but the names is read by listing
				verify(mockCommands, never()).get(any());

				final var entry = (IuDataStoreEntry) listed.get(0);
				when(mockCommands.get(DATA_KEY)).thenReturn("value".getBytes());
				when(mockCommands.get(MODIFIED_KEY)).thenReturn(IuText.ascii("1700000000000"));
				assertArrayEquals("value".getBytes(), entry.getData());
				assertEquals(Instant.ofEpochMilli(1700000000000L), entry.getModified());
			}
		}
	}
}
