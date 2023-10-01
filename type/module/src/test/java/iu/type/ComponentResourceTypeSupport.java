package iu.type;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import edu.iu.type.IuConstructor;
import edu.iu.type.IuType;

/**
 * Mimics type introspection behavior required by ComponentResource.
 */
public class ComponentResourceTypeSupport implements InvocationInterceptor {

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {
		try (var mockType = mockStatic(IuType.class)) {
			var mockTypes = new HashMap<Class<?>, IuType<?>>();
			mockType.when(() -> IuType.of(any())).then(a -> {
				Class c = (Class) a.getArgument(0);
				var type = mockTypes.get(c);
				if (type == null) {
					type = mock(IuType.class);
					when(type.baseClass()).thenReturn(c);
					var constructor = mock(IuConstructor.class);
					assertDoesNotThrow(() -> when(constructor.exec()).then(b -> {
						try {
							return c.getDeclaredConstructor(invocationContext.getTargetClass())
									.newInstance(invocationContext.getTarget().get());
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					}));
					when(type.constructor()).thenReturn(constructor);
					mockTypes.put(c, type);
				}
				return type;
			});
			invocation.proceed();
		}
	}

}
