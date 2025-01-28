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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implements a basic visitor pattern for tracking disparate uniform instances
 * of a specific element type.
 * 
 * <p>
 * This resource is thread-safe and intended for use in high-volume and
 * time-sensitive component initialization scenarios. Elements are weakly held,
 * so will may only be visited until cleared by the garbage collector.
 * </p>
 * 
 * <p>
 * <img src="doc-files/Visitor.svg" alt="UML Class Diagram" />
 * </p>
 * 
 * @param <T> element type
 */
public class IuVisitor<T> implements Consumer<T> {

	private class ElementSplitter implements Spliterator<T> {
		private final Spliterator<Reference<T>> elementSpliterator;

		private ElementSplitter(Spliterator<Reference<T>> elementSpliterator) {
			this.elementSpliterator = elementSpliterator;
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			class Box {
				boolean accepted;
			}
			final var box = new Box();

			while (!box.accepted)
				if (!elementSpliterator.tryAdvance(ref -> {
					final var element = ref.get();
					if (element != null) {
						action.accept(element);
						box.accepted = true;
					}
				}))
					return false;

			return box.accepted;
		}

		@Override
		public Spliterator<T> trySplit() {
			final var split = elementSpliterator.trySplit();
			if (split != null)
				return new ElementSplitter(split);
			else
				return null;
		}

		@Override
		public long estimateSize() {
			if (elementSpliterator.hasCharacteristics(SIZED))
				return elementSpliterator.estimateSize();
			else
				return elements.size();
		}

		@Override
		public int characteristics() {
			return elementSpliterator.characteristics() | SIZED;
		}
	}

	private final Queue<Reference<T>> elements = new ConcurrentLinkedQueue<>();

	/**
	 * Default constructor.
	 */
	public IuVisitor() {
	}

	/**
	 * Applies a function to each element until a generic condition is satisfied.
	 * 
	 * @param <V>     value type
	 * @param visitor Function to {@link Function#apply(Object) apply} each element
	 *                to until an {@link Optional} value is returned. {@code null}
	 *                will be {@link Function#apply(Object) applied} last if no
	 *                elements result in an {@link Optional} value. The function
	 *                <em>may</em> return {@code null} to continue to the next
	 *                element, and <em>may</em> always return {@code null} to always
	 *                visit all elements. A non-null {@link Optional} result
	 *                indicates the visited element satisfied a generic condition of
	 *                some sort and so no other elements should be visited; the
	 *                {@link Optional} is immediately returned as-is.
	 * @return {@link Optional} result of the first terminal condition satisfied;
	 *         null if a terminal condition was not met.
	 */
	public <V> Optional<V> visit(Function<T, Optional<V>> visitor) {
		final var elementIterator = elements.iterator();
		while (elementIterator.hasNext()) {
			final var elementReference = elementIterator.next();
			final var element = elementReference.get();
			if (element == null) {
				elementIterator.remove();
				continue;
			}

			final var optionalValue = visitor.apply(element);
			if (optionalValue != null)
				return optionalValue;
		}

		return visitor.apply(null);
	}

	/**
	 * Accepts an element to be observed.
	 * 
	 * @param element to observe
	 */
	@Override
	public void accept(T element) {
		elements.add(new WeakReference<>(element));
	}

	/**
	 * Removes an element from observation queue without waiting for the garbage
	 * collector to clear it.
	 * 
	 * <p>
	 * This method does not tear down or take any other action on the element, the
	 * controlling component <em>should</em> handle all tear down logic related to
	 * this visitor instance prior to clearing the reference, typically invoking
	 * this method last in a managed instance's lifecycle teardown process.
	 * </p>
	 * 
	 * <p>
	 * This method has no effect if the element is not in the observation queue.
	 * </p>
	 * 
	 * <p>
	 * <strong>API Note:</strong> Since elements are {@link WeakReference weakly
	 * held}, this method is only necessary when hooking in to an external instance
	 * management mechanism. Typically, instances may simply be discarded rather
	 * then explicitly cleared.
	 * </p>
	 * 
	 * @param element element to clear
	 */
	public void clear(T element) {
		final var elementIterator = elements.iterator();
		while (elementIterator.hasNext()) {
			final var elementReference = elementIterator.next();
			final var deref = elementReference.get();
			if (deref == null || element == deref) {
				elementIterator.remove();
				continue;
			}
		}
	}

	/**
	 * Gets a {@link IuAsynchronousSubject} originated by non-cleared references to
	 * accepted elements.
	 * 
	 * <p>
	 * Each call to this method returns an independent subject instance. The
	 * controller responsible for providing elements to the visitor <em>must</em>
	 * independently provide the same values to, and close its own, subject instance
	 * to ensure continuity for subscribers.
	 * </p>
	 * 
	 * @return {@link IuAsynchronousSubject}
	 */
	public IuAsynchronousSubject<T> subject() {
		return new IuAsynchronousSubject<>(() -> new ElementSplitter(elements.spliterator()));
	}

}
