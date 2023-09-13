package edu.iu.type;

import java.lang.reflect.Executable;
import java.util.Collection;
import java.util.List;

/**
 * Facade interface for an {@link Executable} element: a method or constructor.
 */
public interface IuExecutable extends IuDeclaredElement, IuParameterizedElement {

	/**
	 * Gets the parameters.
	 * 
	 * @return parameters
	 */
	List<IuParameter<?>> parameters();

	/**
	 * Gets a parameter type.
	 * 
	 * @param i index
	 * @return parameter type
	 */
	default IuParameter<?> parameter(int i) {
		return parameters().get(i);
	}

	/**
	 * Gets interceptors
	 * 
	 * @return interceptor classes
	 */
	Collection<?> interceptors();

}
