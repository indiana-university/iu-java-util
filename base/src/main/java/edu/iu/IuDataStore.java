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

import java.time.Duration;
import java.time.Instant;

/**
 * Backing data storage interface for use in implementing data management
 * resources.
 */
public interface IuDataStore {

	/**
	 * Returns a listing of all entries in the data store, without their contents.
	 *
	 * <p>
	 * A listing is a point-in-time approximation, not a transaction: entries added
	 * or removed while it is being taken may or may not appear, and an entry that
	 * appears may already be gone by the time it is read. Callers <em>should</em>
	 * treat it as a diagnostic view of the store rather than as a set they can act
	 * on element by element.
	 * </p>
	 *
	 * <p>
	 * Not every backing store can enumerate itself, and one that shares its
	 * namespace with other tenants may not be able to tell its own entries from
	 * theirs. An implementation that cannot produce a listing it can stand behind
	 * throws {@link UnsupportedOperationException} rather than an approximation of
	 * one.
	 * </p>
	 *
	 * @return data entry listing
	 * @throws UnsupportedOperationException if the store cannot enumerate its
	 *                                       entries
	 */
	Iterable<IuDataStoreEntry> list();

	/**
	 * Get the binary value representation from Redis stored for the given key.
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist.
	 */
	byte[] get(byte[] key);

	/**
	 * Gets the instant the data stored for a given key was last written.
	 *
	 * <p>
	 * Intended as a freshness check that does not transfer the value, so a caller
	 * holding a copy can decide whether to read it again. Implementations
	 * <em>should</em> resolve it without reading the value itself.
	 * </p>
	 *
	 * <p>
	 * Not every backing store records a write time, and one that does may lose it
	 * separately from the value it describes. A null return therefore means "no
	 * write time is available", which does <em>not</em> imply the key is absent:
	 * verify with {@link #get(byte[])} rather than inferring existence from this
	 * method. An implementation that never records one returns null for every key.
	 * </p>
	 *
	 * @param key data key. Must not be {@literal null}.
	 * @return instant the value stored for {@code key} was last written;
	 *         {@literal null} if the key does not exist or the store cannot
	 *         determine when it was written
	 */
	Instant lastModified(byte[] key);

	/**
	 * Puts or deletes data represented by a given key.
	 * 
	 * @param key  data key
	 * @param data data to assign to the key, replaces existing data. May be null to
	 *             delete existing data.
	 */
	void put(byte[] key, byte[] data);
	
	/**
	 * Write the given key/value pair to Redis and set the expiration time if defined.
	 *
	* @param key key for the cache entry. Must not be {@literal null}.
	 * @param value value stored for the key. Must not be {@literal null}.
	 * @param ttl optional expiration time. Can be {@literal null}.
	 */
	void put(byte[] key, byte[] value, Duration ttl);
	
}
