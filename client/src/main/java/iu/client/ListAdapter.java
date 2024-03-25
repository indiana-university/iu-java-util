package iu.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.iu.client.IuJsonAdapter;

/**
 * Adapts {@link List} values.
 * 
 * @param <E> element type
 */
public class ListAdapter<E> extends JsonArrayAdapter<List<E>, E> {

	/**
	 * Singleton instance.
	 */
	static final ListAdapter<?> INSTANCE = new ListAdapter<>(null);

	/**
	 * Constructor
	 * 
	 * @param itemAdapter item adapter
	 */
	protected ListAdapter(IuJsonAdapter<E> itemAdapter) {
		super(itemAdapter);
	}

	@Override
	protected Iterator<E> iterator(List<E> value) {
		return value.iterator();
	}

	@Override
	protected List<E> collect(Iterator<E> items) {
		final List<E> list = new ArrayList<>();
		while (items.hasNext())
			list.add(items.next());
		return list;
	}

}
