package iu.logging.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxy from an external interface to an implementation class that follows the
 * same contract.
 */
public class IuLoggingProxy implements InvocationHandler {

	private final Object impl;

	private IuLoggingProxy(Object impl) {
		this.impl = impl;
	}

	/**
	 * Adapts an implementation to an external interface it follows the contract
	 * for.
	 * 
	 * @param <T>               external type
	 * @param externalInterface external interface
	 * @param impl              implementation
	 * @return target if instance of interface, else a proxy wrapper that translates
	 *         from the target to the interface
	 */
	public static <T> T adapt(Class<T> externalInterface, Object impl) {
		if (impl == null)
			return null;

		if (externalInterface.isInstance(impl))
			return externalInterface.cast(impl);

		if (Proxy.isProxyClass(impl.getClass())) {
			final var h = Proxy.getInvocationHandler(impl);
			if (h instanceof IuLoggingProxy)
				impl = ((IuLoggingProxy) h).impl;
		}

		return externalInterface.cast(Proxy.newProxyInstance(externalInterface.getClassLoader(),
				new Class<?>[] { externalInterface }, new IuLoggingProxy(impl)));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return impl.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(impl, args);
	}

}
