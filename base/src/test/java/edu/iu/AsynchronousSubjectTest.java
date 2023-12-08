package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class AsynchronousSubjectTest {

	@Test
	public void testBasic() {
		Queue<String> q = new ArrayDeque<>();
		q.offer("foo");
		q.offer("bar");

		try (final var subject = new IuAsynchronousSubject<>(q::spliterator)) {
			final var subscriber = subject.subscribe();
			final var i = subscriber.iterator();
			assertTrue(i.hasNext());
			assertEquals("foo", i.next());
			assertTrue(i.hasNext());
			assertEquals("bar", i.next());

			new Thread(() -> {
				try {
					Thread.sleep(50L);
					subject.accept("baz");
					subject.accept("bazz");
				} catch (Throwable e) {
					subject.error(e);
				}
			}).start();

			assertTrue(i.hasNext());
			assertEquals("baz", i.next());
			assertTrue(i.hasNext());
			assertEquals("bazz", i.next());
		}
	}

	@Test
	public void testAppendsEmpty() {
		try (final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator)) {
			final var subscriber = subject.subscribe();
			final var i = subscriber.iterator();
			subject.accept("foo");
			assertTrue(i.hasNext());
			assertEquals("foo", i.next());
		}
	}

	@Test
	public void testCloseUnused() {
		final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator);
		final var subscriber = subject.subscribe();
		subject.close();
		assertFalse(subscriber.iterator().hasNext());
	}

	@Test
	public void testClosesOnError() {
		try (final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator)) {
			final var subscriber = subject.subscribe();
			final var e = new RuntimeException();
			subject.error(e);
			assertSame(e, assertThrows(RuntimeException.class, () -> subscriber.iterator().hasNext()));
		}
	}

	@Test
	public void testClose() {
		try (final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator)) {
			subject.close();
			assertEquals("closed", assertThrows(IllegalStateException.class, () -> subject.subscribe()).getMessage());
			assertEquals("closed", assertThrows(IllegalStateException.class, () -> subject.accept("foo")).getMessage());
		} // tests double close
	}

	@Test
	public void testCloseThrowsErrorFromPipe() {
		final var e = new RuntimeException();
		try (final var mockPipe = mockConstruction(IuAsynchronousPipe.class, (p, context) -> {
			doThrow(e).when(p).close();
		})) {
			final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator);
			subject.subscribe();
			assertSame(e, assertThrows(RuntimeException.class, subject::close));
		}
	}

	@Test
	public void testTwoSubscribers() throws Throwable {
		final Set<String> control = Collections.synchronizedSet(new HashSet<>());
		final var q = new ConcurrentLinkedQueue<String>();
		try (final var subject = new IuAsynchronousSubject<>(q::spliterator)) {
			new Thread(() -> {
				try {
					for (var i = 0; i < 100; i++) {
						final var id = IdGenerator.generateId();
						control.add(id);
						q.add(id);
						subject.accept(id);
					}
					subject.close();
				} catch (Throwable e) {
					subject.error(e);
				}
			}).start();

			Thread.sleep(20L);
			final var subscriber = subject.subscribe().parallel();
			final var collected = subscriber.collect(Collectors.toSet());
			assertEquals(control.size(), collected.size());
			assertTrue(collected.containsAll(control));
		}
	}

}
