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
package edu.iu.validation;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * One constraint failure: a value, where it was found, and which rule it failed.
 * 
 * @see IuValidationResult
 */
public interface IuConstraintViolation {

	/**
	 * Gets the rendered path from the validated root object to the invalid value,
	 * for example {@code details[2].strm} or {@code byCode[ABCD]<key>}.
	 * 
	 * <p>
	 * Always names at least one property: every constraint this module evaluates
	 * is declared on a property, so nothing fails at the root object itself. See
	 * {@link IuValidationNodeKind} for the rendering rule of each segment.
	 * </p>
	 * 
	 * @return dotted and bracketed property path; never null, never empty
	 */
	String path();

	/**
	 * Gets the same path as {@link #path()} in structured form, root first.
	 * 
	 * @return path nodes; never null, never empty
	 */
	Iterable<? extends IuValidationNode> nodes();

	/**
	 * Gets the constraint annotation type the value failed, for example
	 * {@link jakarta.validation.constraints.Pattern}.
	 * 
	 * @return constraint annotation type; never null
	 */
	Class<? extends Annotation> constraint();

	/**
	 * Gets the declared attributes of the constraint annotation, for example
	 * {@code regexp} and {@code flags} for
	 * {@link jakarta.validation.constraints.Pattern}.
	 * 
	 * <p>
	 * Includes {@code message}, {@code groups}, and {@code payload}. These are the
	 * values a JSON Schema or OpenAPI generator needs in order to translate the
	 * constraint into a schema keyword.
	 * </p>
	 * 
	 * @return unmodifiable attribute map keyed by annotation method name; never
	 *         null
	 */
	Map<String, Object> attributes();

	/**
	 * Gets the uninterpolated message template, as declared by the constraint's
	 * {@code message} attribute.
	 * 
	 * @return message template, for example
	 *         <code>{jakarta.validation.constraints.Pattern.message}</code>; never
	 *         null
	 */
	String messageTemplate();

	/**
	 * Gets the interpolated failure message, for example
	 * {@code must match "\p{Alpha}{4}"}.
	 * 
	 * @return human-readable message; never null
	 */
	String message();

	/**
	 * Gets the value that failed the constraint.
	 * 
	 * <p>
	 * Excluded from {@link #describe()} and from {@link IuValidationResult#report()}
	 * because validated input is frequently sensitive. Use
	 * {@link IuValidationResult#report(boolean)} to opt a rendered report into
	 * including values.
	 * </p>
	 * 
	 * @return invalid value; may be null, since {@link jakarta.validation.constraints.NotNull}
	 *         is failed by a null value
	 */
	Object invalidValue();

	/**
	 * Gets the type the validation pass started from.
	 * 
	 * @return root type; never null
	 */
	Class<?> rootType();

	/**
	 * Gets the type that declared the failed constraint.
	 * 
	 * <p>
	 * Differs from {@link #rootType()} when the constraint was inherited from a
	 * super-interface, or when the violation was found by cascading into a nested
	 * value.
	 * </p>
	 * 
	 * @return declaring type; never null
	 */
	Class<?> declaringType();

	/**
	 * Renders this violation as one line: path, constraint, message.
	 * 
	 * <p>
	 * Does not include {@link #invalidValue()}.
	 * </p>
	 * 
	 * @return single-line description
	 */
	default String describe() {
		return path() //
				+ " @" + constraint().getSimpleName() //
				+ ' ' + message();
	}

}
