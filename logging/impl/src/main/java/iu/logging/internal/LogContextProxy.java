package iu.logging.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import iu.logging.LogContext;

/**
 * Translates calls between {@link LogContext} and any externally defined
 * interface following the same contract.
 */
public class LogContextProxy implements InvocationHandler {

	private final Object target;

	/**
	 * Constructor
	 * 
	 * @param target {@link LogContext} for an incoming request, or external
	 *               equivalent.
	 */
	LogContextProxy(Object target) {
		this.target = target;
	}

	/**
	 * Adapts a {@link LogContext} or external equivalent.
	 * 
	 * @param <T>    type
	 * @param ifc    interface
	 * @param target target
	 * @return target if instance of interface, else a proxy wrapper that translates
	 *         from the target to the interface
	 */
	public static <T> T adapt(Class<T> ifc, Object target) {
		if (target == null)
			return null;

		if (ifc.isInstance(target))
			return ifc.cast(target);

		if (Proxy.isProxyClass(target.getClass())) {
			final var h = Proxy.getInvocationHandler(target);
			if (h instanceof LogContextProxy)
				target = ((LogContextProxy) h).target;
		}

		return ifc.cast(
				Proxy.newProxyInstance(ifc.getClassLoader(), new Class<?>[] { ifc }, new LogContextProxy(target)));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return target.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(target, args);
	}

}
