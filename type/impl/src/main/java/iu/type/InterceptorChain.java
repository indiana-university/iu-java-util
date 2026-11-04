/*
 * Copyright © 2026 Indiana University
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.iu.type.DefaultInterceptor;
import edu.iu.type.IuAnnotatedElement;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.ExcludeClassInterceptors;
import jakarta.interceptor.ExcludeDefaultInterceptors;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.Interceptors;
import jakarta.interceptor.InvocationContext;

/**
 * Resolves and runs the interceptors that apply to a method or constructor.
 *
 * <p>
 * Interceptors run in the order defined by Jakarta Interceptors:
 * </p>
 * <ol>
 * <li>{@link DefaultInterceptor default interceptors}, unless
 * {@link ExcludeDefaultInterceptors excluded}, by class name</li>
 * <li>{@link Interceptors} declared by the target class, unless
 * {@link ExcludeClassInterceptors excluded} by the method</li>
 * <li>{@link Interceptors} declared by the method</li>
 * <li>for a method, {@link AroundInvoke} methods of the target class</li>
 * </ol>
 * <p>
 * Within an interceptor class, methods declared by superclasses run before
 * methods of subclasses, except where overridden. For a constructor,
 * {@link AroundConstruct} applies to interceptor classes only; one declared by
 * the target class itself is ignored. {@link jakarta.annotation.PostConstruct}
 * and {@link jakarta.annotation.PreDestroy} callbacks are never intercepted.
 * </p>
 */
final class InterceptorChain {

	/**
	 * An interceptor method.
	 *
	 * @param owner  interceptor class, or null for a method of the target class
	 * @param method interceptor method
	 */
	record Step(Class<?> owner, Method method) {
	}

	private record Snapshot(int version, List<Class<?>> interceptorClasses, List<Step> steps) {
	}

	private final Executable executable;
	private final IuAnnotatedElement element;
	private final IuAnnotatedElement declaringType;
	private final Class<?> targetClass;
	private final boolean construct;
	private volatile Snapshot snapshot;

	/**
	 * Constructor.
	 *
	 * @param executable    method or constructor
	 * @param element       facade for the method or constructor
	 * @param declaringType facade for the declaring type
	 * @param targetClass   declaring class
	 * @param construct     true to resolve {@link AroundConstruct}; false to
	 *                      resolve {@link AroundInvoke}
	 */
	InterceptorChain(Executable executable, IuAnnotatedElement element, IuAnnotatedElement declaringType,
			Class<?> targetClass, boolean construct) {
		this.executable = executable;
		this.element = element;
		this.declaringType = declaringType;
		this.targetClass = targetClass;
		this.construct = construct;
	}

	/**
	 * Determines if any interceptor applies.
	 *
	 * @return true if the invocation must run through {@link #invoke}
	 * @throws IllegalArgumentException if an applicable interceptor is not valid
	 */
	boolean isIntercepted() {
		return !snapshot().steps.isEmpty();
	}

	/**
	 * Invokes a method through its interceptors.
	 *
	 * @param target     target instance, null for a static method
	 * @param parameters method parameters
	 * @param terminal   invokes the method itself
	 * @return result
	 * @throws Exception from an interceptor or the method
	 */
	Object invoke(Object target, Object[] parameters, InvocationContextImpl.Terminal terminal) throws Exception {
		final var snapshot = snapshot();
		final var instances = instances(target, snapshot);
		return new InvocationContextImpl(executable, target, parameters, snapshot.steps, instances, terminal).proceed();
	}

	/**
	 * Invokes a constructor through its interceptors.
	 *
	 * @param parameters constructor parameters
	 * @param terminal   invokes the constructor itself, and provides the instance
	 *                   to the context
	 * @return constructed instance
	 * @throws Exception from an interceptor or the constructor
	 */
	Object construct(Object[] parameters, InvocationContextImpl.Terminal terminal) throws Exception {
		final var snapshot = snapshot();
		final Map<Class<?>, Object> instances = new HashMap<>();
		create(instances, snapshot);

		final var context = new InvocationContextImpl(executable, null, parameters, snapshot.steps, instances,
				ctx -> {
					final var instance = terminal.call(ctx);
					ctx.target(instance);
					return instance;
				});
		context.proceed();

		final var target = context.getTarget();
		if (target == null)
			throw new IllegalStateException("Interceptor did not proceed with " + executable);

		InterceptorInstances.adopt(target, instances);
		return target;
	}

	private Map<Class<?>, Object> instances(Object target, Snapshot snapshot) {
		final Map<Class<?>, Object> instances = target == null ? new HashMap<>() : InterceptorInstances.of(target);
		create(instances, snapshot);
		return instances;
	}

	private void create(Map<Class<?>, Object> instances, Snapshot snapshot) {
		for (final var interceptorClass : snapshot.interceptorClasses)
			if (!instances.containsKey(interceptorClass))
				instances.putIfAbsent(interceptorClass, InterceptorInstances.create(interceptorClass));
	}

	private Snapshot snapshot() {
		final var current = snapshot;
		if (current != null && current.version == DefaultInterceptors.version())
			return current;
		return snapshot = resolve();
	}

	private Snapshot resolve() {
		final var version = DefaultInterceptors.version();
		final List<Class<?>> interceptorClasses = new ArrayList<>();

		// lifecycle callbacks are not business methods, and never run through
		// @AroundInvoke
		if (!construct //
				&& (element.hasAnnotation(PostConstruct.class) || element.hasAnnotation(PreDestroy.class)))
			return new Snapshot(version, List.of(), List.of());

		final var isInterceptor = AnnotationBridge.isAnnotationPresent(Interceptor.class, targetClass)
				|| AnnotationBridge.isAnnotationPresent(DefaultInterceptor.class, targetClass);
		if (!isInterceptor //
				&& !element.hasAnnotation(ExcludeDefaultInterceptors.class)
				&& !declaringType.hasAnnotation(ExcludeDefaultInterceptors.class))
			interceptorClasses.addAll(DefaultInterceptors.applicableTo(targetClass));

		if (!element.hasAnnotation(ExcludeClassInterceptors.class)) {
			final var declared = declaringType.annotation(Interceptors.class);
			if (declared != null)
				for (final Class<?> c : declared.value())
					interceptorClasses.add(c);
		}

		final var onElement = element.annotation(Interceptors.class);
		if (onElement != null)
			for (final Class<?> c : onElement.value())
				interceptorClasses.add(c);

		final var annotationType = construct ? AroundConstruct.class : AroundInvoke.class;
		final List<Step> steps = new ArrayList<>();
		for (final var interceptorClass : interceptorClasses)
			for (final var method : aroundMethods(interceptorClass, annotationType))
				steps.add(new Step(interceptorClass, method));

		// @AroundConstruct has no effect on a target class, per Jakarta Interceptors
		if (!construct)
			for (final var method : aroundMethods(targetClass, AroundInvoke.class))
				steps.add(new Step(null, method));

		return new Snapshot(version, List.copyOf(interceptorClasses), List.copyOf(steps));
	}

	/**
	 * Finds the interceptor methods of a class, superclass methods first.
	 *
	 * @param c              class
	 * @param annotationType {@link AroundInvoke} or {@link AroundConstruct}
	 * @return interceptor methods
	 */
	private static List<Method> aroundMethods(Class<?> c, Class<? extends Annotation> annotationType) {
		final Deque<Class<?>> hierarchy = new ArrayDeque<>();
		for (var k = c; k != null && k != Object.class; k = k.getSuperclass())
			hierarchy.addFirst(k);

		final var classes = new ArrayList<>(hierarchy);
		final List<Method> methods = new ArrayList<>();
		for (var i = 0; i < classes.size(); i++) {
			Method declared = null;
			for (final var method : classes.get(i).getDeclaredMethods())
				if (!method.isSynthetic() && AnnotationBridge.isAnnotationPresent(annotationType, method)) {
					if (declared != null)
						throw new IllegalArgumentException(
								"At most one @" + annotationType.getSimpleName() + " method per class: " + classes.get(i));
					validate(method, annotationType);
					declared = method;
				}

			if (declared != null && !isOverridden(declared, classes.subList(i + 1, classes.size()))) {
				declared.setAccessible(true);
				methods.add(declared);
			}
		}
		return methods;
	}

	private static void validate(Method method, Class<? extends Annotation> annotationType) {
		final var parameterTypes = method.getParameterTypes();
		final var returnType = method.getReturnType();
		if (Modifier.isStatic(method.getModifiers()) //
				|| parameterTypes.length != 1 //
				// an isolated component may reference its own copy of the API
				|| !InvocationContext.class.getName().equals(parameterTypes[0].getName()) //
				|| (returnType != Object.class //
						&& !(annotationType == AroundConstruct.class && returnType == void.class)))
			throw new IllegalArgumentException(
					"@" + annotationType.getSimpleName() + " method must be non-static and match Object name(InvocationContext): "
							+ method);
	}

	private static boolean isOverridden(Method method, List<Class<?>> subclasses) {
		if (Modifier.isPrivate(method.getModifiers()))
			return false;

		// evaluates every declared method, so coverage doesn't depend on the order
		// reflection happens to return them
		var overridden = false;
		for (final var subclass : subclasses)
			for (final var declared : subclass.getDeclaredMethods())
				overridden |= declared.getName().equals(method.getName())
						&& Arrays.equals(declared.getParameterTypes(), method.getParameterTypes());
		return overridden;
	}

}
