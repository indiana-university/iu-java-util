/*
 * Copyright © 2024 Indiana University
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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class CompactEncodedTest {

	@Test
	void testCompactEncoded() {
		final var s1 = IdGenerator.generateId();
		final var s2 = IdGenerator.generateId();
		final var s3 = IdGenerator.generateId();
		final var i = s1 + ".." + s2 + "." + s3;

		final var compact = CompactEncoded.compact(i);
		assertTrue(compact.hasNext());
		assertEquals(s1, compact.next());
		assertTrue(compact.hasNext());
		assertNull(compact.next());
		assertTrue(compact.hasNext());
		assertEquals(s2, compact.next());
		assertTrue(compact.hasNext());
		assertEquals(s3, compact.next());
		assertFalse(compact.hasNext());
		assertThrows(NoSuchElementException.class, () -> compact.next());
	}

	@Test
	public void testProtectedHeader() {
		final var header = IuJson.object().add(IdGenerator.generateId(), IdGenerator.generateId()).build();
		assertEquals(header, CompactEncoded.getProtectedHeader(IuText.base64Url(IuText.utf8(header.toString())) + "."));
	}

	@Test
	public void testProtectedHeaderEmpty() {
		final var error = assertThrows(IllegalArgumentException.class,
				() -> CompactEncoded.getProtectedHeader(IdGenerator.generateId()));
		assertEquals("Invalid compact serialized data", error.getMessage());
	}

}
