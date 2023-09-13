package iu.type.test;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import org.mockito.Mockito;

public final class TestUtil {

	public static <T> T mock(Class<T> type) {
		var mock = Mockito.mock(type);
		if (!type.isInterface())
			return mock;
		else
			return Mockito.spy(type.cast(
					Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
						if (method.isDefault())
							try {
								return MethodHandles.privateLookupIn(type, MethodHandles.lookup())
										.unreflectSpecial(method, type).bindTo(proxy).invokeWithArguments(args);
							} catch (UnsupportedOperationException e) {
							}

						try {
							return method.invoke(mock, args);
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					})));
	}

}
