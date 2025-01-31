package iu.type.container;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.function.Supplier;

import edu.iu.type.IuResource;
import edu.iu.type.IuType;

/**
 * Combines multiple resources.
 * 
 * @param <T> resource type
 * @param <I> item type
 */
class CompoundResource<T, I> implements IuResource<T> {

	private final String name;
	private final IuType<?, T> type;
	private final boolean shared;
	private final boolean needsAuthentication;
	private final int priority;

	private final IuType<?, I> itemType;
	private final Iterable<IuResource<I>> resources;

	/**
	 * Constructor.
	 * 
	 * @param name      resource name
	 * @param type      compound resource name
	 * @param resources individual resources to combine
	 */
	@SuppressWarnings("unchecked")
	CompoundResource(String name, IuType<?, T> type, Iterable<IuResource<I>> resources) {
		if (type.erasedClass() != Iterable.class)
			throw new IllegalArgumentException();

		itemType = (IuType<?, I>) type.typeParameter("T");
		final var erasedItemType = itemType.erasedClass();

		boolean shared = true;
		boolean needsAuthentication = false;
		int priority = 0;
		for (final var resource : resources) {
			if (!erasedItemType.isAssignableFrom(resource.type().erasedClass()))
				throw new IllegalArgumentException("resource type mismatch " + resource + ", expected " + itemType);
			
			if (!resource.shared())
				shared = false;
			if (resource.needsAuthentication())
				needsAuthentication = true;

			final var resourcePriority = resource.priority();
			if (TypeContainerResource.comparePriority(resourcePriority, priority) > 0)
				priority = resourcePriority;
		}
		this.shared = shared;
		this.needsAuthentication = needsAuthentication;
		this.priority = priority;

		this.name = name;
		this.type = type;
		this.resources = resources;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public IuType<?, T> type() {
		return type;
	}

	@Override
	public boolean needsAuthentication() {
		return needsAuthentication;
	}

	@Override
	public boolean shared() {
		return shared;
	}

	@Override
	public int priority() {
		return priority;
	}

	@Override
	public Supplier<?> factory() {
		return this::get;
	}

	@Override
	public void factory(Supplier<?> factory) {
		throw new UnsupportedOperationException();
	}

	@Override
	public T get() {
		final Queue<I> instances = new ArrayDeque<>();
		resources.forEach(resource -> instances.offer(resource.get()));
		return type.erasedClass().cast(Collections.unmodifiableCollection(instances));
	}

}
