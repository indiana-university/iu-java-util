package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuUtilityTaskControllerTest {

	@Test
	public void testTask() throws Throwable {
		assertEquals("foo", new IuUtilityTaskController<>(() -> "foo", Instant.now().plusMillis(100L)).get());
	}

	@Test
	public void testGetBefore() throws Throwable {
		assertEquals("foo", IuUtilityTaskController.getBefore(() -> "foo", Instant.now().plusMillis(100L)));
	}

	@Test
	public void testDoBefore() throws Throwable {
		class Box {
			boolean done;
		}
		final var box = new Box();
		IuUtilityTaskController.doBefore(() -> {
			box.done = true;
		}, Instant.now().plusMillis(100L));
		assertTrue(box.done);
	}

	@Test
	public void testError() throws Throwable {
		final var e = new Exception();
		assertSame(e, assertThrows(Exception.class, () -> IuUtilityTaskController.getBefore(() -> {
			throw e;
		}, Instant.now().plusMillis(100L))));
	}

	@Test
	public void testTimeout() throws Throwable {
		final var t = System.currentTimeMillis();
		assertThrows(TimeoutException.class, () -> IuUtilityTaskController.doBefore(() -> {
			Thread.sleep(200L);
		}, Instant.now().plusMillis(100L)));
		assertTrue(System.currentTimeMillis() - t < 125L, Long.toString(t - System.currentTimeMillis()));
	}

}
