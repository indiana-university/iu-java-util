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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuFixedLimitOutputBufferTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testNothingToDo() {
		final var buf = new IuFixedLimitOutputBuffer(32);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn((byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertEquals(32, buf.remaining());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExactFit() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(data.length);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn(data, (byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(data, out.toByteArray());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOverflow() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(8, data.length);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn(data, (byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(Arrays.copyOf(data, 24), out.toByteArray());
		assertEquals(0, buf.remaining());

		out.reset();
		buf.resetCount();
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(Arrays.copyOfRange(data, 24, 32), out.toByteArray());
		assertEquals(24, buf.remaining());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testUndersized() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(8, 0, data.length);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn(data, (byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(data, out.toByteArray());
	}

	@Test
	public void testFillAvailable() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(8, 0, data.length);
		buf.fill(() -> data);
		assertEquals(0, buf.available());
		assertDoesNotThrow(() -> buf.fill(() -> null));
	}

	@Test
	public void testBigRemaining() {
		final var buf = new IuFixedLimitOutputBuffer(((long) Integer.MAX_VALUE) + 1);
		assertEquals(Integer.MAX_VALUE, buf.remaining());
	}

	@Test
	public void testBulk() {
		final var control = new ByteArrayOutputStream();
		final Queue<byte[]> queue = new ConcurrentLinkedDeque<>();
		final var feed = new Thread() {
			volatile boolean active = true;

			@Override
			public void run() {
				final var rand = new Random();
				while (active)
					synchronized (this) {
						IuException.unchecked(() -> {
							final var length = rand.nextInt(16382) + 2;
							final var data = new byte[length];
							rand.nextBytes(data);
							control.write(data);
							queue.offer(data);
							this.wait(100L);
						});
					}
			}
		};
		feed.start();

		final var out = new ByteArrayOutputStream();
		final var buf = new IuFixedLimitOutputBuffer(4096);
		for (var i = 0; i < 20; i++) {
			while (buf.remaining() > 0) {
				assertDoesNotThrow(() -> buf.write(queue::poll, out));
				IuException.unchecked(() -> Thread.sleep(50L));
			}
			assertEquals(4096, out.size());
			assertArrayEquals(Arrays.copyOfRange(control.toByteArray(), i * 4096, (i + 1) * 4096), out.toByteArray());

			out.reset();
			buf.resetCount();
		}

		synchronized (feed) {
			feed.active = false;
			feed.notifyAll();
		}
	}

}
