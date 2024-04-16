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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuCachedValueTest {

	private Set<IuCachedValue<?>> allRefs = new HashSet<>();

	@Test
	public void testGet() throws Throwable {
		final var val = IdGenerator.generateId();
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCachedValue<>(val, Duration.ofMillis(200L), thunk);
		allRefs.add(ref);
		assertEquals(val, ref.get());
		assertTrue(ref.isValid());
		assertTrue(ref.has(val));
		Thread.sleep(225L);
		verify(thunk).run();
		assertNull(ref.get());
		assertFalse(ref.isValid());
		assertFalse(ref.has(val));
	}

	@Test
	public void testNull() throws Throwable {
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCachedValue<>(null, Duration.ofMillis(25L), thunk);
		allRefs.add(ref);
		assertNull(ref.get());
		assertTrue(ref.isValid());
		assertTrue(ref.has(null));
		assertFalse(ref.has(this));
		Thread.sleep(26L);
		assertNull(ref.get());
		assertFalse(ref.isValid());
		assertFalse(ref.has(null));
		verify(thunk).run();
	}

	@Test
	public void testHasCleared() throws Throwable {
		final var f = IuCachedValue.class.getDeclaredField("reference");
		f.setAccessible(true);
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCachedValue<>(null, Duration.ofMillis(500L), thunk);
		allRefs.add(ref);
		assertTrue(ref.has(null));
		verify(thunk, never()).run();
		((Reference<?>) f.get(ref)).clear();
		assertFalse(ref.has(null));
		verify(thunk).run();
	}

	@Test
	public void testThunkError() throws Throwable {
		final var log = LogManager.getLogManager().getLogger("");
		final var restoreHandlers = log.getHandlers();
		try {
			for (final var h : restoreHandlers)
				log.removeHandler(h);
			final var h = mock(Handler.class);
			log.addHandler(h);
			try {
				final var thunk = mock(UnsafeRunnable.class);
				doThrow(Exception.class).when(thunk).run();
				final var ref = new IuCachedValue<>(null, Duration.ofMillis(25L), thunk);
				allRefs.add(ref);
				final var f = IuCachedValue.class.getDeclaredField("reference");
				f.setAccessible(true);
				((Reference<?>) f.get(ref)).clear();
				verify(thunk, never()).run();

				assertNull(ref.get());
				assertFalse(ref.isValid());
				assertFalse(ref.has(null));
				verify(h).publish(
						argThat(a -> a.getLevel() == Level.INFO && a.getThrown().getClass() == Exception.class));
				verify(thunk).run();
			} finally {
				log.removeHandler(h);
			}
		} finally {
			for (final var h : restoreHandlers)
				log.addHandler(h);
		}
	}

	@Test
	public void testSoftRef() throws Throwable {
		var val = IdGenerator.generateId();
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCachedValue<>(val, Duration.ofSeconds(15L), thunk);
		allRefs.add(ref);
		assertEquals(val, ref.get());
		val = null;
		assertThrows(OutOfMemoryError.class, () -> {
			final long freeLong = Runtime.getRuntime().maxMemory();
			final int free = (int) freeLong;
			assertTrue(free > 0 && ((long) free) == freeLong, () -> "max memory must be < 2GB; " + freeLong);
			Array.newInstance(Object.class, free);
		});
		Thread.sleep(550L);
		verify(thunk).run();
	}

	@Test
	public void testEquals() {
		var val = IdGenerator.generateId();
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCachedValue<>(val, Duration.ofMillis(25L), thunk);
		final var ref2 = new IuCachedValue<>(val, Duration.ofMillis(25L), thunk);
		final var ref3 = new IuCachedValue<>(IdGenerator.generateId(), Duration.ofMillis(25L), thunk);
		assertEquals(ref, ref2);
		assertNotEquals(ref, ref3);
		ref.clear();
		assertNotEquals(ref2, ref);
		assertNotEquals(ref, ref2);
		assertNotEquals(ref2, this);
	}

}
