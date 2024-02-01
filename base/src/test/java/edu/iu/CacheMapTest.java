package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.ref.SoftReference;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class CacheMapTest {

	private CacheMap<String, String> cache;
	private Map<String, SoftReference<String>> internal;

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setup() throws Exception {
		cache = new CacheMap<>(Duration.ofMillis(100L), Duration.ofMillis(50L));
		final var f = CacheMap.class.getDeclaredField("cache");
		f.setAccessible(true);
		internal = (Map<String, SoftReference<String>>) f.get(cache);
	}

	@Test
	public void testCacheExpires() throws InterruptedException {
		assertNull(cache.get("foo"));
		cache.put("foo", "bar");
		cache.put("bar", "baz");
		assertEquals("bar", cache.get("foo"));
		assertEquals("bar", cache.get("foo"));
		Thread.sleep(101L);
		assertNull(cache.get("foo"));
		assertNull(cache.get("bar"));
	}

	@Test
	public void testCacheClears() throws InterruptedException {
		assertNull(cache.get("foo"));
		cache.put("foo", "bar");
		cache.put("bar", "baz");
		assertEquals("bar", cache.get("foo"));
		assertEquals("bar", cache.get("foo"));
		internal.get("foo").clear();
		Thread.sleep(51L);
		assertNull(cache.get("foo"));
		assertEquals("baz", cache.get("bar"));
	}

}
