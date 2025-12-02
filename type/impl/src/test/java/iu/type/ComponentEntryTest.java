/*
 * Copyright © 2025 Indiana University
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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeConsumer;

@SuppressWarnings("javadoc")
public class ComponentEntryTest extends IuTypeTestCase {

	@Test
	public void testWrapsName() {
		try (var componentEntry = new ComponentEntry("entry name", null)) {
			assertEquals("entry name", componentEntry.name());
		}
	}

	@Test
	public void testImplementsToString() {
		var data = new byte[32768];
		ThreadLocalRandom.current().nextBytes(data);
		try (var componentEntry = new ComponentEntry("foo", new ByteArrayInputStream(data))) {
			assertEquals("ComponentEntry [name=foo, read=false, closed=false]", componentEntry.toString());
			assertArrayEquals(data, componentEntry.data());
			assertEquals("ComponentEntry [name=foo, read=true, data=32768B, closed=false]", componentEntry.toString());
		}
	}

	@Test
	public void testReadsData() throws IOException {
		var data = new byte[32768];
		ThreadLocalRandom.current().nextBytes(data);
		try (var componentEntry = new ComponentEntry(null, new ByteArrayInputStream(data))) {
			assertArrayEquals(data, componentEntry.data());
			assertArrayEquals(data, componentEntry.data());
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testOnlyReadsOnce() throws Throwable {
		var in = mock(InputStream.class);
		var inputStreamConsumer = mock(UnsafeConsumer.class);
		try (var componentEntry = new ComponentEntry(null, in)) {
			componentEntry.read(inputStreamConsumer);
			assertEquals("already read",
					assertThrows(IllegalStateException.class, () -> componentEntry.read(inputStreamConsumer))
							.getMessage());
			assertEquals("already read", assertThrows(IllegalStateException.class, componentEntry::data).getMessage());
		}
		verify(inputStreamConsumer, times(1)).accept(in);
	}

	@Test
	public void testHandlesIOException() throws IOException {
		var ioException = new IOException();
		try (var componentEntry = new ComponentEntry(null, new InputStream() {
			@Override
			public int read() throws IOException {
				throw ioException;
			}
		})) {
			assertSame(ioException, assertThrows(IllegalStateException.class, componentEntry::data).getCause());
		}
	}

	@Test
	public void testClose() {
		var componentEntry = new ComponentEntry(null, null);
		componentEntry.close();
		assertEquals("closed", assertThrows(IllegalStateException.class, componentEntry::name).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> componentEntry.read(null)).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, componentEntry::data).getMessage());
	}
}
