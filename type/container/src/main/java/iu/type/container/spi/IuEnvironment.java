package iu.type.container.spi;

/**
 * Provides environment property value overrides.
 */
public interface IuEnvironment {

	/**
	 * Resolves an environment property value.
	 * 
	 * @param <T>          value type
	 * @param name         property name
	 * @param type         value class
	 * @return property value
	 */
	<T> T resolve(String name, Class<T> type);

}
