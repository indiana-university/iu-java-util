package edu.iu.type;

import java.lang.reflect.Parameter;

/**
 * Facade interface for a {@link Parameter}.
 * 
 * @param <T> field type
 */
public interface IuParameter<T> extends IuAnnotatedElement {

	/**
	 * Gets the executable element that declares the parameter.
	 * 
	 * @return declaring executable
	 */
	IuExecutable declaringExecutable();

	/**
	 * Gets the parameter index.
	 * 
	 * @return parameter index
	 */
	int index();

	/**
	 * Gets the parameter name.
	 * 
	 * @return parameter name
	 */
	int name();

	/**
	 * Gets the parameter type.
	 * 
	 * @return parameter type.
	 */
	IuType<T> type();

}
