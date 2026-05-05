package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.InMemoryDataStore.Key;

@SuppressWarnings("javadoc")
public class InMemoryDataStoreTest {

	@Test
	void testKey() {
		final var key1 = new byte[32];
		ThreadLocalRandom.current().nextBytes(key1);
		final var dkey1 = new Key(key1);
		assertEquals(dkey1, new Key(key1));
		assertNotEquals(dkey1, this);
		assertEquals(dkey1.hashCode(), new Key(key1).hashCode());

		final var key2 = new byte[32];
		ThreadLocalRandom.current().nextBytes(key2);
		final var dkey2 = new Key(key2);
		assertNotEquals(dkey1, dkey2);
		assertNotEquals(dkey1.hashCode(), key2.hashCode());
	}

	@Test
	void testPutGetList() {
		final var ds = new InMemoryDataStore();
		final var key1 = new byte[32];
		ThreadLocalRandom.current().nextBytes(key1);
		final var val1 = new byte[32];
		ThreadLocalRandom.current().nextBytes(val1);
		ds.put(key1, val1);
		assertArrayEquals(val1, ds.get(key1));
		assertEquals(key1, ds.list().iterator().next());
		ds.put(key1, null);
		assertNull(ds.get(key1));
		assertFalse(ds.list().iterator().hasNext());
	}

	@Test
	void testPurgeBeforeTimer() throws InterruptedException {
		final var ds = new InMemoryDataStore();
		final var key1 = new byte[32];
		ThreadLocalRandom.current().nextBytes(key1);
		final var val1 = new byte[32];
		ThreadLocalRandom.current().nextBytes(val1);
		ds.put(key1, val1, Duration.ofSeconds(1L));
		Thread.sleep(1000L);
		assertNull(ds.get(key1));
		assertFalse(ds.list().iterator().hasNext());
	}

	@Test
	void testPurgeFromTimer() throws InterruptedException {
		final var ds = new InMemoryDataStore();
		final var key1 = new byte[32];
		ThreadLocalRandom.current().nextBytes(key1);
		final var val1 = new byte[32];
		ThreadLocalRandom.current().nextBytes(val1);
		ds.put(key1, val1, Duration.ofSeconds(2L));
		Thread.sleep(4000L);
		assertNull(ds.get(key1));
		assertFalse(ds.list().iterator().hasNext());
	}

}
