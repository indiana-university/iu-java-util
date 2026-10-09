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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import edu.iu.IuException;
import edu.iu.validation.IuConstraintViolation;
import edu.iu.validation.IuValidationNode;

/**
 * One constraint failure.
 *
 * <p>
 * Attributes and the interpolated message are resolved eagerly. A violation is
 * only ever created for a value that actually failed, so this reflection is
 * paid on the error path rather than on every validated property.
 * </p>
 */
final class Violation implements IuConstraintViolation {

	private final Class<?> rootType;
	private final ConstraintDeclaration declaration;
	private final ValidationPath path;
	private final Object invalidValue;
	private final Map<String, Object> attributes;
	private final String message;

	/**
	 * Constructor.
	 *
	 * @param rootType     type the validation pass started from
	 * @param declaration  constraint that failed, and the type that declared it
	 * @param path         path from the root object to {@code invalidValue}
	 * @param invalidValue value that failed the constraint
	 */
	Violation(Class<?> rootType, ConstraintDeclaration declaration, ValidationPath path, Object invalidValue) {
		this.rootType = rootType;
		this.declaration = declaration;
		this.path = path;
		this.invalidValue = invalidValue;
		this.attributes = attributes(declaration.annotation());
		this.message = Messages.interpolate(this.attributes);
	}

	/**
	 * Reads every attribute an annotation declares.
	 *
	 * @param annotation constraint annotation
	 * @return unmodifiable attribute map, ordered by name so that a rendered report
	 *         does not depend on reflection order
	 */
	private static Map<String, Object> attributes(Annotation annotation) {
		final Map<String, Object> attributes = new TreeMap<>();

		for (final var attribute : annotation.annotationType().getDeclaredMethods())
			attributes.put(attribute.getName(), IuException.uncheckedInvocation(() -> attribute.invoke(annotation)));

		return Collections.unmodifiableMap(attributes);
	}

	@Override
	public String path() {
		return path.toString();
	}

	@Override
	public Iterable<? extends IuValidationNode> nodes() {
		return path.nodes();
	}

	@Override
	public Class<? extends Annotation> constraint() {
		return declaration.annotation().annotationType();
	}

	@Override
	public Map<String, Object> attributes() {
		return attributes;
	}

	@Override
	public String messageTemplate() {
		return String.valueOf(attributes.get("message"));
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public Object invalidValue() {
		return invalidValue;
	}

	@Override
	public Class<?> rootType() {
		return rootType;
	}

	@Override
	public Class<?> declaringType() {
		return declaration.declaringType();
	}

	/**
	 * Gets the constraint annotation, for identifying duplicate declarations of the
	 * same rule at the same path.
	 *
	 * @return constraint annotation
	 */
	Annotation annotation() {
		return declaration.annotation();
	}

	@Override
	public String toString() {
		return describe();
	}

}
