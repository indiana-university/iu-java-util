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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import iu.type.BackwardsCompatibilityHelper;

/**
 * Represents a property on a Java object, as defined by the default JavaBeans
 * convention.
 * 
 * <p>
 * This class is roughly equivalent to java.beans.PropertyDescriptor in the
 * java.desktop module, but without the additional overhead of the full
 * JavaBeans framework.
 * </p>
 * 
 * <p>
 * Properties managed by {@link IuType} are not customizable:
 * </p>
 * <ul>
 * <li>Read methods take no arguments and return the property value
 * <ul>
 * <li>Boolean read methods start with "is" followed by the capitalized property
 * name.</li>
 * <li>All other read methods start with "get" followed by the capitalized
 * property name.</li>
 * </ul>
 * </li>
 * <li>Write methods return {@code void}, take a single argument of the property
 * type, and are named "set" followed by the capitalized property name</li>
 * </ul>
 * 
 * @param <P> property type
 * 
 * @see IuType#property(String)
 */
public class IuProperty<P> implements IuAnnotatedElement, IuTypeAttribute<P> {

	static class Builder<P> {
		final IuType<P> type;
		final String name;
		private Method read;
		private Method write;

		Builder(IuType<P> type, String name) {
			this.type = type;
			this.name = name;
		}

		void read(Method read) {
			assert this.read == null : read + " " + read + " " + this.read;
			assert read.getParameterTypes().length == 0 : read + " " + type;
			assert read.getGenericReturnType().equals(type.deref()) : read + " " + type;
			this.read = read;
		}

		Method write() {
			return write;
		}

		void write(Method write) {
			assert this.write == null : type + " " + write + " " + this.write;
			assert write.getGenericParameterTypes().length == 1 : write + " " + type;
			assert write.getGenericParameterTypes()[0].equals(type.deref()) : write + " " + type;
			assert write.getReturnType() == Void.TYPE : write + " " + type;
			this.write = write;
		}

		IuProperty<P> build() {
			return new IuProperty<>(this);
		}
	}

	private final IuType<P> type;
	private final String name;
	private final Method read;
	private final Method write;
	private final Map<Class<? extends Annotation>, ? extends Annotation> annotations;
	private final boolean isTransient;
	private final boolean serializable;

	private IuProperty(Builder<P> builder) {
		this.type = builder.type;
		this.name = builder.name;
		this.read = builder.read;
		this.write = builder.write;

		boolean hasTransient = false;
		Map<Class<? extends Annotation>, Annotation> annotations = new LinkedHashMap<>();
		for (var method : new Method[] { write, read })
			if (method != null) // write first ^ allows read to override
				for (var annotationEntry : BackwardsCompatibilityHelper.getAnnotations(method).entrySet()) {
					var annotationType = annotationEntry.getKey();
					if (!hasTransient)
						// avoid hard references to supported upstream modules
						switch (annotationType.getName()) {
						case "java.beans.Transient":
						case "javax.persistence.Transient":
						case "jakarta.persistence.Transient":
						case "javax.xml.bind.annotation.XmlTransient": // deprecated support only
							hasTransient = true;
						}
					annotations.put(annotationEntry.getKey(), annotationEntry.getValue());
				}
		this.annotations = annotations;
		var isTransient = hasTransient || read == null;
		if (!isTransient)
			try {
				if (Modifier.isTransient(read.getDeclaringClass().getDeclaredField(name).getModifiers()))
					isTransient = true;
			} catch (NoSuchFieldException e) {
				isTransient = false; // redundant
			}
		this.isTransient = isTransient;
		serializable = !isTransient && (write != null || Collection.class.isAssignableFrom(read.getReturnType()));
	}

	/**
	 * Gets the type resolution wrapper.
	 * 
	 * @return type resolution wrapper
	 */
	@Override
	public IuType<P> type() {
		return type;
	}

	/**
	 * Gets the property name.
	 * 
	 * @return property name
	 */
	@Override
	public String name() {
		return name;
	}

	/**
	 * Determines if the property is readable.
	 * 
	 * @return true if the property is readable, else false.
	 */
	public boolean canRead() {
		return read != null;
	}

	/**
	 * Determines if the property is writable.
	 * 
	 * @return true if the property is writable, else false.
	 */
	public boolean canWrite() {
		return write != null;
	}

	/**
	 * Determines whether or not the property is transient.
	 * 
	 * <p>
	 * Transient properties should be excluded from processing when converting to
	 * serialized form to be sent to a remote system or server-side template
	 * processing mechanism. A property is considered transient if any of the
	 * following is true:
	 * </p>
	 * <ul>
	 * <li>The property is not readable</li>
	 * <li>An annotation of one of the following types is present. Note that only
	 * the classname of the annotation type is checked; there is no implicit
	 * dependency on any module that exports these types.
	 * <ul>
	 * <li>{@code java.beans.Transient}</li>
	 * <li>{@code javax.persistence.Transient}</li>
	 * <li>{@code javax.xml.bind.annotation.XmlTransient}</li>
	 * </ul>
	 * </li>
	 * <li>A field with the same name as the property exists on the read method's
	 * declaring class that has the {code transient} modifier.</li>
	 * </ul>
	 * <p>
	 * This method returning false does not imply that the property can be converted
	 * back to object form by same module.
	 * </p>
	 * 
	 * @return true if the property is transient, else false
	 * @see #isSerializable()
	 */
	public boolean isTransient() {
		return isTransient;
	}

	/**
	 * Determines whether or not the property should be included when converting to
	 * serialized form to be converted back to object form by the same module.
	 * 
	 * <p>
	 * This method return true if {@link #isTransient()} returns false and either of
	 * the following conditions is true.
	 * </p>
	 * <ul>
	 * <li>{@link #canWrite} returns true. The default behavior for converting back
	 * to object form is to invoke the write method with the incoming value.</li>
	 * <li>The property type's resolved base class implements the {@link Collection}
	 * interface. When {@link #canWrite} returns false, but the property type is a
	 * collection, then it is expected for the read method to return a non-null
	 * modifiable collection and for incoming values to be added to the collections
	 * via {@link Collection#add(Object)}.</li>
	 * </ul>
	 * 
	 * @return true if the property is serializable, else false
	 */
	public boolean isSerializable() {
		return serializable;
	}

	/**
	 * Gets an annotation defined for the property.
	 * 
	 * <p>
	 * An annotation is returned if present on either the read method or the write
	 * method.
	 * </p>
	 * <ol>
	 * <li>If defined on both methods, then only the read method annotation is
	 * returned.</li>
	 * <li>If not defined on either method, then null is returned.</li>
	 * </ol>
	 * 
	 * @param <A>             annotation type
	 * @param annotationClass annotation type
	 * @return annotation
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <A extends Annotation> A annotation(Class<A> annotationClass) {
		return (A) annotations.get(annotationClass);
	}

	@Override
	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		return annotations.containsKey(annotationType);
	}

	@Override
	public Map<Class<? extends Annotation>, ? extends Annotation> annotations() {
		return annotations;
	}

	/**
	 * Gets the property value from an object.
	 * 
	 * @param o non-null instance of the type declaring the property
	 * @return property value
	 */
	@Override
	public P get(Object o) {
		if (read == null)
			throw new IllegalStateException("No read method defined " + this);
		if (!read.canAccess(o))
			read.setAccessible(true);
		try {
			return type().autoboxClass().cast(read.invoke(o));
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw IuException.handleUnchecked(IuException.handleInvocation(e));
		}
	}

	/**
	 * Sets the property value on an object.
	 * 
	 * @param o     non-null instance of the type declaring the property
	 * @param value property value
	 */
	@Override
	public void set(Object o, P value) {
		if (write == null)
			throw new IllegalStateException("No write method defined " + this);
		if (!write.canAccess(o))
			write.setAccessible(true);
		try {
			write.invoke(o, value);
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw IuException.handleUnchecked(IuException.handleInvocation(e));
		}
	}

	@Override
	public String toString() {
		return "property [type=" + type + ", name=" + name + ", read=" + read + ", write=" + write + ", annotations="
				+ annotations + ", isTransient=" + isTransient + ", serializable=" + serializable + "]";
	}

}
