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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuEnumerableQueueTest {

	@Test
	public void testEdges() {
		final var e = new IuEnumerableQueue<>();
		assertFalse(e.getAsBoolean());
		assertThrows(NoSuchElementException.class, e::next);
		e.accept(new Object());
		assertTrue(e.getAsBoolean());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testIterator() throws Throwable {
		proliferate(IdGenerator::generateId, (a, b) -> {
			final var sa = Collections.synchronizedSet(new HashSet<>());
			a.asIterator().forEachRemaining(sa::add);
			final var sb = new HashSet<>();
			new IuEnumerableQueue<>((Enumeration) b).forEach(sb::add);
			assertEquals(sa.size(), sb.size());
			assertTrue(sa.containsAll(sb));
			assertTrue(sb.containsAll(sa));
		});
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testSpliterator() throws Throwable {
		proliferate(IdGenerator::generateId, (a, b) -> {
			final var sa = Collections.synchronizedSet(new HashSet<>());
			new IuEnumerableQueue<>((Iterable) a).forEachRemaining(sa::add);
			final var sb = new HashSet<>();
			new IuEnumerableQueue<>((Spliterator) b).forEachRemaining(sb::add);
			assertEquals(sa.size(), sb.size());
			assertTrue(sa.containsAll(sb));
			assertTrue(sb.containsAll(sa));
		});
	}

	@Test
	public void testEnumeration() throws Throwable {
		proliferate(IdGenerator::generateId, (a, b) -> {
			final var sa = Collections.synchronizedSet(new HashSet<>());
			a.forEach(sa::add);
			final var sb = new HashSet<>();
			while (b.hasMoreElements())
				sb.add(b.nextElement());
			assertEquals(sa.size(), sb.size());
			assertTrue(sa.containsAll(sb));
			assertTrue(sb.containsAll(sa));
		});
	}

	@Test
	public void testStream() throws Throwable {
		proliferate(IdGenerator::generateId, (a, b) -> {
			final var sa = Collections.synchronizedSet(new HashSet<>());
			a.parallelStream().forEach(sa::add);
			final var sb = new HashSet<>();
			new IuEnumerableQueue<>(b.stream()).forEach(sb::add);
			assertEquals(sa.size(), sb.size());
			assertFalse(a.tryAdvance(c -> {
			}));
			assertTrue(sa.containsAll(sb));
			assertTrue(sb.containsAll(sa));
		});
	}

	private <T> void proliferate(Supplier<T> factory, BiConsumer<IuEnumerableQueue<T>, IuEnumerableQueue<T>> with)
			throws Throwable {
		final var q = generate(128, factory);
		while (!q.isEmpty())
			with.accept(q.remove(ThreadLocalRandom.current().nextInt(q.size())),
					q.remove(ThreadLocalRandom.current().nextInt(q.size())));
	}

	public <T> List<IuEnumerableQueue<T>> generate(int length, Supplier<T> factory) throws Throwable {
		final var a = new ArrayList<IuEnumerableQueue<T>>(length);
		for (var j = 0; j < length; j++)
			a.add(new IuEnumerableQueue<T>());

		final var l = new IuRateLimitter(30, Duration.ofSeconds(5L));
		try (final var pc = new IuParallelWorkloadController("test", 32, Duration.ofSeconds(5L))) {
			for (var i = 0; i < Short.MAX_VALUE; i++)
				l.accept(pc.apply(task -> {
					final var v = factory.get();
					a.parallelStream().forEach(b -> b.accept(v));
				}));
			l.join();
			pc.await();
		}
		return a;
	}

}
