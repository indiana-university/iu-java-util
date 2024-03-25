package iu.client;

import java.util.Iterator;

import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;

/**
 * Adapts to/from {@link JsonArray} values.
 * 
 * @param <T> target type
 * @param <E> element type
 */
abstract class JsonArrayAdapter<T, E> implements IuJsonAdapter<T> {

	/**
	 * Extracts an iterator from a Java value.
	 * 
	 * @param value value
	 * @return iterator
	 */
	abstract protected Iterator<E> iterator(T value);

	/**
	 * Collects items into the target type.
	 * 
	 * @param items items
	 * @return target value
	 */
	abstract protected T collect(Iterator<E> items);

	private final IuJsonAdapter<E> itemAdapter;

	/**
	 * Constructor
	 * 
	 * @param itemAdapter item adapter
	 */
	protected JsonArrayAdapter(IuJsonAdapter<E> itemAdapter) {
		this.itemAdapter = itemAdapter;
	}

	@Override
	public T fromJson(JsonValue jsonValue) {
		if (jsonValue == null //
				|| JsonValue.NULL.equals(jsonValue))
			return null;
		else if (itemAdapter != null)
			return collect(IuIterable.map(jsonValue.asJsonArray(), itemAdapter::fromJson).iterator());
		else
			return collect(IuIterable.map(jsonValue.asJsonArray(), IuJsonAdapter.<E>basic()::fromJson).iterator());
	}

	@Override
	public JsonValue toJson(T javaValue) {
		if (javaValue == null)
			return JsonValue.NULL;

		final var a = IuJson.array();
		iterator(javaValue).forEachRemaining(i -> {
			if (itemAdapter == null)
				a.add(IuJsonAdapter.of(i).toJson(i));
			else
				a.add(itemAdapter.toJson(i));
		});
		return a.build();
	}

}
