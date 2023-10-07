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
package iu.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import edu.iu.IuObject;
import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuConstructor;
import edu.iu.type.IuField;
import edu.iu.type.IuMethod;
import edu.iu.type.IuProperty;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

/**
 * Implementation of {@link IuType}.
 * 
 * @param <T> generic type
 */
class TypeFacade<T> implements IuType<T> {

	/**
	 * Type builder utility for use by {@link TypeFactory}.
	 * 
	 * @param <T> generic type
	 */
	static class Builder<T> {
		private final Type type;
		private final TypeFacade<T> erasureTemplate;

		private List<TypeFacade<? super T>> hierarchy;

		private Builder(Class<T> rawClass) {
			this.type = rawClass;
			this.erasureTemplate = null;
		}

		private Builder(Type type, TypeFacade<T> erasureTemplate) {
			assert !(type instanceof Class) : type;
			assert erasureTemplate.deref() instanceof Class : erasureTemplate;
			this.type = type;
			this.erasureTemplate = erasureTemplate;
		}

		/**
		 * Adds hierarchy templates to the builder. <em>May</em> only be called once.
		 * 
		 * @param hierarchy hierarchy templates.
		 * @return this
		 */
		Builder<T> hierarchy(List<TypeFacade<? super T>> hierarchy) {
			assert this.hierarchy == null;
			this.hierarchy = hierarchy;
			return this;
		}

		/**
		 * Gets the type facade instance.
		 * 
		 * @return type facade instance
		 */
		TypeFacade<T> build() {
			return new TypeFacade<>(this);
		}
	}

	/**
	 * Creates a builder for a raw class.
	 * 
	 * @param <T>      raw type
	 * @param rawClass raw class
	 * @return raw class facade builder
	 */
	static <T> Builder<T> builder(Class<T> rawClass) {
		return new Builder<>(rawClass);
	}

	/**
	 * Creates a builder for a generic type.
	 * 
	 * @param <T>             generic type
	 * @param type            generic type
	 * @param erasureTemplate facade for the raw type representing the generic
	 *                        type's erasure
	 * @return generic type facade builder
	 */
	static <T> Builder<T> builder(Type type, TypeFacade<T> erasureTemplate) {
		return new Builder<>(type, erasureTemplate);
	}

	private final Type type;
	private final TypeFacade<T> erasedType;
	private final TypeReference<T, ?> reference;
	private final List<TypeFacade<? super T>> hierarchy;

	private TypeFacade(TypeFacade<T> template, IuAnnotatedElement referrer, IuReferenceKind referenceKind) {
		this.type = template.type;
		this.erasedType = template.erasedType;
		this.hierarchy = template.hierarchy;
		this.reference = new TypeReference<>(referenceKind, referrer, this);
	}

	private TypeFacade(Builder<T> builder) {
		this.type = builder.type;
		reference = null;

		if (builder.erasureTemplate == null)
			this.erasedType = this;
		else
			this.erasedType = bindReference(builder.erasureTemplate, IuReferenceKind.ERASURE);

		if (builder.hierarchy == null)
			this.hierarchy = Collections.emptyList();
		else {
			List<TypeFacade<? super T>> hierarchy = new ArrayList<>(builder.hierarchy.size());
			for (var superType : builder.hierarchy)
				hierarchy.add(bindReference(superType, IuReferenceKind.SUPER));
			this.hierarchy = Collections.unmodifiableList(hierarchy);
		}
	}

	private <U> TypeFacade<U> bindReference(TypeFacade<U> referentTemplate, IuReferenceKind kind) {
		return new TypeFacade<U>(referentTemplate, this, kind);
	}

	@Override
	public IuTypeReference<T, ?> reference() {
		return reference;
	}

	@Override
	public String name() {
		return erasedClass().getName();
	}

	@Override
	public TypeFacade<T> erase() {
		return erasedType;
	}

	@Override
	public Type deref() {
		return type;
	}

	@Override
	public boolean permitted(Predicate<String> isUserInRole) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuType<?> declaringType() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Map<Class<? extends Annotation>, ? extends Annotation> annotations() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Map<String, ? extends IuType<?>> typeParameters() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public List<TypeFacade<? super T>> hierarchy() {
		return hierarchy;
	}

	@Override
	public IuType<? super T> referTo(Type referentType) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<? extends IuType<?>> enclosedTypes() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<? extends IuConstructor<T>> constructors() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuConstructor<T> constructor(Type... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuConstructor<T> constructor(IuType<?>... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<? extends IuField<?>> fields() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuField<?> field(String name) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<? extends IuProperty<?>> properties() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuProperty<?> property(String name) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<? extends IuMethod<?>> methods() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuMethod<?> method(String name, Type... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuMethod<?> method(String name, IuType<?>... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public int hashCode() {
		if (erasedType == this)
			return type.hashCode();
		else
			return super.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		TypeFacade<?> other = (TypeFacade<?>) obj;
		return (erasedType == this //
				&& other.erasedType == other //
				&& this.type == other.type) //
				|| super.equals(obj);
	}

	@Override
	public String toString() {
		var sb = new StringBuilder("IuType[").append(type.toString());
		IuTypeReference<?, ?> ref = reference;
		while (ref != null) {
			sb.append(' ').append(ref.kind());
			if (ref.index() >= 0)
				sb.append('(').append(ref.index()).append(") ");
			else if (ref.name() != null)
				sb.append('(').append(ref.name()).append(") ");
			else
				sb.append(" ");

			var referrer = ref.referrer();

			if (referrer instanceof TypeFacade) {
				var referrerType = (TypeFacade<?>) referrer;
				sb.append(referrerType.type);
				ref = referrerType.reference;
			} else {
				sb.append(referrer);
				ref = null;
			}
		}
		sb.append(']');
		return sb.toString();
	}

}
