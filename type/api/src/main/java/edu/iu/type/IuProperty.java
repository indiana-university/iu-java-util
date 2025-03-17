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
package edu.iu.type;

import java.beans.PropertyDescriptor;
import java.beans.Transient;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Predicate;

/**
 * Facade interface for a bean property.
 * 
 * @param <D> declaring type
 * @param <T> property type
 * @see PropertyDescriptor
 */
public interface IuProperty<D, T> extends IuAttribute<D, T> {

	/**
	 * Gets a facade describing the property read method.
	 * 
	 * @return read method facade
	 */
	IuMethod<D, T> read();

	/**
	 * Gets a facade describing the property write method.
	 * 
	 * @return write method facade
	 */
	IuMethod<D, Void> write();

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
	 * <li>{@link IuExecutable#permitted() Permits} {@link #read() read method}
	 * execution.</li>
	 * </ul>
	 * 
	 * @return true if the property may be printed
	 */
	default boolean printSafe() {
		return canRead() //
				&& !hasAnnotation(Transient.class) //
				&& read().permitted();
	}

	/**
	 * {@inheritDoc} Combines permissions from both {@link #read()} and
	 * {@link #write} to determine read-write permission.
	 * 
	 * <p>
	 * A true return value from this method does not imply that the property is
	 * {@link #canRead() readable} or {@link #canWrite() writable}, but does imply
	 * that at least one of those is true.
	 * </p>
	 */
	@Override
	default boolean permitted(Predicate<String> isUserInRole) {
		var read = read();
		var write = write();
		return (read != null || write != null) //
				&& (read == null || read.permitted(isUserInRole)) //
				&& (write == null || write.permitted(isUserInRole));
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
	default Iterable<? extends Annotation> annotations() {
		Queue<Annotation> annotations = new ArrayDeque<>();

		var write = write();
		if (write != null)
			write.annotations().forEach(annotations::offer);

		var read = read();
		if (read != null)
			read.annotations().forEach(annotations::offer);

		return annotations;
	}

	@Override
	default <A extends Annotation> A annotation(Class<A> annotationType) {
		A annotation = null;

		final var write = write();
		if (write != null)
			annotation = write.annotation(annotationType);

		final var read = read();
		if (read != null) {
			final var readAnnotation = read.annotation(annotationType);
			if (annotation == null)
				annotation = readAnnotation;
			else if (readAnnotation != null && !readAnnotation.equals(annotation))
				throw new IllegalArgumentException(
						this + " defines unequal values for @" + annotationType + " on both read and write methods");
		}

		return annotation;
	}

	@Override
	default boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		final var write = write();
		if (write != null && write.hasAnnotation(annotationType))
			return true;

		final var read = read();
		return read != null && read.hasAnnotation(annotationType);
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
