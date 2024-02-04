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
		final var ref = new IuCachedValue<>(val, Duration.ofMillis(25L), thunk);
		allRefs.add(ref);
		assertEquals(val, ref.get());
		assertTrue(ref.isValid());
		assertTrue(ref.has(val));
		Thread.sleep(26L);
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
