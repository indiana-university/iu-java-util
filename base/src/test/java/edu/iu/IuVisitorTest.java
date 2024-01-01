/*
 * Copyright © 2023 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuVisitorTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testEmpty() {
		final var visitor = new IuVisitor();
		final Function f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testVisitOne() {
		final var visitor = new IuVisitor();
		final var one = new Object();
		visitor.accept(one);
		final Function f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(one);
		verify(f).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testPicksOne() {
		final var visitor = new IuVisitor();
		final var one = new Object();
		visitor.accept(one);
		final Function f = mock(Function.class);
		when(f.apply(one)).thenReturn(Optional.empty());
		visitor.visit(f);
		verify(f).apply(one);
		verify(f, never()).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testClear() throws InterruptedException {
		final var visitor = new IuVisitor();
		final var one = new Object();
		final var two = new Object();
		visitor.accept(one);
		visitor.accept(two);
		Function f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(one);
		verify(f).apply(null);
		visitor.accept(new Object());
		System.gc();
		Thread.sleep(100L);
		visitor.clear(one);
		f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(two);
		verify(f).apply(notNull());
		verify(f).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testPrunesClearedRefs() throws InterruptedException {
		final var visitor = new IuVisitor();
		visitor.accept(new Object());
		final Function f = mock(Function.class);
		System.gc();
		Thread.sleep(100L);
		visitor.visit(f);
		verify(f, never()).apply(notNull());
		verify(f).apply(null);
	}

	@Test
	public void testSubject() throws Throwable {
		final var visitor = new IuVisitor<Object>();
		final var subject = visitor.subject();
		final var control = Collections.synchronizedList(new ArrayList<Object>());
		final Runnable thunk = () -> {
			final var o = new Object();
			visitor.accept(o);
			subject.accept(o);
			control.add(o);
		};

		class Box {
			final List<Object> collected = Collections.synchronizedList(new ArrayList<>());
			volatile boolean done;
			volatile Throwable error;

			Box(UnsafeConsumer<Box> task) {
				new Thread(() -> {
					try {
						task.accept(this);
						done = true;
					} catch (Throwable e) {
						error = e;
					} finally {
						synchronized (this) {
							this.notifyAll();
						}
					}
				}).start();
			}

			void join() throws Throwable {
				IuObject.waitFor(this, () -> done || error != null, Duration.ofSeconds(1L));
				if (error != null)
					throw error;
			}
		}

		final var generator = new Box(b -> {
			for (var i = 0; i < 100; i++) {
				Thread.sleep(0, ThreadLocalRandom.current().nextInt(100_000));
				thunk.run();
			}
			subject.close();
		});
		final var sequence = new Box(b -> subject.subscribe().forEach(b.collected::add));
		final var parallel = new Box(b -> subject.subscribe().parallel().forEach(b.collected::add));
		Throwable error = null;
		error = IuException.suppress(error, generator::join);
		error = IuException.suppress(error, sequence::join);
		error = IuException.suppress(error, parallel::join);
		if (error != null)
			throw error;

		assertEquals(100, control.size());
//		assertEquals(100, sequence.collected.size());
		assertEquals(control, sequence.collected);
		assertEquals(100, parallel.collected.size());
		assertTrue(parallel.collected.containsAll(control));
	}

}
