package iu.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.test.IuTestLogger;
import edu.iu.test.VaultProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
@SuppressWarnings("javadoc")
public class CacheIT {

	@Test
	public void testRedisConnection() {
		IuTestLogger.allow("io.netty", Level.FINE);
		IuTestLogger.allow("io.lettuce", Level.FINE);
		IuTestLogger.allow("io.netty", Level.FINEST);
		IuTestLogger.allow("io.lettuce", Level.FINEST);
		String host = VaultProperties.getProperty("spring.redis.host");
		String port = VaultProperties.getProperty("spring.redis.port");
		String pw = VaultProperties.getProperty("spring.redis.password");

		RedisURI redisUri = RedisURI.Builder.redis(host, Integer.parseInt(port)) //
				.withPassword(pw.toCharArray()) //
				.withSsl(true) //
				.build();
		RedisClient redisClient = RedisClient.create(redisUri);
		StatefulRedisConnection<String, String> connection = redisClient.connect();
		RedisCommands<String, String> syncCommands = connection.sync();

		String testKey = "testkey";
		String testField = "testField";
		String testValue = "testValue";
		// Make sure the key/field doesn't exist before trying to set values
		syncCommands.hdel(testKey, testField);
		final boolean mod = syncCommands.hset(testKey, testField, testValue);
		assertEquals(true, mod);
		final String value = syncCommands.hget(testKey, testField);
		assertEquals(testValue, value);
		final boolean mod2 = syncCommands.hset(testKey, testField, testValue);
		assertEquals(false, mod2);
		final String value2 = syncCommands.hget(testKey, testField);
		assertEquals(testValue, value2);
		final String testValue2 = "testValue2";
		final boolean mod3 = syncCommands.hset(testKey, testField, testValue2);
		assertEquals(false, mod3);
		final String value3 = syncCommands.hget(testKey, testField);
		assertEquals(testValue2, value3);
		
		connection.close();
		redisClient.shutdown();
		assertFalse(connection.isOpen());
	}
}
