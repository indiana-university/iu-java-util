package iu.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

/**
 * Hash key for mapping {@link Executable} instances.
 * 
 * @param name   method name; null for constructors
 * @param params parameter raw types
 */
record ExecutableKey(String name, Class<?>... params) {

	/**
	 * Gets a hash key for a constructor.
	 * 
	 * @param constructor constructor
	 * @return constructor hash key
	 */
	static ExecutableKey of(Constructor<?> constructor) {
		return new ExecutableKey(null, constructor.getParameterTypes());
	}

	/**
	 * Gets a hash key for a method.
	 * 
	 * @param method method
	 * @return method hash key
	 */
	static ExecutableKey of(Method method) {
		return new ExecutableKey(method.getName(), method.getParameterTypes());
	}
}
