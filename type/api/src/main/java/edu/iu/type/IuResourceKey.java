package edu.iu.type;

/**
 * Defines the attributes that identify a {@link IuResource resource}.
 * 
 * @param <T> resource type
 */
public interface IuResourceKey<T> {

	/**
	 * Gets the resource name.
	 * 
	 * @return resource name
	 */
	String name();

	/**
	 * Gets the resource type
	 * 
	 * @return resource type
	 */
	IuType<?, T> type();

}
