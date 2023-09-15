package edu.iu.type;

/**
 * Facade for a resource from an {@link IuComponent}.
 * 
 * @param <T> resource type
 */
public interface IuResource<T> {

	/**
	 * Determines whether or not the resource is shared.
	 * 
	 * @return true if the resource is shared; else false
	 */
	boolean isShared();

	/**
	 * Gets the resource name.
	 * 
	 * @return resource name
	 */
	String name();

	/**
	 * Gets the resource type.
	 * 
	 * @return resource type
	 */
	IuType<T> type();

	/**
	 * Gets the resource instance.
	 * 
	 * <p>
	 * When {@link #isShared() shared}, returns the same singleton instance each
	 * time this method is invoked. When not shared, returns a new instance of the
	 * resource on each invocation.
	 * </p>
	 * 
	 * @return resource instance
	 */
	T get();

}
