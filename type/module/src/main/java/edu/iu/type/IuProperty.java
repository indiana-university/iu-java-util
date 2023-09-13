package edu.iu.type;

import java.beans.PropertyDescriptor;
import java.beans.Transient;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

import iu.type.StaticDependencyHelper;

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
	IuMethod read();

	/**
	 * Gets a facade describing the property write method.
	 * 
	 * @return write method facade
	 */
	IuMethod write();

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
	default boolean isPrintSafe() {
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
	default boolean isSerializable() {
		return canRead() && canWrite() && !hasAnnotation(Transient.class);
	}

}
