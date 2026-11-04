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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import edu.iu.validation.IuValidationResult;

/**
 * Traverses an object graph, applying the constraints {@link BeanModel} derives
 * for each type it reaches.
 *
 * <p>
 * Public so that {@code edu.iu.validation} can reach it from another package of
 * the same module. The package is not exported.
 * </p>
 */
public final class ValidationWalk {

	/** One bean waiting to be validated, and the path it was reached by. */
	private record Frame(Object bean, ValidationPath path) {
	}

	/**
	 * Validates an object graph.
	 *
	 * <p>
	 * Traversal is breadth-first over an explicit queue rather than recursive, so a
	 * deeply nested graph costs heap instead of stack and needs no depth limit. A
	 * bean is validated at most once per pass, identified by instance identity,
	 * which terminates cycles.
	 * </p>
	 *
	 * @param rootType type to report as the root of the pass
	 * @param value    object to validate; may be null
	 * @return validation result
	 * @throws NullPointerException if {@code rootType} is null
	 */
	public static IuValidationResult walk(Class<?> rootType, Object value) {
		return walk(rootType, ValidationPath.ROOT, value);
	}

	/**
	 * Validates an object graph, rooting every violation path at a name.
	 *
	 * <p>
	 * Reports {@code body.emplid} rather than {@code emplid}, for a caller that
	 * knows what the graph it is validating is called &mdash; a request entity, for
	 * instance.
	 * </p>
	 *
	 * @param rootType type to report as the root of the pass
	 * @param name     name to root every violation path at
	 * @param value    object to validate; may be null
	 * @return validation result
	 * @throws NullPointerException if {@code rootType} is null
	 */
	public static IuValidationResult walk(Class<?> rootType, String name, Object value) {
		return walk(rootType, ValidationPath.ROOT.property(name), value);
	}

	/**
	 * Validates one value against the constraints declared on a standalone
	 * annotation target.
	 *
	 * <p>
	 * The value is supplied rather than read, so {@code element} needs no accessor.
	 * Everything past the first evaluation is shared with the bean walk: container
	 * elements, {@link jakarta.validation.Valid} cascade, cycle detection, ordering,
	 * and deduplication all behave identically, so a cascading target reports
	 * {@code message.street} with no special handling.
	 * </p>
	 *
	 * @param element annotation target
	 * @param value   value to validate; may be null
	 * @return validation result
	 * @throws IllegalArgumentException if {@code element} is not a supported target
	 */
	public static IuValidationResult walkElement(AnnotatedElement element, Object value) {
		final var model = ElementModel.of(element);
		return walk(model.declaringType(), model.constraints(), model.name(), value);
	}

	/**
	 * Validates one value against the constraints declared on a standalone
	 * annotation target, rooting the violation path at a supplied name rather than
	 * at the target's own.
	 *
	 * @param element annotation target
	 * @param name    name to root the violation path at
	 * @param value   value to validate; may be null
	 * @return validation result
	 * @throws IllegalArgumentException if {@code element} is not a supported target
	 */
	public static IuValidationResult walkElement(AnnotatedElement element, String name, Object value) {
		final var model = ElementModel.of(element);
		return walk(model.declaringType(), model.constraints(), name, value);
	}

	/**
	 * Validates one supplied value against a resolved set of constraints.
	 *
	 * @param rootType type to report as the root of the pass
	 * @param element  constraints declared on the target
	 * @param name     name to root the violation path at
	 * @param value    value to validate; may be null
	 * @return validation result
	 */
	private static IuValidationResult walk(Class<?> rootType, BeanProperty element, String name, Object value) {
		final List<Violation> violations = new ArrayList<>();
		final Deque<Frame> queue = new ArrayDeque<>();
		final var path = ValidationPath.ROOT.property(name);

		evaluate(rootType, element.constraints(), value, path, violations);
		descend(rootType, element, value, path, queue, violations);

		return drain(rootType, queue, violations);
	}

	/**
	 * Seeds a bean walk from one object and a path.
	 *
	 * @param rootType type to report as the root of the pass
	 * @param path     path to report {@code value} at
	 * @param value    object to validate; may be null
	 * @return validation result
	 * @throws NullPointerException if {@code rootType} is null
	 */
	private static IuValidationResult walk(Class<?> rootType, ValidationPath path, Object value) {
		Objects.requireNonNull(rootType, "rootType");

		final Deque<Frame> queue = new ArrayDeque<>();
		if (value != null)
			queue.add(new Frame(value, path));

		return drain(rootType, queue, new ArrayList<>());
	}

	/**
	 * Validates every queued bean, and everything they cascade into, until the
	 * queue empties.
	 *
	 * @param rootType   type to report as the root of the pass
	 * @param queue      beans awaiting validation
	 * @param violations violations found so far
	 * @return validation result
	 */
	private static IuValidationResult drain(Class<?> rootType, Deque<Frame> queue, List<Violation> violations) {
		final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

		while (!queue.isEmpty()) {
			final var frame = queue.poll();
			if (visited.add(frame.bean()))
				validate(rootType, frame, queue, violations);
		}

		return new ValidationResultImpl(rootType, violations);
	}

	/**
	 * Validates every constrained property of one bean.
	 *
	 * @param rootType   type to report as the root of the pass
	 * @param frame      bean to validate, and the path it was reached by
	 * @param queue      queue to add cascaded beans to
	 * @param violations list to collect violations into
	 */
	private static void validate(Class<?> rootType, Frame frame, Deque<Frame> queue, List<Violation> violations) {
		final var bean = frame.bean();

		for (final var property : BeanModel.of(bean.getClass()).properties()) {
			final var path = frame.path().property(property.name());
			final var value = property.value(bean);

			evaluate(rootType, property.constraints(), value, path, violations);
			descend(rootType, property, value, path, queue, violations);
		}
	}

	/**
	 * Applies a property value's container element constraints, and queues whatever
	 * it cascades into.
	 *
	 * @param rootType   type to report as the root of the pass
	 * @param property   property the value was read from
	 * @param value      property value
	 * @param path       path to {@code value}
	 * @param queue      queue to add cascaded beans to
	 * @param violations list to collect violations into
	 */
	private static void descend(Class<?> rootType, BeanProperty property, Object value, ValidationPath path,
			Deque<Frame> queue, List<Violation> violations) {
		if (value == null)
			return;

		if (value instanceof Optional<?> optional) {
			final var contained = optional.orElse(null);
			if (contained != null) {
				// an Optional holds exactly one container element, and is a wrapper rather
				// than a level of the graph, so that element keeps the property path: a
				// caller sees birthDate, not birthDate[0]
				evaluate(rootType, property.elementConstraints(), contained, path, violations);
				if (cascadesToElements(property))
					queue.add(new Frame(contained, path));
			}
			return;
		}

		if (value instanceof Map<?, ?> map) {
			descendMap(rootType, property, map, path, queue, violations);
			return;
		}

		final var elements = elements(value);
		if (elements == null) {
			if (property.cascade())
				queue.add(new Frame(value, path));
			return;
		}

		var index = 0;
		for (final var element : elements) {
			final var elementPath = path.element(index++);
			evaluate(rootType, property.elementConstraints(), element, elementPath, violations);
			if (element != null && cascadesToElements(property))
				queue.add(new Frame(element, elementPath));
		}
	}

	/**
	 * Applies a map value's key and value constraints, and queues whatever it
	 * cascades into.
	 *
	 * @param rootType   type to report as the root of the pass
	 * @param property   property the map was read from
	 * @param map        property value
	 * @param path       path to {@code map}
	 * @param queue      queue to add cascaded beans to
	 * @param violations list to collect violations into
	 */
	private static void descendMap(Class<?> rootType, BeanProperty property, Map<?, ?> map, ValidationPath path,
			Deque<Frame> queue, List<Violation> violations) {
		for (final var entry : map.entrySet()) {
			final var key = entry.getKey();

			final var keyPath = path.mapKey(key);
			evaluate(rootType, property.keyConstraints(), key, keyPath, violations);
			if (key != null && property.cascadeKeys())
				queue.add(new Frame(key, keyPath));

			final var mapped = entry.getValue();
			final var valuePath = path.mapValue(key);
			evaluate(rootType, property.elementConstraints(), mapped, valuePath, violations);
			if (mapped != null && cascadesToElements(property))
				queue.add(new Frame(mapped, valuePath));
		}
	}

	/**
	 * Determines whether a container property cascades into its elements.
	 *
	 * <p>
	 * True for both {@code @Valid List<Address>} and {@code List<@Valid Address>}:
	 * the specification defines the first as cascading to the elements rather than
	 * to the container, and the second says so directly.
	 * </p>
	 *
	 * @param property container property
	 * @return true if elements should be validated; else false
	 */
	private static boolean cascadesToElements(BeanProperty property) {
		return property.cascade() || property.cascadeElements();
	}

	/**
	 * Views a value as a sequence of elements.
	 *
	 * @param value property value
	 * @return the elements of an {@link Iterable} or array; null if {@code value} is
	 *         neither
	 */
	private static Iterable<?> elements(Object value) {
		if (value instanceof Iterable<?> iterable)
			return iterable;

		if (!value.getClass().isArray())
			return null;

		final var length = Array.getLength(value);
		final List<Object> elements = new ArrayList<>(length);
		for (var i = 0; i < length; i++)
			elements.add(Array.get(value, i));

		return elements;
	}

	/**
	 * Evaluates a set of constraints against one value.
	 *
	 * @param rootType     type to report as the root of the pass
	 * @param declarations constraints to evaluate
	 * @param value        value to check
	 * @param path         path to {@code value}
	 * @param violations   list to collect violations into
	 * @throws UnsupportedOperationException if a constraint cannot be applied to a
	 *                                       value of this type, restated to name
	 *                                       the offending path
	 */
	private static void evaluate(Class<?> rootType, List<ConstraintDeclaration> declarations, Object value,
			ValidationPath path, List<Violation> violations) {
		for (final var declaration : declarations) {
			final var constraint = declaration.annotation();
			final var rule = Constraints.rule(constraint.annotationType());

			final boolean valid;
			try {
				valid = rule.isValid(constraint, value);
			} catch (UnsupportedOperationException e) {
				throw new UnsupportedOperationException(
						'@' + constraint.annotationType().getSimpleName() + " cannot be applied to "
								+ rootType.getName() + '.' + path + ": " + e.getMessage(),
						e);
			}

			if (!valid)
				violations.add(new Violation(rootType, declaration, path, value));
		}
	}

	private ValidationWalk() {
	}

}
