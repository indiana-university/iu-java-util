/*
 * Copyright © 2023 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.type;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.UnsafeSupplier;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;
import jakarta.annotation.Resource.AuthenticationType;
import jakarta.annotation.Resources;

/**
 * Implementation of {@link IuResource};
 * 
 * @param <T> resource type
 */
class ComponentResource<T> implements IuResource<T> {

	private static final Logger LOG = Logger.getLogger(ComponentResource.class.getName());

	/**
	 * Creates a static web resource.
	 * 
	 * @param name name
	 * @param data content
	 * @return static web resource
	 */
	static ComponentResource<byte[]> createWebResource(String name, byte[] data) {
		return new ComponentResource<byte[]>(true, true, name, IuType.of(byte[].class), () -> data);
	}

	/**
	 * Determines if a resource annotation indicates an instance of a potential
	 * implementation class.
	 * 
	 * @param resourceReference {@link Resource} annotation
	 * @param classToCheck      class to check
	 * @return true if the class satisfies a requirement implied by the
	 *         {@link Resource}; else false
	 */
	static boolean isApplicationResource(Resource resourceReference, Class<?> classToCheck) {
		Class<?> resourceType = resourceReference.type();
		if (InvocationHandler.class.isAssignableFrom(classToCheck))
			return resourceType.isInterface();
		else
			return resourceType.isAssignableFrom(classToCheck);
	}

	/**
	 * Gets an instance of the resource.
	 * 
	 * @param <T>                   resource type
	 * @param resourceInterface     resource interface or raw implementation class
	 * @param implementationFactory supplies a resource implementation or
	 *                              {@link InvocationHandler} instance
	 * @return resource instance
	 */
	static <T> T createResourceInstance(Class<T> resourceInterface, UnsafeSupplier<?> implementationFactory) {
		var implementationInstance = IuException.unchecked(implementationFactory);
		if (implementationInstance instanceof InvocationHandler)
			return resourceInterface.cast(Proxy.newProxyInstance(resourceInterface.getClassLoader(),
					new Class<?>[] { resourceInterface }, (InvocationHandler) implementationInstance));
		else
			return resourceInterface.cast(implementationInstance);
	}

	/**
	 * Gets an instance of the resource.
	 * 
	 * @param resourceReference     resource annotation;
	 *                              {@link #isApplicationResource(Resource, Class)}
	 *                              <em>must</em> return true.
	 * @param implementationClass   implementation class or
	 *                              {@link InvocationHandler}
	 * @param implementationFactory supplies a resource implementation or
	 *                              {@link InvocationHandler} instance
	 * @return resource instance
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static ComponentResource<?> createResource(Resource resourceReference, Class<?> implementationClass,
			UnsafeSupplier<?> implementationFactory) {
		IuType type;
		if (InvocationHandler.class.isAssignableFrom(implementationClass)) {
			Class<?> resourceClass = resourceReference.type();
			if (!resourceClass.isInterface())
				throw new IllegalArgumentException(
						"Application resource defined by InvocationHandler requires resource type to be an interface");
			else
				type = IuType.of(resourceClass);
		} else {
			Class<?> resourceClass = resourceReference.type();
			if (resourceClass == Object.class) {
				for (var i : implementationClass.getInterfaces())
					if (!TypeUtils.isPlatformType(i.getName())) {
						resourceClass = i;
						break;
					}
				if (resourceClass == Object.class)
					resourceClass = implementationClass;
			}

			if (!resourceClass.isAssignableFrom(implementationClass))
				throw new IllegalArgumentException(
						"Application resource implementation class must be an InvocationHandler or assignable from resource type");
			else
				type = IuType.of(resourceClass);
		}

		var name = resourceReference.name();
		if (name.isEmpty())
			name = type.erasedClass().getSimpleName();

		Supplier supplier;
		if (resourceReference.shareable()) {
			var instance = createResourceInstance(type.erasedClass(), implementationFactory);
			supplier = () -> instance;
		} else
			supplier = () -> createResourceInstance(type.erasedClass(), implementationFactory);

		return new ComponentResource(resourceReference.authenticationType().equals(AuthenticationType.CONTAINER),
				resourceReference.shareable(), name, type, supplier);
	}

	/**
	 * Gets all resources definitions tied to an implementation class
	 * 
	 * @param implementationClass   implementation class
	 * @param implementationFactory supplies resource implementation or
	 *                              {@link InvocationHandler} instances
	 * @return resource definitions
	 */
	static Iterable<ComponentResource<?>> getResources(Class<?> implementationClass,
			UnsafeSupplier<?> implementationFactory) {
		Queue<ComponentResource<?>> resources = new ArrayDeque<>();

		var resourceReferences = AnnotationBridge.getAnnotation(Resources.class, implementationClass);
		if (resourceReferences != null)
			for (var resourceReference : resourceReferences.value())
				if (isApplicationResource(resourceReference, implementationClass))
					resources.add(createResource(resourceReference, implementationClass, implementationFactory));

		var resourceReference = AnnotationBridge.getAnnotation(Resource.class, implementationClass);
		if (resourceReference != null)
			if (isApplicationResource(resourceReference, implementationClass))
				try {
					resources.add(createResource(resourceReference, implementationClass, implementationFactory));
				} catch (Throwable e) {
					LOG.log(Level.CONFIG, e,
							() -> "Resource initialization failure; " + resourceReference + ' ' + implementationClass);
				}

		return resources;
	}

	private final boolean needsAuthentication;
	private final boolean shared;
	private final String name;
	private final IuType<?, T> type;
	private final Supplier<T> factory;

	private ComponentResource(boolean needsAuthentication, boolean shared, String name, IuType<?, T> type,
			Supplier<T> factory) {
		this.needsAuthentication = needsAuthentication;
		this.shared = shared;
		this.name = name;
		this.type = type;
		this.factory = factory;
	}

	@Override
	public boolean needsAuthentication() {
		return needsAuthentication;
	}

	@Override
	public boolean shared() {
		return shared;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public IuType<?, T> type() {
		return type;
	}

	@Override
	public T get() {
		return factory.get();
	}

	@Override
	public String toString() {
		return "ComponentResource [needsAuthentication=" + needsAuthentication + ", shared=" + shared + ", name=" + name
				+ ", type=" + type + "]";
	}

}
