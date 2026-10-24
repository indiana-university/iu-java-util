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

import java.time.Instant;

/**
 * A single entry in an {@link IuDataStore#list() data store listing}.
 *
 * <p>
 * An entry names what the store held when it was listed, and resolves the rest
 * on demand. A listing is therefore cheap to take and cheap to discard: nothing
 * but the names is read until something asks for more.
 * </p>
 *
 * <p>
 * Because the store keeps changing after the listing is taken, an entry may
 * name data that has since expired or been deleted. Every attribute other than
 * {@link #getName() the name} may resolve to null for that reason, and one that
 * resolved a moment ago may resolve to null now. An implementation
 * <em>may</em> resolve an attribute afresh on each invocation, so a caller that
 * needs a value twice <em>should</em> hold on to it rather than ask again.
 * </p>
 *
 * @see IuDataStore#list()
 */
public interface IuDataStoreEntry {

	/**
	 * Gets the name of the entry.
	 *
	 * <p>
	 * The store's key, Base64 URL-encoded, so that a name is printable, is safe to
	 * log, and identifies the same entry in every implementation.
	 * {@link IuText#base64Url(String)} converts it back to the key
	 * {@link IuDataStore#get(byte[])} and {@link IuDataStore#put(byte[], byte[])}
	 * accept.
	 * </p>
	 *
	 * @return entry name; never null
	 */
	String getName();

	/**
	 * Gets the instant this entry was last written.
	 *
	 * <p>
	 * Resolved when the listing is taken by a store that has the write time on
	 * hand, and on first use by one that must go and read it.
	 * </p>
	 *
	 * @return instant this entry was last written; null if the entry no longer
	 *         exists, or the store cannot determine when it was written
	 * @see IuDataStore#lastModified(byte[])
	 */
	Instant getModified();

	/**
	 * Gets the data stored for this entry.
	 *
	 * <p>
	 * Never resolved by {@link IuDataStore#list()}, which is what allows a listing
	 * to be taken without transferring what the store holds.
	 * </p>
	 *
	 * @return stored data; null if the entry no longer exists
	 * @see IuDataStore#get(byte[])
	 */
	byte[] getData();

}
