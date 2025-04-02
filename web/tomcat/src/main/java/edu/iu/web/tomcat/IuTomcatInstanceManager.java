package edu.iu.web.tomcat;

import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;

import org.apache.tomcat.InstanceManager;
//import org.apache.tomcat.websocket.pojo.PojoEndpointBase;

import edu.iu.type.IuType;

//import iu.util.IuCallHeader;
//import iu.util.IuResources;

public class IuTomcatInstanceManager implements InstanceManager {

//	private static final Method GET_POJO;
//
//	static {
//		try {
	// TODO: to use PojoEndpointBase, it seems we'll need to somehow make it
	// available
	// as org.apache.tomcat.websocket.pojo is not exported by the tomcat websocket
	// module.
	// Also, see note on destroyInstance method below. This may not be needed with
	// tomcat 11.
//			GET_POJO = PojoEndpointBase.class.getDeclaredMethod("getPojo");
//		} catch (NoSuchMethodException e) {
//			throw new ExceptionInInitializerError(e);
//		}
//		GET_POJO.setAccessible(true);
//	}

	private final ClassLoader loader;

	IuTomcatInstanceManager(ClassLoader loader) {
		this.loader = loader;
	}

	@Override
	public Object newInstance(String className)
			throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException,
			ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException {
		return newInstance(className, loader);
	}

	@Override
	public Object newInstance(String className, ClassLoader loader)
			throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException,
			ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException {
		return newInstance(loader.loadClass(className));
	}

	@Override
	public Object newInstance(Class<?> type) throws IllegalAccessException, InvocationTargetException, NamingException,
			InstantiationException, IllegalArgumentException, NoSuchMethodException, SecurityException {
		// TODO: TRY Resolving all the other issues besides the PojoEndpointBase issue.
		// See if that will allow the PojoEndpointBase issue to be resolved.
		// There may be a way to do the below with IuType or maybe a built-in Java
		// method.
//		Object o = IU.newInstance(type);
		final var iuType = IuType.of(type);
		final var o = iuType.erasedClass().getDeclaredConstructor().newInstance(new Object[0]);
		newInstance(o); // TODO: see note on this method below
		return o;
	}

	@Override
	public void newInstance(Object o) throws IllegalAccessException, InvocationTargetException, NamingException {
//		try {
//			// TODO: is there a 7.0 equivalent to this? Currently just trying to remove
//			// binding to get classes to compile.
//			IuResources.SPI.bound(new IuCallHeader() {
//				@Override
//				public String getDescription() {
//					return "newInstance " + o;
//				}
//
//				@Override
//				public ClassLoader getClassLoader() {
//					return loader;
//				}
//			}, () -> IuResources.SPI.addExternalBinding(o));
//		} catch (IllegalAccessException | InvocationTargetException | NamingException | RuntimeException | Error e) {
//			throw e;
//		} catch (Throwable e) {
//			throw new IllegalStateException(e);
//		}
	}

	@Override
	public void destroyInstance(Object o) throws IllegalAccessException, InvocationTargetException {
		// SIS-8976 - work around improper use of instance manager
		// by Tomcat web socket module.
		// TODO: re-evaluate with future Tomcat upgrades - see WsSession
		// As of Tomcat 9.0.54 this is still an issue
		Object instanceToDestroy;
//		if (o instanceof PojoEndpointBase)
//			instanceToDestroy = GET_POJO.invoke(o);
//		else
		instanceToDestroy = o;

//		try {
//			IuResources.SPI.bound(new IuCallHeader() {
//				@Override
//				public String getDescription() {
//					return "destroyInstance " + instanceToDestroy;
//				}
//
//				@Override
//				public ClassLoader getClassLoader() {
//					return loader;
//				}
//			}, () -> IuResources.SPI.destroyExternalBinding(instanceToDestroy));
//		} catch (IllegalAccessException | InvocationTargetException | RuntimeException | Error e) {
//			throw e;
//		} catch (Throwable e) {
//			throw new IllegalStateException(e);
//		}
	}

}
