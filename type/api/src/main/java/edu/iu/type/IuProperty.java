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
package edu.iu.type;

import java.beans.PropertyDescriptor;
import java.beans.Transient;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

import iu.type.api.StaticDependencyHelper;

/**
 * Facade interface for a bean property.
 * 
 * @param <T> property type
 * @see PropertyDescriptor
 */
public interface IuProperty<T> extends IuAttribute<T> {

	/**
	 * Gets a facade describing the property read method.
	 * 
	 * @return read method facade
	 */
	IuMethod<T> read();

	/**
	 * Gets a facade describing the property write method.
	 * 
	 * @return write method facade
	 */
	IuMethod<Void> write();

	/**
	 * Determines if the property is readable.
	 * 
	 * @return true if the property is readable, else false.
	 */
	default boolean canRead() {
		return read() != null;
	}

	/**
	 * Determines if the property is writable.
	 * 
	 * @return true if the property is writable, else false.
	 */
	default boolean canWrite() {
		return write() != null;
	}

	/**
	 * Determines if the property is neither transient nor restricted by a security
	 * role, and is therefore safe for one-way serialization to a log stream, public
	 * API, or other unrestricted destination.
	 * 
	 * <p>
	 * A print-safe property:
	 * </p>
	 * <ul>
	 * <li>Is {@link #canRead() readable}</li>
	 * <li>Does not {@link #hasAnnotation(Class) have} the {@link Transient}
	 * annotation</li>
	 * <li>One of
	 * <ul>
	 * <li>Read method {@link #hasAnnotation(Class) has} the
	 * {@link jakarta.annotation.security.PermitAll} annotation</li>
	 * <li>Declaring type {@link #hasAnnotation(Class) has} the
	 * {@link jakarta.annotation.security.PermitAll} annotation</li>
	 * </ul>
	 * </li>
	 * </ul>
	 * 
	 * @return true if the property may be printed
	 */
	default boolean printSafe() {
		return canRead() //
				&& !hasAnnotation(Transient.class) //
				&& (StaticDependencyHelper.hasPermitAll(read())
						|| StaticDependencyHelper.hasPermitAll(declaringType()));
	}

	/**
	 * Combines annotations from the read method and write method.
	 * 
	 * <p>
	 * If the same annotation type appears on both methods, the annotation from the
	 * read method <em>should</em> be included.
	 * </p>
	 * 
	 * @return annotations
	 */
	@Override
	default Map<Class<? extends Annotation>, ? extends Annotation> annotations() {
		Map<Class<? extends Annotation>, Annotation> annotations = new LinkedHashMap<>();

		var write = write();
		if (write != null)
			annotations.putAll(write.annotations());

		var read = read();
		if (read != null)
			annotations.putAll(read.annotations());

		return annotations;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * A property is considered serializable if it is {@link #canRead() readable},
	 * {@link #canWrite() writable}, and does not include the {@link Transient}
	 * annotation.
	 * 
	 * <p>
	 * Note that serializable is intended for back-end and/or cache storage.
	 * </p>
	 * 
	 * @return true if readable, writable, and not transient; else false
	 */
	@Override
	default boolean serializable() {
		return canRead() && canWrite() && !hasAnnotation(Transient.class);
	}

}
