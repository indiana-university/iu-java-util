package iu.client;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.IntFunction;

import edu.iu.IuIterable;
import edu.iu.client.IuJsonAdapter;

/**
 * Adapts array values.
 * 
 * @param <C> component type
 */
public class ArrayAdapter<C> extends JsonArrayAdapter<C[], C> {

	private final IntFunction<C[]> factory;

	/**
	 * Constructor
	 * 
	 * @param itemAdapter item adapter
	 * @param factory     array factory
	 */
	protected ArrayAdapter(IuJsonAdapter<C> itemAdapter, IntFunction<C[]> factory) {
		super(itemAdapter);
		this.factory = factory;
	}

	@Override
	protected Iterator<C> iterator(C[] value) {
		return IuIterable.iter(value).iterator();
	}

	@Override
	protected C[] collect(Iterator<C> items) {
		final Queue<C> q = new ArrayDeque<>();
		while (items.hasNext())
			q.add(items.next());
		return q.toArray(factory);
	}

}
