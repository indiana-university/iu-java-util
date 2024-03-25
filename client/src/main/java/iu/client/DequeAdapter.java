package iu.client;

import java.util.ArrayDeque;
import java.util.Iterator;

import edu.iu.client.IuJsonAdapter;

/**
 * Adapts {@link Iterable} values.
 * 
 * @param <E> element type
 */
public class DequeAdapter<E> extends JsonArrayAdapter<Iterable<E>, E> {

	/**
	 * Singleton instance.
	 */
	static final DequeAdapter<?> INSTANCE = new DequeAdapter<>(null);

	/**
	 * Constructor
	 * 
	 * @param itemAdapter item adapter
	 */
	protected DequeAdapter(IuJsonAdapter<E> itemAdapter) {
		super(itemAdapter);
	}

	@Override
	protected Iterator<E> iterator(Iterable<E> value) {
		return value.iterator();
	}

	@Override
	protected Iterable<E> collect(Iterator<E> items) {
		final var list = new ArrayDeque<E>();
		while (items.hasNext())
			list.add(items.next());
		return list;
	}

}
