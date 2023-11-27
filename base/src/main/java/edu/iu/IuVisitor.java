package edu.iu;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.Queue;
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
 * @param <T> element type
 */
public class IuVisitor<T> implements Consumer<T> {

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

}
