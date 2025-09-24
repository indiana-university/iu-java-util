package iu.redis.spi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockConstruction;

import org.junit.jupiter.api.Test;

import edu.iu.redis.IuRedisConfiguration;
import iu.redis.lettuce.LettuceConnection;

@SuppressWarnings("javadoc")
public class RedisSpiTest {

	@Test
	public void testCreateConnection() {
		final var spi = new RedisSpi();
		final var redisConfig = new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return "localhost";
			}

			@Override
			public String getPort() {
				return "6379";
			}

			@Override
			public String getPassword() {
				return "password";
			}

			@Override
			public String getUsername() {
				return null;
			}
		};
		try (final var mock = mockConstruction(LettuceConnection.class)) {
			final var redis = spi.createConnection(redisConfig);
			assertSame(redis, mock.constructed().get(0));
		}
	}

	@Test
	public void testInit() {
		final var spi = new RedisSpi();
		assertDoesNotThrow(() -> {
			final var m = RedisSpi.class.getDeclaredMethod("init");
			m.setAccessible(true);
			m.invoke(spi);
		});
	}
}
