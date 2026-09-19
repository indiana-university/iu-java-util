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
package iu.oidc.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.IuDataStore;
import edu.iu.IuDataStoreEntry;
import edu.iu.IuIterable;
import edu.iu.IuText;

/**
 * Stands in for whatever store a deployment binds.
 */
@SuppressWarnings("javadoc")
class MemoryDataStore implements IuDataStore {

	/** Readable by the tests, which assert on what was filed and under what key. */
	final Map<String, byte[]> entries = new LinkedHashMap<>();

	private final Map<String, Instant> modified = new LinkedHashMap<>();

	/** Listing entry over the fixture's own maps. */
	private final class Entry implements IuDataStoreEntry {
		private final String name;

		private Entry(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Instant getModified() {
			return modified.get(name);
		}

		@Override
		public byte[] getData() {
			return entries.get(name);
		}
	}

	@Override
	public Iterable<IuDataStoreEntry> list() {
		return IuIterable.map(entries.keySet(), Entry::new);
	}

	@Override
	public byte[] get(byte[] key) {
		return entries.get(IuText.base64Url(key));
	}

	@Override
	public Instant lastModified(byte[] key) {
		return modified.get(IuText.base64Url(key));
	}

	@Override
	public void put(byte[] key, byte[] data) {
		if (data == null) {
			entries.remove(IuText.base64Url(key));
			modified.remove(IuText.base64Url(key));
		} else {
			entries.put(IuText.base64Url(key), data);
			modified.put(IuText.base64Url(key), Instant.now());
		}
	}

	@Override
	public void put(byte[] key, byte[] value, Duration ttl) {
		put(key, value);
	}
}
