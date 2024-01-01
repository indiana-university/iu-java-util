package edu.iu.transaction;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import edu.iu.IuException;
import jakarta.annotation.Resource;
import jakarta.annotation.Resources;

/**
 * Invocation handler supporting JTA 1 compliant transaction scenarios.
 */
@Resources({ //
		@Resource(type = TransactionManager.class), //
		@Resource(type = UserTransaction.class), //
		@Resource(type = TransactionSynchronizationRegistry.class), //
})
public class LegacyTransactionHandler implements InvocationHandler {

	private final Object delegate;

	// TODO FIXME
	
	/**
	 * Constructor.
	 */
	private LegacyTransactionHandler() {
		this(IuTransactionManager.getInstance());
	}
	
	private LegacyTransactionHandler(Object delegate) {
		this.delegate = delegate;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final var paramTypes = method.getParameterTypes();
		final var arg = new Object[paramTypes.length];
		for (var i = 0; i < paramTypes.length; i++)
			arg[i] = adapt(paramTypes[i], args[i]);

		final Object rv;
		try {
			rv = IuException.checkedInvocation(
	
				() -> IuTransactionManager.class.getMethod(method.getName(), paramTypes).invoke(delegate, arg));
		} catch (Throwable e) {
			final var errorClassName = e.getClass().getName();
			if (errorClassName.startsWith("jakarta.transaction.")) {
				Class.forName("javax".errorClassName.substring(7))
			}
		}
		
		final var returnType = method.getReturnType();
		final var name = returnType.getName();
		if (name.startsWith("javax.transaction.")) {
			return Proxy.newProxyInstance(LegacyTransactionHandler.class.getClassLoader(),
					new Class<?>[] { Class.forName("jakarta" + name.substring(5)) }, new LegacyTransactionHandler(rv));
		} else
			return rv;
	}

	private Object adapt(Class<?> type, Object object) throws ClassNotFoundException {
		final var name = type.getName();
		if (name.startsWith("javax.transaction.")) {
			type = Class.forName("jakarta" + name.substring(5));

			if (Proxy.isProxyClass(object.getClass()) // unwrap, don't re-wrap
					&& (Proxy.getInvocationHandler(object) instanceof LegacyTransactionHandler h) //
					&& type.isInstance(h.delegate))
				return h.delegate;

			return Proxy.newProxyInstance(LegacyTransactionHandler.class.getClassLoader(), new Class<?>[] { type },
					new LegacyTransactionHandler(object));
		} else
			return object;
	}

}
