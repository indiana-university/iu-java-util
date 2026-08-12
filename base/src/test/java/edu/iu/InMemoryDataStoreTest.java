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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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
		// Expiry is strictly after the purge time, so sleeping exactly the TTL leaves
		// the entry unexpired whenever the sleep returns on the deadline itself. The
		// margin sits well inside the 1500ms purge timer's first run, keeping the read
		// the thing that removes the entry.
		ds.put(key1, val1, Duration.ofMillis(250L));
		Thread.sleep(500L);
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
		ds.put(key1, val1, Duration.ofMillis(250L));

		final var key2 = new byte[32];
		ThreadLocalRandom.current().nextBytes(key2);
		final var val2 = new byte[32];
		ThreadLocalRandom.current().nextBytes(val2);
		ds.put(key2, val2, Duration.ofSeconds(30L));

		final var timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
		while (IuIterable.stream(ds.list()).count() > 1L && System.nanoTime() < timeout)
			Thread.sleep(25L);

		// Listed before being read: list() does no purging of its own, so an empty
		// expired entry shows the timer removed it. The retained entry covers the
		// timer's non-expired branch in the same deterministic pass.
		assertEquals(1L, IuIterable.stream(ds.list()).count());
		assertArrayEquals(key2, (byte[]) ds.list().iterator().next());
		assertNull(ds.get(key1));
		assertArrayEquals(val2, ds.get(key2));
	}

}
