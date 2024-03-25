package iu.client;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Supplier;

import edu.iu.client.IuJsonAdapter;

/**
 * Adapts {@link Collection} values.
 * 
 * @param <E> element type
 * @param <C> collection type
 */
class CollectionAdapter<E, C extends Collection<E>> extends JsonArrayAdapter<C, E> {

	private final Supplier<C> factory;

	/**
	 * Constructor
	 * 
	 * @param itemAdapter item adapter
	 * @param factory     creates a new collection
	 */
	protected CollectionAdapter(IuJsonAdapter<E> itemAdapter, Supplier<C> factory) {
		super(itemAdapter);
		this.factory = factory;
	}

	@Override
	protected Iterator<E> iterator(C value) {
		return value.iterator();
	}

	@Override
	protected C collect(Iterator<E> items) {
		final var list = factory.get();
		while (items.hasNext())
			list.add(items.next());
		return list;
	}

}
