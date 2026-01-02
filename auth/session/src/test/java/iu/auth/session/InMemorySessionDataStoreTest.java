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
package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class InMemorySessionDataStoreTest {

	@Test
	void putStoresSessionToken() {
		InMemorySessionStore store = new InMemorySessionStore();
		byte[] key = "testKey".getBytes();
		byte[] data = "testData".getBytes();
		store.put(key, data);
		assertNotNull(store.get(key));
	}

	@Test
	void putRemovesSessionTokenWhenDataIsNull() {
		InMemorySessionStore store = new InMemorySessionStore();
		byte[] key = "testKey".getBytes();
		byte[] data = "testData".getBytes();
		store.put(key, data);
		store.put(key, null);
		assertNull(store.get(key));
	}

	@Test
	void putUpdatesSessionTokenWithNewData() {
		InMemorySessionStore store = new InMemorySessionStore();
		byte[] key = "testKey".getBytes();
		byte[] data1 = "testData1".getBytes();
		byte[] data2 = "testData2".getBytes();
		store.put(key, data1);
		store.put(key, data2);
		assertArrayEquals(data2, store.get(key));
	}

	@Test
	void getReturnsStoredSessionToken() {
		InMemorySessionStore store = new InMemorySessionStore();
		byte[] key = "testKey".getBytes();
		byte[] data = "testData".getBytes();
		store.put(key, data);
		assertArrayEquals(data, store.get(key));
	}

	@Test
	void getReturnsNullForNonExistentKey() {
		InMemorySessionStore store = new InMemorySessionStore();
		byte[] key = "nonExistentKey".getBytes();
		assertNull(store.get(key));
	}

	@Test
	void getRemovesExpiredSessionToken() {
		InMemorySessionStore store = new InMemorySessionStore();
		byte[] key = "testKey".getBytes();
		byte[] data = "testData".getBytes();
		store.put(key, data, Duration.ofMillis(1));
		try {
			Thread.sleep(2);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		store.get(key);
		assertNull(store.get(key));
	}

	@Test
	void putStoresSessionTokenWithTTL() {
		InMemorySessionStore store = new InMemorySessionStore();

		byte[] key = "testKey".getBytes();
		byte[] data = "testData".getBytes();
		Duration ttl = Duration.ofMinutes(10);
		store.put(key, data, ttl);
		assertNotNull(store.get(key));
	}

	@Test
	void listReturnsAllStoredKeys() {
		InMemorySessionStore store = new InMemorySessionStore();
		byte[] key1 = "testKey1".getBytes();
		byte[] key2 = "testKey2".getBytes();
		byte[] data = "testData".getBytes();
		store.put(key1, data);
		store.put(key2, data);
		@SuppressWarnings("unchecked")
		Iterable<String> keys = (Iterable<String>) store.list();
		for (String key : keys) {
			assertNotNull(key);
		}
	}

}
