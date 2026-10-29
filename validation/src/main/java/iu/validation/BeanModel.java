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
package iu.validation;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuObject;

/**
 * The constrained bean properties of one type.
 *
 * <p>
 * Properties are discovered the same way {@code edu.iu.client.IuJsonAdapter}
 * discovers them for JSON binding: {@link Introspector} at every level of the
 * type hierarchy, super-interfaces included, since {@link Introspector} walks
 * superclasses but not super-interfaces. Keeping the two in step is what lets a
 * generated schema describe exactly the properties that are validated.
 * </p>
 *
 * <p>
 * Constraints are <em>unioned</em> across the hierarchy rather than resolved to
 * the nearest declaration, because Jakarta Validation constraints are additive:
 * a constraint on an interface method and another on the implementing method
 * both apply. This also keeps the model correct for a dynamic proxy, whose own
 * methods carry no annotations at all. {@link ConstraintCollector} does that
 * accumulating.
 * </p>
 */
final class BeanModel {

	/**
	 * Derived models, keyed by type.
	 *
	 * <p>
	 * Time-limited and softly reachable rather than permanent: a model holds
	 * {@link Method} and {@link Annotation} instances that reference their
	 * declaring class, so a permanent cache would pin the {@link ClassLoader} of a
	 * redeployed application.
	 * </p>
	 */
	private static final Map<Class<?>, BeanModel> CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	/**
	 * Gets the model for a type, deriving it if it is not cached.
	 *
	 * @param type type to model
	 * @return bean model
	 */
	static BeanModel of(Class<?> type) {
		return CACHE.computeIfAbsent(type, BeanModel::new);
	}

	private final List<BeanProperty> properties;

	/**
	 * Derives the model for a type.
	 *
	 * @param type type to model
	 */
	private BeanModel(Class<?> type) {
		final Map<String, ConstraintCollector> collectors = new TreeMap<>();
		final Deque<Class<?>> todo = new ArrayDeque<>();
		final Set<Class<?>> visited = new HashSet<>();

		todo.push(type);
		while (!todo.isEmpty()) {
			final var next = todo.pop();
			if (!visited.add(next))
				continue;

			for (final var descriptor : IuException.unchecked(() -> Introspector.getBeanInfo(next))
					.getPropertyDescriptors()) {
				final var read = descriptor.getReadMethod();
				if (read == null || read.getDeclaringClass() == Object.class)
					continue;

				collectors.computeIfAbsent(descriptor.getName(), ConstraintCollector::new).merge(next, descriptor);
			}

			for (final var implemented : next.getInterfaces())
				todo.push(implemented);

			final var superclass = next.getSuperclass();
			if (superclass != null && !IuObject.isPlatformName(superclass.getName()))
				todo.push(superclass);
		}

		final List<BeanProperty> constrained = new ArrayList<>();
		for (final var collector : collectors.values()) {
			final var property = collector.build();
			if (property != null)
				constrained.add(property);
		}

		this.properties = Collections.unmodifiableList(constrained);
	}

	/**
	 * Gets the constrained properties of the modeled type, ordered by name.
	 *
	 * @return bean properties; empty if the type declares no constraints
	 */
	Iterable<BeanProperty> properties() {
		return properties;
	}

}
