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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class AsynchronousSubjectTest {

	@Test
	public void testBasic() {
		Queue<String> q = new ArrayDeque<>();
		q.offer("a");
		q.offer("b");
		q.offer("c");

		try (final var subject = new IuAsynchronousSubject<>(q::spliterator)) {
			final var subscriber = subject.subscribe();

			final var sp = subscriber.stream().spliterator();
			final var sp1 = sp.trySplit();
			assertTrue(sp1.tryAdvance(a -> assertEquals("a", a)));
			assertFalse(sp1.tryAdvance(a -> fail()));
			final var sp2 = sp.trySplit();
			assertTrue(sp.tryAdvance(a -> assertEquals("c", a)));

			new Thread(() -> {
				try {
					Thread.sleep(50L);
					subject.accept("d");
					assertTrue(sp2.tryAdvance(a -> assertEquals("b", a)));
					assertFalse(sp2.tryAdvance(a -> fail()));

					Thread.sleep(50L);
					subject.accept("e");
					Thread.sleep(50L);
					subject.accept("f");

				} catch (Throwable e) {
					subject.error(e);
				}
			}).start();

			assertTrue(sp.tryAdvance(a -> assertEquals("d", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("e", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("f", a)));
		}
	}

	@Test
	public void testCanAdvanceAndAccept() {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a")::spliterator)) {
			final var subscriber = subject.subscribe();
			final var i = subscriber.stream().iterator();
			final BooleanSupplier canAdvance = () -> (boolean) IuException.uncheckedInvocation(() -> {
				final var m = subscriber.getClass().getDeclaredMethod("canAdvance");
				m.setAccessible(true);
				return m.invoke(subscriber);
			});
			final BooleanSupplier canAccept = () -> (boolean) IuException.uncheckedInvocation(() -> {
				final var m = subscriber.getClass().getDeclaredMethod("canAccept");
				m.setAccessible(true);
				return m.invoke(subscriber);
			});

			assertTrue(canAdvance.getAsBoolean());
			assertTrue(canAccept.getAsBoolean());
			subject.accept("b");
			assertEquals("a", i.next());
			assertTrue(canAdvance.getAsBoolean());
			assertTrue(canAccept.getAsBoolean());
			assertEquals("b", i.next());
			assertFalse(canAdvance.getAsBoolean());
			assertFalse(canAccept.getAsBoolean());
		}
	}

	@Test
	public void testIsClosed() {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a")::spliterator)) {
			final var subscriber = subject.subscribe();
			assertFalse(subscriber.isClosed());
			subscriber.close();
			assertTrue(subscriber.isClosed());
		}
	}

	@Test
	public void testIsClosedAfterError() {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a")::spliterator)) {
			final var subscriber = subject.subscribe();
			assertFalse(subscriber.isClosed());
			subject.error(new Throwable());
			assertTrue(subscriber.isClosed());
		}
	}

	@SuppressWarnings({ "unchecked" })
	@Test
	public void testForEachRemaining() {
		Queue<String> q = new ArrayDeque<>();
		q.offer("a");
		q.offer("b");
		q.offer("c");

		try (final var subject = new IuAsynchronousSubject<>(q::spliterator)) {
			final var subscriber = subject.subscribe();

			final var sp = subscriber.stream().spliterator();
			new Thread(() -> {
				try {
					Thread.sleep(50L);
					subject.accept("d");
					Thread.sleep(50L);
					subject.accept("e");
					subject.close();
				} catch (Throwable e) {
					subject.error(e);
				}
			}).start();

			final Set<String> control = new HashSet<>();
			sp.forEachRemaining(control::add);
			assertTrue(control.containsAll(Set.of("a", "b", "c", "d", "e")));

			final var c = mock(Consumer.class);
			sp.forEachRemaining(c);
			verify(c, never()).accept(any());
		}
	}

	@Test
	public void testForEachRemainingAfterPipe() {
		Queue<String> q = new ArrayDeque<>();
		q.offer("a");

		try (final var subject = new IuAsynchronousSubject<>(q::spliterator)) {
			final var subscriber = subject.subscribe();
			final var sp = subscriber.stream().spliterator();
			assertTrue(sp.tryAdvance(a -> assertEquals("a", a)));
			new Thread(() -> {
				try {
					Thread.sleep(50L);
					subject.accept("b");
					Thread.sleep(50L);
					subject.accept("c");
					Thread.sleep(50L);
					subject.accept("d");
					Thread.sleep(50L);
					subject.accept("e");
					subject.close();
				} catch (Throwable e) {
					subject.error(e);
				}
			}).start();
			assertTrue(sp.tryAdvance(a -> assertEquals("b", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("c", a)));
			final Set<String> control = new HashSet<>();
			sp.forEachRemaining(control::add);
			assertTrue(control.containsAll(Set.of("d", "e")));
		}
	}

	@Test
	public void testSpliterator() {
		try (final var subject = new IuAsynchronousSubject<>(Spliterators::emptySpliterator)) {
			final var subscriber = subject.subscribe();
			final var subsplit = subscriber.stream().spliterator();
			final var o = new Object();
			subject.accept(o);
			assertTrue(subsplit.tryAdvance(a -> assertEquals(a, o)));
			assertEquals(Spliterator.CONCURRENT, subsplit.characteristics());
			assertEquals(Long.MAX_VALUE, subsplit.estimateSize());
			assertNull(subsplit.trySplit());
			subject.accept(o);
			assertTrue(subsplit.tryAdvance(a -> assertEquals(a, o)));
			assertEquals(Spliterator.CONCURRENT, subsplit.characteristics());
			assertEquals(Long.MAX_VALUE, subsplit.estimateSize());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSourceSplit() {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var subscriber = subject.subscribe();
			final var subsplit = subscriber.stream().spliterator();

			final var split1 = subsplit.trySplit();
			assertEquals(1, split1.estimateSize());
			assertEquals(Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED, split1.characteristics());

			final var split2 = subsplit.trySplit();
			assertEquals(1, split1.estimateSize());
			assertEquals(Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED, split2.characteristics());

			subject.accept("d");

			assertTrue(split2.tryAdvance(a -> assertEquals("b", a)));
			assertFalse(split2.tryAdvance(a -> fail()));
			assertEquals(0, split2.estimateSize());
			assertEquals(Spliterator.SIZED, split2.characteristics());
			final var c = mock(Consumer.class);
			split2.forEachRemaining(c);
			verify(c, never()).accept(any());

			assertTrue(split1.tryAdvance(a -> assertEquals("a", a)));
			assertFalse(split1.tryAdvance(a -> fail()));
			assertEquals(0, split1.estimateSize());
			assertEquals(Spliterator.SIZED, split1.characteristics());

			assertTrue(subsplit.tryAdvance(a -> assertEquals("c", a)));
		}
	}

	@Test
	public void testSplitSplit() throws Throwable {
		final List<Object> l = new ArrayList<>(1000);
		for (var i = 0; i < 1000; i++)
			l.add(new Object());
		try (final var subject = new IuAsynchronousSubject<>(l::spliterator)) {
			final var subscriber = subject.subscribe();
			final var subsplit = subscriber.stream().spliterator();
			final var split1 = subsplit.trySplit();
			assertEquals(500, split1.estimateSize());
			assertEquals(Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED, split1.characteristics());
			final var split2 = split1.trySplit();
			assertEquals(250, split2.estimateSize());
			assertEquals(Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED, split2.characteristics());

			class Box {
				int count;
			}
			final var box = new Box();
			final var async = new Async(() -> {
				try {
					for (var i = 0; i < 100; i++)
						subject.accept(new Object());

					Thread.sleep(100L);
					while (split2.tryAdvance(a -> box.count++))
						;
					Thread.sleep(100L);
					while (split1.tryAdvance(a -> box.count++))
						;
					Thread.sleep(100L);
				} finally {
					subject.close();
				}
			});

			subsplit.forEachRemaining(a -> box.count++);
			async.await();

			assertEquals(1100, box.count);
		}
	}

	@Test
	public void testSubscriberSplitStates() throws Exception {
		try (final var subject = new IuAsynchronousSubject<String>(List.of("a")::spliterator)) {
			final var subscriber = subject.subscribe();
			final var subsplit = subscriber.stream().spliterator();
			subject.accept("b");
			assertNull(subsplit.trySplit()); // not exhausted
			assertTrue(subsplit.tryAdvance(a -> assertEquals("a", a)));
			assertTrue(subsplit.tryAdvance(a -> assertEquals("b", a)));
			assertNull(subsplit.trySplit()); // bootstrap pipe
			assertNull(subsplit.trySplit()); // reuse pipe
			subscriber.close();
		}
	}

	@Test
	public void testAppendsEmpty() {
		try (final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator)) {
			final var subscriber = subject.subscribe();
			final var i = subscriber.stream().iterator();
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
		assertFalse(subscriber.stream().iterator().hasNext());
	}

	@Test
	public void testClosesOnError() {
		try (final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator)) {
			final var subscriber = subject.subscribe();
			final var e = new RuntimeException();
			subject.error(e);
			assertSame(e, assertThrows(RuntimeException.class, () -> subscriber.stream().iterator().hasNext()));
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
	public void testError() throws Throwable {
		final var e = new RuntimeException();
		try (final var subject = new IuAsynchronousSubject<String>(Spliterators::emptySpliterator)) {
			final var async = new Async(() -> {
				Thread.sleep(100L);
				subject.error(e);
			});
			assertThrows(RuntimeException.class, () -> subject.subscribe().stream().forEach(a -> fail()));
			async.await();
		}
	}

	@Test
	public void testCloseThrowsErrorFromPipe() {
		final var e = new RuntimeException();
		try (final var mockPipe = mockConstruction(IuAsynchronousPipe.class, (p, context) -> {
			doThrow(e).when(p).close();
		})) {
			final var subject = new IuAsynchronousSubject<>(Spliterators::emptySpliterator);
			subject.subscribe();
			subject.accept(new Object());
			assertSame(e, assertThrows(RuntimeException.class, subject::close));
		}
	}

	@Test
	public void testCloseStreamUnsubscribes() throws Throwable {
		final var q = new ConcurrentLinkedQueue<String>();
		final var f = IuAsynchronousSubject.class.getDeclaredField("subscribers");
		f.setAccessible(true);
		try (final var subject = new IuAsynchronousSubject<>(q::spliterator)) {
			final Queue<?> subscribers = (Queue<?>) f.get(subject);
			final var stream = subject.subscribe();
			assertEquals(1, subscribers.size());
			stream.close();
			assertEquals(0, subscribers.size());
		}
	}

	@Test
	public void testSubscriberVolume() throws Throwable {
		final List<Object> control = Collections.synchronizedList(new ArrayList<>());
		final var q = new ConcurrentLinkedQueue<Object>();
		try (final var subject = new IuAsynchronousSubject<>(q::spliterator)) {
			final var generator = new Async(() -> {
				try {
					for (var i = 0; i < 1000; i++) {
						Thread.sleep(0, ThreadLocalRandom.current().nextInt(100_000));
						final var o = new Object();
						control.add(o);
						subject.accept(o);
						q.add(o);
					}
				} catch (Throwable e) {
					subject.error(e);
				}
			});

			Deque<UnsafeRunnable> toAwait = new ArrayDeque<>();
			for (var i = 0; i < 100; i++) {
				final var seqList = Collections.synchronizedList(new ArrayList<>());
				final var seqSub = subject.subscribe().stream();
				final var seqTask = new Async(() -> seqSub.forEach(seqList::add));
				final var parList = Collections.synchronizedList(new ArrayList<>());
				final var parSub = subject.subscribe().stream().parallel();
				final var parTask = new Async(() -> parSub.forEach(parList::add));
				toAwait.push(() -> {
					seqTask.await();
					assertEquals(control.size(), seqList.size());
					assertEquals(control, seqList);
					parTask.await();
					assertEquals(control.size(), parList.size());
					assertTrue(control.containsAll(parList));
				});
			}

			generator.await();
			subject.close();

			while (!toAwait.isEmpty())
				toAwait.pop().run();
		}
	}

	@Test
	public void testSplitAndAdvance() {
		final List<Object> l = new ArrayList<>(1000);
		for (var i = 0; i < 100; i++)
			l.add(new Object());

		try (final var subject = new IuAsynchronousSubject<>(() -> new Unsized<>(l.spliterator()))) {
			final var subscriber = subject.subscribe().stream();
			final var subsplit = subscriber.spliterator();
			final var split1 = subsplit.trySplit();
			for (var i = 0; i < 50; i++)
				assertTrue(split1.tryAdvance(a -> assertNotNull(a)));
			assertFalse(split1.tryAdvance(a -> fail()));
			assertFalse(split1.tryAdvance(a -> fail()));
		}
	}

	@Test
	public void testSplitAdvanceAndSplit() {
		final List<Object> l = new ArrayList<>(1000);
		for (var i = 0; i < 100; i++)
			l.add(new Object());

		try (final var subject = new IuAsynchronousSubject<>(() -> l.spliterator())) {
			final var subscriber = subject.subscribe().stream();
			final var subsplit = subscriber.spliterator();
			final var split1 = subsplit.trySplit();
			for (var i = 0; i < 50; i++)
				assertTrue(split1.tryAdvance(a -> assertNotNull(a)));
			assertNull(split1.trySplit());
			assertNull(split1.trySplit());
			assertFalse(split1.tryAdvance(a -> fail()));
		}
	}

	@Test
	public void testSplitSplitSplittySplit() {
		final List<Object> l = new ArrayList<>(100);
		for (var i = 0; i < 100; i++)
			l.add(new Object());

		class Box {
			int count;
		}
		final var box = new Box();

		try (final var subject = new IuAsynchronousSubject<>(() -> new Unsized<>(l.spliterator()))) {
			final var subscriber = subject.subscribe().stream();
			final var subsplit = subscriber.spliterator();
			for (var i = 0; i < 50; i++)
				subject.accept(new Object());
			subject.close();

			final Deque<Spliterator<Object>> splitq = new ArrayDeque<>();
			Spliterator<Object> split;
			while ((split = subsplit.trySplit()) != null)
				splitq.push(split);
			assertNull(subsplit.trySplit());

			while (subsplit.tryAdvance(a -> box.count++))
				;
			assertNull(subsplit.trySplit());

			while (!splitq.isEmpty()) {
				final var i = splitq.iterator();
				final Queue<Spliterator<Object>> p = new ArrayDeque<>();
				while (i.hasNext()) {
					final var n = i.next();
					final var s = n.trySplit();
					if (s != null)
						p.offer(s);
					if (!n.tryAdvance(a -> box.count++))
						i.remove();
				}
				p.forEach(splitq::push);
			}
		}
		assertEquals(150, box.count);
	}

	@Test
	public void testClosed() {
		final var subject = new IuAsynchronousSubject<>(Spliterators::emptySpliterator);
		final var sub = subject.subscribe().stream().spliterator();
		subject.close();
		assertEquals(0, sub.estimateSize());
		assertEquals(Spliterator.IMMUTABLE | Spliterator.SIZED, sub.characteristics());
	}

	@Test
	public void testForEachAfterError() {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var sub = subject.subscribe();
			final var sp = sub.stream().spliterator();
			subject.accept("d");
			subject.accept("e");

			final var e = new RuntimeException();
			assertEquals(Long.MAX_VALUE, sp.estimateSize());
			subject.error(e);
			assertEquals(5, sp.estimateSize());

			final Set<String> control = new HashSet<>(Set.of("a", "b", "c", "d", "e"));
			assertSame(e, assertThrows(RuntimeException.class,
					() -> sp.forEachRemaining(a -> assertTrue(control.remove(a)))));
			assertTrue(control.isEmpty(), control::toString);
			assertSame(e, assertThrows(RuntimeException.class, () -> sp.trySplit()));
		}
	}

	@Test
	public void testAvailable() {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var sub = subject.subscribe();
			assertEquals(3, sub.available());
			subject.accept("d");
			subject.accept("e");
			assertEquals(5, sub.available());

			final var sp = sub.stream().spliterator();
			assertEquals(Long.MAX_VALUE, sp.estimateSize());
			subject.close();
			assertEquals(5, sp.estimateSize());

			assertTrue(sp.tryAdvance(a -> assertEquals("a", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("b", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("c", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("d", a)));
			assertEquals(1, sub.available());
			assertTrue(sp.tryAdvance(a -> assertEquals("e", a)));
			assertEquals(0, sub.available());
		}
	}

	@Test
	public void testEstimateAddsAcceptedToLastSplit() {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var sub = subject.subscribe();
			assertEquals(3, sub.available());
			subject.accept("d");
			subject.accept("e");
			assertEquals(5, sub.available());

			final var sp = sub.stream().spliterator();
			final var sp1 = sp.trySplit();
			final var sp2 = sp.trySplit();
			subject.close();
			assertEquals(1, sub.available());
			assertEquals(1, sp.estimateSize());
			assertEquals(1, sp1.estimateSize());
			assertEquals(1, sp2.estimateSize());

			assertTrue(sp.tryAdvance(a -> assertEquals("c", a)));
			assertEquals(0, sub.available());
			assertEquals(1, sp1.estimateSize());
			assertTrue(sp1.tryAdvance(a -> assertEquals("a", a)));
			assertEquals(0, sp1.estimateSize());
			assertEquals(0, sub.available());
			assertEquals(3, sp2.estimateSize());
			assertTrue(sp2.tryAdvance(a -> assertEquals("b", a)));
			assertEquals(2, sp2.estimateSize());
			assertEquals(2, sp1.estimateSize());
			assertEquals(2, sp.estimateSize());
			assertEquals(2, sub.available());
			assertTrue(sp2.tryAdvance(a -> assertEquals("d", a)));
			assertEquals(1, sp2.estimateSize());
			assertEquals(1, sp1.estimateSize());
			assertEquals(1, sp.estimateSize());
			assertEquals(1, sub.available());
			assertTrue(sp1.tryAdvance(a -> assertEquals("e", a)));
			assertEquals(0, sp2.estimateSize());
			assertEquals(0, sp1.estimateSize());
			assertEquals(0, sp.estimateSize());
			assertEquals(0, sub.available());
		}
	}

	@Test
	public void testAvailableOfUnsized() {
		try (final var subject = new IuAsynchronousSubject<>(new ConcurrentLinkedQueue<>()::spliterator)) {
			final var sub = subject.subscribe();
			assertEquals(0, sub.available());
			subject.accept("d");
			subject.accept("e");
			assertEquals(2, sub.available());

			final var sp = sub.stream().spliterator();
			assertTrue(sp.tryAdvance(a -> assertEquals("d", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("e", a)));
			assertEquals(0, sub.available());

			subject.accept("f");
			assertEquals(1, sub.available());
		}
	}

	@Test
	public void testReceiverPauseOnSource() throws Throwable {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var sub = subject.subscribe();
			final var sp = sub.stream().spliterator();
			final var async = new Async(() -> {
				Thread.sleep(100L);
				subject.accept("d");
				subject.accept("e");
				Thread.sleep(100L);
				assertTrue(sp.tryAdvance(a -> assertEquals("e", a)));
				Thread.sleep(100L);
				subject.accept("f");
				subject.accept("g");
				subject.close();
			});
			assertEquals(0L, sub.pause(0, Duration.ZERO));
			sub.pause(2, Duration.ofSeconds(1L));
			assertEquals(5, sub.available());
			assertTrue(sp.tryAdvance(a -> assertEquals("a", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("b", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("c", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("d", a)));
			assertEquals(2, sub.pause(Instant.now().plusMillis(1000L)));
			assertTrue(sp.tryAdvance(a -> assertEquals("f", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("g", a)));
			async.await();
		}
	}

	@Test
	public void testMixedPause() throws Throwable {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var sub = subject.subscribe();
			final var sp = sub.stream().spliterator();
			final var async = new Async(() -> {
				Thread.sleep(100L);
				subject.accept("d");
				subject.accept("e");
				Thread.sleep(100L);
				assertTrue(sp.tryAdvance(a -> assertEquals("c", a)));
				assertTrue(sp.tryAdvance(a -> assertEquals("d", a)));
				assertTrue(sp.tryAdvance(a -> assertEquals("e", a)));
				Thread.sleep(100L);
				subject.accept("f");
				subject.accept("g");
				Thread.sleep(100L);
				subject.accept("h");
				Thread.sleep(100L);
				subject.accept("i");
				subject.close();
			});
			assertTrue(sp.tryAdvance(a -> assertEquals("a", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("b", a)));
			assertEquals(4, sub.pause(4, Duration.ofSeconds(1L)));
			assertTrue(sp.tryAdvance(a -> assertEquals("f", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("g", a)));
			assertEquals(1, sub.pause(1, Duration.ofSeconds(1L)));
			assertTrue(sp.tryAdvance(a -> assertEquals("h", a)));
			assertEquals(1, sub.pause(Instant.now().plus(Duration.ofSeconds(1L))));
			assertTrue(sp.tryAdvance(a -> assertEquals("i", a)));
			async.await();
		}
	}

	@Test
	public void testPauseAndClose() throws Throwable {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var sub = subject.subscribe();
			final var sp = sub.stream().spliterator();
			final var async = new Async(() -> {
				Thread.sleep(100L);
				sub.close();
				assertTrue(sp.tryAdvance(a -> assertEquals("c", a)));
			});
			assertTrue(sp.tryAdvance(a -> assertEquals("a", a)));
			assertTrue(sp.tryAdvance(a -> assertEquals("b", a)));
			assertEquals(0, sub.pause(1, Duration.ofSeconds(1L)));
			async.await();
		}
	}

	@Test
	public void testPauseAndExpire() throws Throwable {
		try (final var subject = new IuAsynchronousSubject<>(List.of("a", "b", "c")::spliterator)) {
			final var sub = subject.subscribe();
			final var async = new Async(() -> {
				Thread.sleep(200L);
				sub.close();
			});
			assertEquals(0, sub.pause(Instant.now().plusMillis(100L)));
			async.await();
		}
	}

}
