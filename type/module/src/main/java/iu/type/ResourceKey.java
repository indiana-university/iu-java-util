package iu.type;

import edu.iu.IuObject;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;

/**
 * Hash key implementation of {@link IuResourceReference}.
 * 
 * @param <T> resource type
 */
public class ResourceKey<T> {

	private final String name;
	private final TypeTemplate<?, T> type;

	/**
	 * Constructor.
	 * 
	 * @param name resource name
	 * @param type resource type
	 */
	public ResourceKey(String name, TypeTemplate<?, T> type) {
		this.name = name;
		this.type = type;
	}

	/**
	 * Gets the resource name.
	 * 
	 * @return resource name
	 */
	public String name() {
		return name;
	}

	/**
	 * Gets the resource type
	 * 
	 * @return resource type
	 */
	public IuType<?, T> type() {
		return type;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		ResourceKey<?> other = (ResourceKey<?>) obj;
		return IuObject.equals(name, other.name) && IuObject.equals(type, other.type);
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append(name);
		if (type.erasedClass() != Object.class)
			sb.append('!').append(type.erasedClass().getName());
		return sb.toString();
	}

}
