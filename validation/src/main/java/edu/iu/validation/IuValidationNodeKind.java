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

/**
 * Enumerates the kinds of {@link IuValidationNode} that can appear in a
 * {@link IuConstraintViolation#path() violation path}.
 */
public enum IuValidationNodeKind {

	/**
	 * The root object the validation pass started from.
	 * 
	 * <p>
	 * Contributes nothing to the rendered path. A violation whose path is empty
	 * therefore describes the root object itself.
	 * </p>
	 */
	BEAN,

	/**
	 * A bean property.
	 * 
	 * <p>
	 * Renders as the property {@link IuValidationNode#name() name}, preceded by
	 * {@code .} unless it is the first node in the path.
	 * </p>
	 */
	PROPERTY,

	/**
	 * One element of an {@link Iterable} or array property.
	 * 
	 * <p>
	 * Renders as {@code [}{@link IuValidationNode#index() index}{@code ]}. The
	 * index is the element's position in iteration order, so for an unordered
	 * collection it distinguishes elements without identifying them stably
	 * across passes.
	 * </p>
	 */
	ITERABLE_ELEMENT,

	/**
	 * One value of a {@link java.util.Map} property.
	 * 
	 * <p>
	 * Renders as {@code [}{@link IuValidationNode#key() key}{@code ]}.
	 * </p>
	 */
	MAP_VALUE,

	/**
	 * One key of a {@link java.util.Map} property.
	 * 
	 * <p>
	 * Renders as {@code [}{@link IuValidationNode#key() key}{@code ]<key>} &mdash;
	 * the trailing marker distinguishes a failure in the key itself from a failure
	 * in the value stored under it.
	 * </p>
	 */
	MAP_KEY;

}
