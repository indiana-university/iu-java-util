/*
 * Copyright © 2026 Indiana University
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

import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implements a simple non-blocking FIFO queue based on the {@link Consumer},
 * and {@link Enumeration}, and {@link BooleanSupplier} interfaces.
 * 
 * <p>
 * In this scenario, {@link #getAsBoolean()}, {@link #hasNext()}, and
 * {@link #hasMoreElements()} are synonymous.
 * </p>
 * 
 * <p>
 * This class is thread-safe and intended for use in high-volume parallel
 * processing scenarios.
 * </p>
 * 
 * <p>
 * For blocking behavior, use
 * {@link IuObject#waitFor(Object, BooleanSupplier, java.time.Duration)} or
 * {@link IuObject#waitFor(Object, BooleanSupplier, java.time.Instant)} as in
 * the example below:
 * </p>
 * 
 * <pre>
 * IuObject.waitFor(enumerableQueue, enumerableQueue, Duration.ofSeconds(5L));
 * </pre>
 * 
 * @param <T> element type
 */
public class IuEnumerableQueue<T>
		implements Consumer<T>, BooleanSupplier, Enumeration<T>, Iterator<T>, Spliterator<T>, Iterable<T> {

	private final ConcurrentLinkedQueue<Optional<T>> queue = new ConcurrentLinkedQueue<>();

	/**
	 * Default constructor.
	 */
	public IuEnumerableQueue() {
	}

	/**
	 * Constructor
	 * 
	 * @param e supplies initial values
	 */
	public IuEnumerableQueue(Enumeration<T> e) {
		this(e.asIterator());
	}

	/**
	 * Constructor
	 * 
	 * @param e supplies initial values
	 */
	public IuEnumerableQueue(Iterator<T> e) {
		e.forEachRemaining(this);
	}

	/**
	 * Constructor
	 * 
	 * @param e supplies initial values
	 */
	public IuEnumerableQueue(Spliterator<T> e) {
		e.forEachRemaining(this);
	}

	/**
	 * Constructor
	 * 
	 * @param e supplies initial values
	 */
	public IuEnumerableQueue(Iterable<T> e) {
		e.forEach(this);
	}

	/**
	 * Constructor
	 * 
	 * @param e supplies initial values
	 */
	public IuEnumerableQueue(Stream<T> e) {
		e.forEach(this);
	}

	@Override
	public void accept(T t) {
		queue.offer(Optional.ofNullable(t));
		handleChange();
	}

	@Override
	public boolean getAsBoolean() {
		return !queue.isEmpty();
	}

	@Override
	public boolean hasMoreElements() {
		return !queue.isEmpty();
	}

	@Override
	public boolean hasNext() {
		return !queue.isEmpty();
	}

	@Override
	public T next() {
		return nextElement();
	}

	@Override
	public T nextElement() {
		final var next = queue.poll();
		if (next == null)
			throw new NoSuchElementException();

		handleChange();
		return next.orElse(null);
	}

	@Override
	public boolean tryAdvance(Consumer<? super T> action) {
		final var next = queue.poll();
		if (next != null) {
			handleChange();
			action.accept(next.orElse(null));
			return true;
		} else
			return false;
	}

	@Override
	public Spliterator<T> trySplit() {
		final var splitSize = queue.size() / 2;
		if (splitSize < 16)
			return null;

		final var split = new ArrayDeque<T>();
		while (split.size() < splitSize)
			split.offer(queue.poll().orElse(null));

		handleChange();
		return split.spliterator();
	}

	@Override
	public long estimateSize() {
		return Long.MAX_VALUE;
	}

	@Override
	public int characteristics() {
		return Spliterator.CONCURRENT;
	}

	private synchronized void handleChange() {
		notifyAll();
	}

	@Override
	public void forEachRemaining(Consumer<? super T> action) {
		Spliterator.super.forEachRemaining(action);
	}

	@Override
	public Iterator<T> iterator() {
		return this;
	}

	@Override
	public Iterator<T> asIterator() {
		return this;
	}

	/**
	 * Gets a {@link Stream} of queued elements.
	 * 
	 * @return {@link Stream}
	 */
	public Stream<T> stream() {
		return StreamSupport.stream(this, false);
	}

	/**
	 * Gets a parallel {@link Stream} of queued elements.
	 * 
	 * @return {@link Stream}
	 */
	public Stream<T> parallelStream() {
		return StreamSupport.stream(this, true);
	}

}
