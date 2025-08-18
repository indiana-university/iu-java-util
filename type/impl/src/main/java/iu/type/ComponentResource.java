/*
 * Copyright © 2025 Indiana University
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

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceKey;
import edu.iu.type.IuType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.annotation.Resource.AuthenticationType;
import jakarta.annotation.Resources;

/**
 * Implementation of {@link IuResource};
 * 
 * @param <T> resource type
 */
class ComponentResource<T> implements IuResource<T> {

	/**
	 * Creates a static web resource.
	 * 
	 * @param name name
	 * @param data content
	 * @return static web resource
	 */
	static ComponentResource<byte[]> createWebResource(String name, byte[] data) {
		return new ComponentResource<byte[]>(true, true, -1, name, TypeFactory.resolveRawClass(byte[].class),
				() -> data);
	}

	/**
	 * Gets all resources definitions tied to an implementation class
	 * 
	 * @param targetClass class to check
	 * @return resource definitions
	 */
	static Iterable<ComponentResource<?>> getResources(Class<?> targetClass) {
		Queue<ComponentResource<?>> componentResources = new ArrayDeque<>();

		var resources = AnnotationBridge.getAnnotation(Resources.class, targetClass);
		if (resources != null)
			for (var resourceReference : resources.value())
				if (isApplicationResource(resourceReference, targetClass))
					componentResources.add(createResource(resourceReference, targetClass));

		var resource = AnnotationBridge.getAnnotation(Resource.class, targetClass);
		if (resource != null)
			if (isApplicationResource(resource, targetClass))
				componentResources.add(createResource(resource, targetClass));

		return componentResources;
	}

	private static boolean isApplicationResource(Resource resourceReference, Class<?> classToCheck) {
		Class<?> resourceType = resourceReference.type();
		if (InvocationHandler.class.isAssignableFrom(classToCheck))
			return resourceType.isInterface();
		else
			return resourceType.isAssignableFrom(classToCheck);
	}

	private static ComponentResource<?> createResource(Resource resource, Class<?> targetClass) {
		final TypeTemplate<?, ?> type;
		if (InvocationHandler.class.isAssignableFrom(targetClass))
			type = TypeFactory.resolveRawClass(resource.type());
		else {
			Class<?> resourceClass = resource.type();
			if (resourceClass == Object.class) {
				for (var i : targetClass.getInterfaces())
					if (!IuObject.isPlatformName(i.getName())) {
						resourceClass = i;
						break;
					}
				if (resourceClass == Object.class)
					resourceClass = targetClass;
			}
			type = TypeFactory.resolveRawClass(resourceClass);
		}

		final String name;
		if (resource.name().isEmpty())
			name = IuResourceKey.getDefaultResourceName(type.erasedClass());
		else
			name = resource.name();

		final var priority = type.annotation(Priority.class);

		return new ComponentResource<>(resource.authenticationType().equals(AuthenticationType.CONTAINER),
				resource.shareable(), priority == null ? -1 : priority.value(), name, type,
				() -> IuException.unchecked(() -> TypeFactory.resolveRawClass(targetClass).constructor().exec()));
	}

	private final boolean needsAuthentication;
	private final boolean shared;
	private final int priority;
	private final String name;
	private final TypeTemplate<?, T> type;
	private volatile T singleton;
	private Supplier<?> factory;

	/**
	 * Constructor.
	 * 
	 * @param needsAuthentication whether or not authentication is needed
	 * @param shared              whether or not the resource is shared
	 * @param priority            initialization priority
	 * @param name                resource name
	 * @param type                resource type
	 * @param factory             factory for creating new instances
	 */
	ComponentResource(boolean needsAuthentication, boolean shared, int priority, String name, TypeTemplate<?, T> type,
			Supplier<?> factory) {
		this.needsAuthentication = needsAuthentication;
		this.shared = shared;
		this.priority = priority;
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
	public int priority() {
		return priority;
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
	public void postConstruct() {
		if (shared)
			type.annotatedMethods(PostConstruct.class).forEach(m -> IuException.unchecked(() -> m.exec(get())));
		else
			throw new IllegalStateException("not shared");
	}

	@Override
	public void preDestroy() {
		if (shared)
			type.annotatedMethods(PreDestroy.class).forEach(m -> IuException.unchecked(() -> m.exec(get())));
		else
			throw new IllegalStateException("not shared");
	}

	@Override
	public Supplier<?> factory() {
		return factory;
	}

	@Override
	public void factory(Supplier<?> factory) {
		this.factory = factory;
	}

	@Override
	public T get() {
		if (shared)
			synchronized (this) {
				if (singleton == null)
					singleton = create();
				return singleton;
			}
		else
			return create();
	}

	@Override
	public String toString() {
		return "ComponentResource [needsAuthentication=" + needsAuthentication + ", shared=" + shared + ", name=" + name
				+ ", type=" + type + "]";
	}

	private T create() {
		var type = type().erasedClass();
		var impl = factory.get();
		if (impl instanceof InvocationHandler)
			return type.cast(
					Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (InvocationHandler) impl));
		else
			return type.cast(impl);
	}

}
