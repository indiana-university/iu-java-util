package iu.redis.lettuce;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
			assertDoesNotThrow(
					() -> lettuceConnection.put("key".getBytes(), "value".getBytes()));
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
			assertThrows(IllegalStateException.class,
					() -> lettuceConnection.get("key".getBytes()));
			assertThrows(IllegalStateException.class,
					() -> lettuceConnection.put("key".getBytes(), "value".getBytes(), null));
			assertDoesNotThrow(() -> lettuceConnection.close());
		}
	}

	@Test
	void closeClosesGenericPool() throws Exception {
		final var config = mock(IuRedisConfiguration.class);
		LettuceConnection connection = new LettuceConnection(config);
		connection.close();

	}



}
