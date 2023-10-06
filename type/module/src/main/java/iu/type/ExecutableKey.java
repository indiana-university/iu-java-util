package iu.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

record ExecutableKey(String name, Class<?>... params) {

	static ExecutableKey of(Constructor<?> constructor) {
		return new ExecutableKey(null, constructor.getParameterTypes());
	}

	static ExecutableKey of(Method method) {
		return new ExecutableKey(method.getName(), method.getParameterTypes());
	}
}
