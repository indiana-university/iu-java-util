package iu.auth.session;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;


/**
 * Holds Session attributes 
 */
public class SessionDetail implements InvocationHandler {
	
	/** session attributes */ 
	final Map<String, Object> attributes;
	
	/** session */	
	final Session session;

	/**
	 * Constructor
	 * @param attributes attributes
	 * @param session session
	 */
	public SessionDetail(Map<String, Object> attributes, Session session) {
		this.attributes = attributes;
		this.session = session;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final var methodName = method.getName();

		if (methodName.equals("hashCode")) {
			return System.identityHashCode(proxy); 
		}

		if (methodName.equals("equals")) {
			return args[0] == proxy;
		}

		if (methodName.equals("toString")) {
			return attributes.toString();
		}

		final String key;
		if (methodName.startsWith("get")) {
			key = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
			return attributes.get(key);
		}
		else if (methodName.startsWith("is")) {
			key = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
			return attributes.get(key);
		}

		if (methodName.startsWith("set") && args != null && args.length == 1) {
			key = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
			if (attributes.containsKey(key)) {
				if (args[0] != attributes.get(key)) {
					attributes.put(key, args[0]);
					session.setChange(true);
				}
				return null;
			}
			attributes.put(key, args[0]);
			session.setChange(true);
			return null;
		}

		throw new UnsupportedOperationException("Method " + methodName + " is not supported.");
	}


}

