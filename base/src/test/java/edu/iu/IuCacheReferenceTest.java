package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuCacheReferenceTest {
	
	private Set<IuCacheReference<?>> allRefs = new HashSet<>();

	@Test
	public void testGet() throws Throwable {
		final var val = IdGenerator.generateId();
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCacheReference<>(val, Instant.now().plusMillis(25L), thunk);
		allRefs.add(ref);
		assertEquals(val, ref.get());
		Thread.sleep(26L);
		assertNull(ref.get());
		verify(thunk).run();
	}

	@Test
	public void testNull() throws Throwable {
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCacheReference<>(null, Instant.now().plusMillis(25L), thunk);
		allRefs.add(ref);
		assertNull(ref.get());
		Thread.sleep(26L);
		assertNull(ref.get());
		verify(thunk).run();
	}

	@Test
	public void testThunkError() throws Throwable {
		final var thunk = mock(UnsafeRunnable.class);
		doThrow(Exception.class).when(thunk).run();
		final var ref = new IuCacheReference<>(null, Instant.now().plusMillis(25L), thunk);
		allRefs.add(ref);
		ref.clear();
		assertNull(ref.get());
		verify(thunk).run();
	}

	@Test
	public void testSoftRef() throws Throwable {
		var val = IdGenerator.generateId();
		final var thunk = mock(UnsafeRunnable.class);
		final var ref = new IuCacheReference<>(val, Instant.now().plusSeconds(15L), thunk);
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

}
