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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import edu.iu.type.DefaultInterceptor;
import edu.iu.type.DefaultInterceptor.Scope;

/**
 * Registry of interceptor classes annotated {@link DefaultInterceptor}.
 *
 * <p>
 * Classes are registered when a component indexes them, and unregistered when
 * the component is closed, so the registry never retains a class from a closed
 * component.
 * </p>
 */
final class DefaultInterceptors {

	private record Registration(Class<?> interceptorClass, Scope scope) {
	}

	private static final List<Registration> REGISTRATIONS = new CopyOnWriteArrayList<>();
	private static final AtomicInteger VERSION = new AtomicInteger();

	private DefaultInterceptors() {
	}

	/**
	 * Registers the default interceptors among a set of classes.
	 *
	 * @param classes classes to inspect
	 * @return action to unregister the classes registered; null if none were
	 */
	static Runnable register(Iterable<Class<?>> classes) {
		final List<Registration> registered = new ArrayList<>();
		for (final var c : classes) {
			final var annotation = AnnotationBridge.getAnnotation(DefaultInterceptor.class, c);
			if (annotation != null)
				registered.add(new Registration(c, annotation.scope()));
		}

		if (registered.isEmpty())
			return null;

		REGISTRATIONS.addAll(registered);
		VERSION.incrementAndGet();
		return () -> {
			REGISTRATIONS.removeAll(registered);
			VERSION.incrementAndGet();
		};
	}

	/**
	 * Gets a version number that changes whenever registrations change.
	 *
	 * @return version
	 */
	static int version() {
		return VERSION.get();
	}

	/**
	 * Gets the default interceptor classes that apply to a target class.
	 *
	 * @param targetClass target class
	 * @return interceptor classes, ordered by name
	 */
	static List<Class<?>> applicableTo(Class<?> targetClass) {
		final List<Class<?>> applicable = new ArrayList<>();
		for (final var registration : REGISTRATIONS)
			if (applies(registration, targetClass))
				applicable.add(registration.interceptorClass);
		applicable.sort(Comparator.comparing(Class::getName));
		return applicable;
	}

	private static boolean applies(Registration registration, Class<?> targetClass) {
		final var interceptorClass = registration.interceptorClass;
		switch (registration.scope) {
		case MODULE:
			return interceptorClass.getModule() == targetClass.getModule();
		case CLASS_LOADER:
			return interceptorClass.getClassLoader() == targetClass.getClassLoader();
		default:
			final var interceptorLoader = interceptorClass.getClassLoader();
			for (var loader = targetClass.getClassLoader(); loader != null; loader = loader.getParent())
				if (loader == interceptorLoader)
					return true;
			return false;
		}
	}

}
