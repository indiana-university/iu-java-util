package iu.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.test.VaultProperties;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
@SuppressWarnings("javadoc")
public class JedisCacheIT {

	static JedisPool pool;

	@BeforeAll
	public static void init() {
		String host = VaultProperties.getProperty("spring.redis.host");
		int port = Integer.parseInt(VaultProperties.getProperty("spring.redis.port"));
		String pw = VaultProperties.getProperty("spring.redis.password");
		int maxActive = Integer.parseInt(VaultProperties.getProperty("spring.redis.lettuce.pool.max-active"));
		int minIdle = Integer.parseInt(VaultProperties.getProperty("spring.redis.lettuce.pool.min-idle"));
		boolean ssl = Boolean.parseBoolean(VaultProperties.getProperty("spring.redis.ssl"));
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxIdle(maxActive);
		poolConfig.setMinIdle(minIdle);
		int timeout = 3000;
		pool = new JedisPool(new JedisPoolConfig(), host, port, timeout, pw, ssl);
	}

	@AfterAll
	public static void teardown() {
		pool.close();
	}

	@Test
	public void testJedisPoolConnection() throws NoSuchAlgorithmException, IOException, KeyManagementException {
		try (Jedis jedis = pool.getResource()) {
//			String info = jedis.info();
			List<Object> roles = jedis.role();
			assertEquals(1, pool.getNumActive(), "num active: " + pool.getNumActive() + " not == 1");
			assertEquals(1, pool.getCreatedCount());
			assertTrue(0 <= pool.getNumIdle(), "num idle: " + pool.getNumIdle() + " not >= 0");
//			System.out.println("server info:" + System.lineSeparator() + info);
//			System.out.println("roles: " + roles);
		}

		assertEquals(0, pool.getNumActive(), "num active: " + pool.getNumActive() + " not == 0");
		assertEquals(1, pool.getCreatedCount());
		assertTrue(0 <= pool.getNumIdle(), "num idle: " + pool.getNumIdle() + " not >= 0");
		
		Jedis firstConnection = pool.getResource();
		try (Jedis jedis = pool.getResource()) {
			assertEquals(2, pool.getNumActive(), "num active: " + pool.getNumActive() + " not == 2");
			assertEquals(2, pool.getCreatedCount());
			assertTrue(0 <= pool.getNumIdle(), "num idle: " + pool.getNumIdle() + " not >= 0");
			String testKey = "testkey";
			String testField = "testField";
			String testValue = "testValue";
			// Make sure the key/field doesn't exist before trying to set values
			jedis.hdel(testKey, testField);
			final long mod = jedis.hset(testKey, testField, testValue);
			assertEquals(1L, mod);
			final String value = jedis.hget(testKey, testField);
			assertEquals(testValue, value);
			final long mod2 = jedis.hset(testKey, testField, testValue);
			assertEquals(0L, mod2);
			final String value2 = jedis.hget(testKey, testField);
			assertEquals(testValue, value2);
			final String testValue2 = "testValue2";
			final long mod3 = jedis.hset(testKey, testField, testValue2);
			assertEquals(0L, mod3);
			final String value3 = jedis.hget(testKey, testField);
			assertEquals(testValue2, value3);
		}
		assertEquals(1, pool.getNumActive(), "num active: " + pool.getNumActive() + " not == 1");
		firstConnection.close();
		
		assertEquals(0, pool.getNumActive(), "num active: " + pool.getNumActive() + " not == 0");
		assertEquals(2, pool.getCreatedCount());
		assertTrue(0 <= pool.getNumIdle(), "num idle: " + pool.getNumIdle() + " not >= 0");
	}
}
