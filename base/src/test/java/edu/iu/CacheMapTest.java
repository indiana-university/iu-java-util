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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class CacheMapTest {

	private IuCacheMap<String, String> cache;
	private Map<String, IuCachedValue<String>> internal;

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setup() throws Exception {
		setupCache(Duration.ofMillis(250L));
	}

	@SuppressWarnings("unchecked")
	private void setupCache(Duration timeToLive) throws Exception {
		cache = new IuCacheMap<>(timeToLive);
		final var f = IuCacheMap.class.getDeclaredField("cache");
		f.setAccessible(true);
		internal = (Map<String, IuCachedValue<String>>) f.get(cache);
	}

	@Test
	public void testCacheExpires() throws InterruptedException {
		assertNull(cache.get("foo"));
		cache.put("foo", "bar");
		cache.put("bar", "baz");
		assertEquals("bar", cache.get("foo"));
		assertEquals("bar", cache.get("foo"));
		Thread.sleep(251L);
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
		assertNull(cache.get("foo"));
		assertEquals("baz", cache.get("bar"));
	}

	@Test
	public void testKeySet() throws InterruptedException {
		assertNull(cache.get("foo"));
		cache.put("foo", "bar");
		cache.put("bar", "baz");
		cache.put("baz", "foo");
		assertTrue(cache.containsKey("foo"));
		assertTrue(cache.containsKey("bar"));
		assertTrue(cache.containsKey("baz"));

		final var keySet = cache.keySet();
		assertSame(keySet, cache.keySet());
		assertThrows(UnsupportedOperationException.class, () -> keySet.add(""));
		assertThrows(UnsupportedOperationException.class, () -> keySet.addAll(Collections.emptyList()));

		assertTrue(keySet.contains("foo"));
		assertTrue(keySet.contains("bar"));
		assertTrue(keySet.contains("baz"));
		assertTrue(keySet.containsAll(Set.of("foo", "bar")));
		assertTrue(Arrays.asList(keySet.toArray()).containsAll(Set.of("foo", "bar", "baz")));
		assertEquals(3L, keySet.stream().count());
		assertEquals(3, keySet.size());
		assertFalse(keySet.isEmpty());

		internal.get("foo").clear();
		assertFalse(keySet.contains("foo"));
		assertTrue(keySet.contains("bar"));
		assertTrue(keySet.contains("baz"));
		assertTrue(keySet.containsAll(Set.of("bar", "baz")));
		assertEquals(2, keySet.size());
		assertFalse(keySet.isEmpty());

		assertFalse(keySet.remove("foo"));
		assertTrue(keySet.remove("baz"));
		assertFalse(keySet.contains("foo"));
		assertTrue(keySet.contains("bar"));
		assertFalse(keySet.contains("baz"));
		assertTrue(keySet.containsAll(Set.of("bar")));
		assertEquals(1, keySet.size());
		assertFalse(keySet.isEmpty());

		keySet.clear();
		assertFalse(cache.containsKey("foo"));
		assertFalse(cache.containsKey("bar"));
		assertFalse(cache.containsKey("baz"));
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testEntrySet() throws Throwable {
		// This test exercises iterator mutation and cleared-reference behavior, not
		// expiration. A short TTL made the remaining entries disappear mid-iteration
		// on slower build agents.
		setupCache(Duration.ofSeconds(30L));
		assertNull(cache.get("foo"));
		cache.put("foo", "bar");
		cache.put("bar", "baz");
		cache.put("baz", "foo");
		assertTrue(cache.containsKey("foo"));
		assertTrue(cache.containsKey("bar"));
		assertTrue(cache.containsKey("baz"));

		final var entrySet = cache.entrySet();
		assertSame(entrySet, cache.entrySet());
		final var i = entrySet.iterator();
		final var e = i.next();
		assertTrue(entrySet.contains(e));
		assertFalse(entrySet.contains(this));
		assertFalse(entrySet.contains(new Entry<>() {
			@Override
			public Object getKey() {
				return "foo";
			}

			@Override
			public Object getValue() {
				return "not foo";
			}

			@Override
			public Object setValue(Object value) {
				throw new UnsupportedOperationException();
			}
		}));
		cache.remove(e.getKey());
		assertFalse(entrySet.contains(e));

		final var k2 = i.next().getKey();
		assertDoesNotThrow(() -> i.next());
		assertThrows(NoSuchElementException.class, () -> i.next());
		final var f = IuCachedValue.class.getDeclaredField("reference");
		f.setAccessible(true);
		((Reference<?>) f.get(internal.get(k2))).clear();

		final var i2 = entrySet.iterator();
		assertDoesNotThrow(() -> i2.next());
		i2.remove();
		assertTrue(cache.isEmpty());
	}

	@Test
	public void testEntrySetMods() throws InterruptedException {
		for (int i = 0; i < 100; i++)
			cache.put(IdGenerator.generateId(), IdGenerator.generateId());
		cache.entrySet().parallelStream().forEach(e -> {
			final var o = e.getValue();
			final var oh = e.hashCode();
			assertEquals(o, cache.get(e.getKey()));
			final var n = IdGenerator.generateId();
			assertEquals(o, e.setValue(n));
			assertNotEquals(oh, e.hashCode());
			assertEquals(n, cache.get(e.getKey()));
			assertEquals(e, e);
			assertNotEquals(e, o);
			assertEquals(e, new Entry<>() {
				@Override
				public Object getKey() {
					return e.getKey();
				}

				@Override
				public Object getValue() {
					return n;
				}

				@Override
				public Object setValue(Object value) {
					throw new UnsupportedOperationException();
				}
			});
			assertNotEquals(e, new Entry<>() {
				@Override
				public Object getKey() {
					return e.getKey();
				}

				@Override
				public Object getValue() {
					return o;
				}

				@Override
				public Object setValue(Object value) {
					throw new UnsupportedOperationException();
				}
			});
			assertNotEquals(e, new Entry<>() {
				@Override
				public Object getKey() {
					return o;
				}

				@Override
				public Object getValue() {
					return n;
				}

				@Override
				public Object setValue(Object value) {
					throw new UnsupportedOperationException();
				}
			});
		});
		Thread.sleep(251L);
		assertTrue(cache.isEmpty());
	}

	@Test
	public void testValues() throws InterruptedException {
		final var populated = new CountDownLatch(1);
		final var stop = new AtomicBoolean();
		final var writer = new Thread(() -> {
			while (!stop.get()) {
				cache.put(IdGenerator.generateId(), "foo");
				cache.put(IdGenerator.generateId(), "bar");
				cache.put(IdGenerator.generateId(), "baz");
				populated.countDown();
			}
		});

		final var values = cache.values();
		assertSame(values, cache.values());

		writer.start();
		try {
			// Waiting for the writer's first pass, rather than guessing how long one
			// takes, is what keeps this from failing on a loaded build agent.
			assertTrue(populated.await(10L, TimeUnit.SECONDS));
			assertTrue(cache.containsValue("foo"));
			assertTrue(values.retainAll(Set.of("bar", "baz")));
		} finally {
			// Joined before the test ends: the writer reads the cache field on every
			// pass, so one left running would keep writing into the next test's cache.
			stop.set(true);
			writer.join();
		}

		// Mutate only after the writer has stopped, so new entries cannot race the
		// collection operation. Remove the value being asserted instead of waiting
		// for its cache entry to expire as the former loop implicitly did.
		while (values.contains("bar"))
			assertTrue(values.removeAll(Set.of("bar")));
		assertFalse(values.parallelStream().anyMatch(a -> "bar".equals(a)));
		values.clear();
		assertTrue(values.isEmpty());
	}

	@Test
	public void testEmptySplit() throws InterruptedException {
		assertNull(cache.values().spliterator().trySplit());
	}

	@Test
	public void testSpliteratorSkipsClearedValue() throws Exception {
		cache.put("foo", "bar");
		final var reference = IuCachedValue.class.getDeclaredField("reference");
		reference.setAccessible(true);
		((Reference<?>) reference.get(internal.get("foo"))).clear();

		assertFalse(cache.values().spliterator().tryAdvance(value -> {
		}));
	}

	@Test
	public void testRemoveNothing() throws InterruptedException {
		assertFalse(cache.values().remove("foo"));
		assertNull(cache.remove("foo"));
		assertFalse(cache.keySet().removeAll(Set.of("foo")));
	}

	@Test
	public void testPutRemoveAll() {
		cache.putAll(Map.of("foo", "bar", "bar", "baz", "baz", "foo"));
		assertTrue(cache.containsKey("foo"));
		assertTrue(cache.containsKey("bar"));
		assertTrue(cache.containsKey("baz"));
		assertEquals("bar", cache.put("foo", "not bar"));
		assertEquals("baz", cache.remove("bar"));
		assertEquals("foo", cache.remove("baz"));
		assertEquals("not bar", cache.get("foo"));
	}

	@Test
	public void testSetCacheTimeToLiveValidates() {
		assertEquals("Missing cache TTL",
				assertThrows(NullPointerException.class, () -> cache.setCacheTimeToLive(null)).getMessage());
		assertEquals("Cache TTL must be positive",
				assertThrows(IllegalArgumentException.class, () -> cache.setCacheTimeToLive(Duration.ZERO))
						.getMessage());
		assertEquals("Cache TTL must be positive",
				assertThrows(IllegalArgumentException.class, () -> cache.setCacheTimeToLive(Duration.ofMillis(-1L)))
						.getMessage());
	}

	@Test
	public void testSetCacheTimeToLiveAppliesToNewEntriesOnly() throws Exception {
		setupCache(Duration.ofSeconds(30L));
		cache.put("existing", "value");

		// shortening the TTL leaves the entry already stored untouched
		cache.setCacheTimeToLive(Duration.ofMillis(50L));
		Thread.sleep(75L);
		assertEquals("value", cache.get("existing"));

		// an entry stored after the change expires on the new schedule
		cache.put("new", "value");
		Thread.sleep(75L);
		assertNull(cache.get("new"));
		assertEquals("value", cache.get("existing"));

		// replacing the existing entry moves it onto the new schedule
		cache.put("existing", "replaced");
		Thread.sleep(75L);
		assertNull(cache.get("existing"));
	}

	@Test
	public void testComputeIfAbsent() {
		assertEquals("Missing mappingFunction",
				assertThrows(NullPointerException.class, () -> cache.computeIfAbsent("foo", null)).getMessage());

		final var computed = new AtomicInteger();
		assertEquals("bar", cache.computeIfAbsent("foo", k -> {
			computed.incrementAndGet();
			return k + "->bar";
		}).substring(5));
		assertEquals(1, computed.get());

		// an existing value is returned without invoking the mapping function
		assertEquals("foo->bar", cache.computeIfAbsent("foo", k -> {
			throw new AssertionError("must not be invoked");
		}));
		assertEquals(1, computed.get());

		// a null result stores nothing
		assertNull(cache.computeIfAbsent("bar", k -> null));
		assertFalse(cache.containsKey("bar"));
	}

	@Test
	public void testComputeIfAbsentExpired() throws Exception {
		setupCache(Duration.ofMillis(50L));
		assertEquals("first", cache.computeIfAbsent("foo", k -> "first"));

		Thread.sleep(75L);

		// the expired value is replaced rather than returned
		assertEquals("second", cache.computeIfAbsent("foo", k -> "second"));
		assertEquals("second", cache.get("foo"));
	}

	@Test
	public void testComputeIfAbsentIsAtomic() throws Exception {
		final var computed = new AtomicInteger();
		final var start = new CountDownLatch(1);
		final var results = Collections.synchronizedList(new ArrayList<String>());
		final var threads = new ArrayList<Thread>();
		for (var i = 0; i < 8; i++) {
			final var thread = new Thread(() -> {
				assertDoesNotThrow(() -> start.await(5L, TimeUnit.SECONDS));
				results.add(cache.computeIfAbsent("foo", k -> "value/" + computed.incrementAndGet()));
			});
			threads.add(thread);
			thread.start();
		}

		start.countDown();
		for (final var thread : threads)
			thread.join(5000L);

		assertEquals(1, computed.get(), "concurrent callers must share one computation");
		assertEquals(8, results.size());
		results.forEach(result -> assertEquals("value/1", result));
	}

}
