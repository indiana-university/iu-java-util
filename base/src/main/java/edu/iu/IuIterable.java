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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Lightweight <strong>factory</strong>, manipulation, and processing utility
 * for <strong>constantly repeatable</strong> {@link Iterable} and other
 * {@link Supplier Supplier&lt;Iterator&gt;} compatible iterator sources.
 * 
 * <p>
 * This utility is useful for low-level optimization routines. Note that:
 * </p>
 * 
 * <pre>
 * for (var reduced : IuIterable.map(myList, a::reducer)) {
 * 	// do something with reduced
 * }
 * for (var filtered : IuIterable.filter(myList, a::matches)) {
 * 	// do something with reduced
 * }
 * </pre>
 * <p>
 * ...are equivalent to:
 * </p>
 * 
 * <pre>
 * myList.stream().map(a::reducerMethod)...// do something
 * myList.stream().filter(a::isMatching)...// do something
 * </pre>
 * <p>
 * ...but with minimal object creation (w/o Stream overhead), and in a form that
 * may be used directly in a {@code for} loop.
 * </p>
 * 
 * <p>
 * IuIterable <strong>factory instances</strong> are <em>recommended</em> for
 * all potentially temporary or synchronized complex <strong>constantly
 * repeatable</strong> iteration scenarios, as in the examples above. This
 * consideration improves reachability, i.e., by unit tests and debug
 * breakpoints, compared to {@link Iterable#forEach(Consumer)}.
 * </p>
 * <p>
 * <strong>Factory iterables</strong> are also preferred over {@link Stream}
 * forms.
 * </p>
 * 
 * <p>
 * In particular, {@link #cat(Iterable...)} is useful for union joins of like
 * iterations.:
 * </p>
 * 
 * <pre>
 * for (var item : IuIterable.cat(myCollection, iter(myArray), myMap.values())) {
 * 	// do something with items
 * }
 * </pre>
 * 
 * <p>
 * A common case for <strong>constantly repeatable</strong> iteration is
 * returning a value from or passing an argument to a method on a business
 * interface.
 * </p>
 * 
 * <p>
 * <strong>Factory iterators</strong> implement {@link Object#hashCode()} by
 * returning the number of items already removed from the head of the iterator,
 * and {@link Object#equals(Object)} for non-destructively comparing the tail
 * end to that of another <strong>factory iterator</strong>. These semantics
 * <em>may</em> be used reliably for short-term set operations between
 * concurrent iterators.
 * </p>
 * 
 * <p>
 * <strong>API Note: Constantly repeatable</strong> refers to an immutable
 * {@link Iterator} source factory backed by a fixed number of data elements
 * strictly composed of primitive values and constable instances (i.e.,
 * {@link java.time}, {@link Collection} and {@link Map} with strictly constable
 * values, etc). Source <em>must</em> be immutable—{@link Iterator#remove()}
 * will not be invoked and <em>should not</em> be implemented. These conditions
 * are not verifiable, so results are undefined if not met by the application.
 * </p>
 * 
 * <p>
 * All factory {@link Iterable} instances <em>should</em> be considered
 * disposable and used only in local variable scope. Take care not to pass
 * factory instances to lambdas, inline, or anonymous classes. {@link Iterable}s
 * backing these instances need only remain <strong>constantly
 * repeatable</strong> while in use (i.e. backing a {@code for} loop). Once out
 * of scope, the backing sources may change. Therefore, consumers
 * <em>should</em> hold source references, not factory instances.
 * </p>
 * 
 * @since 5.4
 */
public final class IuIterable {

	private static final Iterable<?> EMPTY = Collections::emptyIterator;

	private static class IuIterator<T> implements Iterator<T> {
		private final Supplier<Iterator<T>> iteratorSupplier;
		private final Iterator<T> i;
		private int skip;

		private IuIterator(Supplier<Iterator<T>> iteratorSupplier) {
			this.iteratorSupplier = iteratorSupplier;
			this.i = iteratorSupplier.get();
		}

		@Override
		public boolean hasNext() {
			return i.hasNext();
		}

		@Override
		public T next() {
			skip++;
			return i.next();
		}

		@Override
		public int hashCode() {
			return skip;
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;

			IuIterator<?> other = (IuIterator<?>) obj;

			Iterator<?> a = iteratorSupplier.get();
			for (var i = 0; i < skip; i++)
				a.next();

			Iterator<?> b = other.iteratorSupplier.get();
			for (var i = 0; i < other.skip; i++)
				b.next();

			return remaindersAreEqual(a, b);
		}

		@Override
		public String toString() {
			return IuIterable.print(iteratorSupplier.get(), skip);
		}
	}

	/**
	 * Creates an {@link Iterable} instance from a <strong>constantly
	 * repeatable</strong> supplier.
	 * 
	 * @param <T>      item type
	 * @param supplier {@link Iterable} {@link Supplier}; <em>must</em> be
	 *                 <strong>constantly repeatable</strong>.
	 * @return {@link Iterable}
	 */
	public static <T> Iterable<T> of(Supplier<Iterator<T>> supplier) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new IuIterator<>(supplier);
			}

			@Override
			public String toString() {
				return IuIterable.print(supplier.get(), 0);
			}
		};
	}

	/**
	 * Returns an {@link Iterable} that supplies empty {@link Iterator}s.
	 * 
	 * @param <T> item type
	 * @return empty {@link Iterable}
	 */
	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> empty() {
		return (Iterable<T>) EMPTY;
	}

	/**
	 * Returns a string representation of an {@link Iterable}.
	 * 
	 * @param iterable {@link Iterable}
	 * @return {@link Object#toString()} if {@code iterable} is a
	 *         {@link Collection}, otherwise returns {@link #print(Iterator)
	 *         .toString(iterable.iterator())}
	 */
	public static String print(Iterable<?> iterable) {
		if (iterable instanceof Collection)
			return iterable.toString();
		else if (iterable == null)
			return "null";
		else
			return print(iterable.iterator(), 0);
	}

	/**
	 * Returns a string representation of all remaining elements of an
	 * {@link Iterator}.
	 * 
	 * @param iterator {@link Iterator}, will be exhausted
	 * @return string representation
	 */
	public static String print(Iterator<?> iterator) {
		return print(iterator, 0);
	}

	/**
	 * Returns a string representation of remaining elements of an {@link Iterator},
	 * after skipping a specified number of elements.
	 * 
	 * @param iterator {@link Iterator}, will be exhausted
	 * @param skip     number of steps to skip before recording; may be 0 to skip no
	 *                 steps. Skipped steps are printed as "..."
	 * @return string representation
	 * @throws NoSuchElementException   if skip requests skipping elements no
	 *                                  present on the source iterable.
	 * @throws IllegalArgumentException if skip &lt; 0
	 */
	public static String print(Iterator<?> iterator, int skip) throws NoSuchElementException, IllegalArgumentException {
		if (skip < 0)
			throw new IllegalArgumentException();

		if (iterator == null)
			return "null";

		final var sb = new StringBuilder("[");
		var first = true;

		while (skip > 0) {
			if (first) {
				first = false;
				sb.append("...");
			}

			iterator.next();
			skip--;
		}

		while (iterator.hasNext()) {
			final var a = iterator.next();
			if (first)
				first = false;
			else
				sb.append(", ");

			sb.append(a);
		}

		sb.append(']');

		return sb.toString();
	}

	/**
	 * Steps through two iterators, comparing all remaining items on each until
	 * either both are exhausted or items mismatch between the two.
	 * 
	 * <p>
	 * This is a destructive operation that renders both arguments unusable.
	 * </p>
	 * 
	 * @param i1 {@link Iterator}, will either be exhausted or left in an unknown
	 *           state.
	 * @param i2 {@link Iterator}, will either be exhausted or left in an unknown
	 *           state.
	 * 
	 * @return true if both iterators contained the same number of items, and all
	 *         items in both iterators were {@link Object#equals(Object)} in the
	 *         order iterated.
	 */
	public static boolean remaindersAreEqual(Iterator<?> i1, Iterator<?> i2) {
		if (i1 == i2)
			return true;

		while (i1.hasNext()) {
			if (!i2.hasNext())
				return false;

			if (!IuObject.equals(i1.next(), i2.next()))
				return false;
		}
		return !i2.hasNext();
	}

	/**
	 * Wraps an array.
	 * 
	 * @param <T> item type
	 * @param a   array
	 * @return An iterable over the entire array.
	 */
	@SafeVarargs
	public static <T> Iterable<T> iter(T... a) {
		return iter(a, 0);
	}

	/**
	 * Wraps an array.
	 * 
	 * @param <T>  item type
	 * @param a    array
	 * @param from starting point
	 * @return An iterable over the array starting from the point indicated.
	 */
	public static <T> Iterable<T> iter(T[] a, int from) {
		if (from < 0)
			throw new IndexOutOfBoundsException();
		else if (a == null)
			if (from > 0)
				throw new IndexOutOfBoundsException();
			else
				return empty();

		final var length = a.length;
		if (from > length)
			throw new IndexOutOfBoundsException();
		else if (length == 0)
			return empty();
		else
			return of(() -> new Iterator<T>() {
				int i = from;

				@Override
				public boolean hasNext() {
					return i < length;
				}

				@Override
				public T next() {
					if (i >= length)
						throw new NoSuchElementException();
					return a[i++];
				}
			});
	}

	/**
	 * Concatenates one or more iterables.
	 * 
	 * @param <T>       item type
	 * @param iterables iterables
	 * @return A single iterable over all iterables in sequence.
	 */
	@SafeVarargs
	public static <T> Iterable<T> cat(Iterable<T>... iterables) {
		switch (iterables.length) {
		case 0:
			return empty();

		case 1:
			return iterables[0];

		default:
			return of(() -> new Iterator<T>() {
				@SuppressWarnings("unchecked")
				final Iterator<? extends T>[] i = new Iterator[iterables.length];
				int n = 0;

				@Override
				public boolean hasNext() {
					while (n < i.length) {
						if (i[n] == null)
							i[n] = iterables[n].iterator();
						if (i[n].hasNext())
							return true;
						else
							n++;
					}
					return false;
				}

				@Override
				public T next() {
					if (hasNext())
						return i[n].next();
					else
						throw new NoSuchElementException();
				}
			});
		}
	}

	/**
	 * Maps an iterable using a transform function.
	 * 
	 * @param <T> item type
	 * @param <U> transformed item type
	 * @param i   iterable
	 * @param f   transform function
	 * @return An iterable over the results of applying the transform function to
	 *         the items available from the iterable.
	 */
	public static <T, U> Iterable<U> map(Iterable<T> i, Function<T, U> f) {
		Objects.requireNonNull(f);
		return of(() -> new Iterator<U>() {
			private Iterator<T> itr = i.iterator();

			@Override
			public boolean hasNext() {
				return itr.hasNext();
			}

			@Override
			public U next() {
				return f.apply(itr.next());
			}
		});
	}

	/**
	 * Filters an interable using predicate.
	 * 
	 * @param <T> item type
	 * @param i   iterable
	 * @param p   predicate
	 * @return An iterable over the items for which the predicate returns true.
	 */
	public static <T> Iterable<T> filter(Iterable<T> i, Predicate<T> p) {
		Objects.requireNonNull(p);
		return of(() -> new Iterator<T>() {
			private Iterator<T> itr = i.iterator();
			private boolean hasNext;
			private T next;

			@Override
			public boolean hasNext() {
				while (!hasNext && itr.hasNext()) {
					next = itr.next();
					hasNext = p.test(next);
				}
				return hasNext;
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();

				T rv = next;
				hasNext = false;
				next = null;
				return rv;
			}
		});
	}

	/**
	 * Gets a {@link Stream} of the elements in an constantly repeatable
	 * {@link Iterable}.
	 * 
	 * @param <T> element type
	 * @param i   {@link Iterable} of elements
	 * @return {@link Stream}
	 */
	public static <T> Stream<T> stream(Iterable<T> i) {
		return StreamSupport.stream(() -> i.spliterator(), Spliterator.IMMUTABLE, false);
	}

	private IuIterable() {
	}

}
