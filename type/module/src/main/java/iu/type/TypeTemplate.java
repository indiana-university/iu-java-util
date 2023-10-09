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

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuConstructor;
import edu.iu.type.IuField;
import edu.iu.type.IuMethod;
import edu.iu.type.IuProperty;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

/**
 * Represents the internal structure of a {@link TypeFacade}.
 * 
 * <p>
 * Each template is a standalone representation of a single generic type,
 * potentially pair with a raw template
 * 
 * @param <T> raw or generic type
 */
sealed class TypeTemplate<T> extends ParameterizedElementBase<Class<T>> implements IuType<T> permits TypeFacade {

	/**
	 * Type builder utility for use by {@link TypeFactory}.
	 * 
	 * @param <T> generic type
	 */
	static class Builder<T> {
		private final Class<T> erasedClass;
		private final Type type;
		private final TypeTemplate<T> erasedType;
		private Iterable<TypeTemplate<? super T>> hierarchy;

		private Builder(Class<T> rawClass) {
			this.type = rawClass;
			this.erasedClass = rawClass;
			this.erasedType = null;
		}

		private Builder(Type type, TypeTemplate<T> erasedType) {
			assert !(type instanceof Class) : type;
			assert erasedType.deref() instanceof Class : erasedType;
			this.type = type;
			this.erasedClass = erasedType.erasedClass();
			this.erasedType = erasedType;

			Queue<TypeTemplate<? super T>> hierarchy = new ArrayDeque<>();
			for (var superType : erasedType.hierarchy)
				hierarchy.add(superType);
			this.hierarchy = hierarchy;
		}

		/**
		 * Adds hierarchy templates to the builder. <em>May</em> only be called once.
		 * 
		 * @param hierarchy hierarchy templates, arguments <em>must not</em> be modified
		 *                  after passing in.
		 * @return this
		 */
		Builder<T> hierarchy(Iterable<TypeTemplate<? super T>> hierarchy) {
			assert this.hierarchy == null : this.hierarchy + " " + hierarchy;
			this.hierarchy = hierarchy;
			return this;
		}

		/**
		 * Gets the type template instance.
		 * 
		 * @return type template instance
		 */
		TypeTemplate<T> build() {
			if (hierarchy == null)
				if (erasedClass != Object.class && !erasedClass.isInterface())
					throw new IllegalStateException("Missing hierarchy for " + erasedClass);
				else
					hierarchy = Collections.emptySet();

			return new TypeTemplate<>(erasedClass, type, erasedType, hierarchy);
		}
	}

	/**
	 * Creates a builder for a raw class.
	 * 
	 * @param <T>      raw type
	 * @param rawClass raw class
	 * @return raw class template builder
	 */
	static <T> Builder<T> builder(Class<T> rawClass) {
		return new Builder<>(rawClass);
	}

	/**
	 * Creates a builder for a generic type.
	 * 
	 * @param <T>             generic type
	 * @param type            generic type
	 * @param erasureTemplate raw type representing the generic type's erasure
	 * @return generic type facade builder
	 */
	static <T> Builder<T> builder(Type type, TypeTemplate<T> erasureTemplate) {
		return new Builder<>(type, erasureTemplate);
	}

	private final Type type;
	private final TypeTemplate<T> erasedType;
	private final Iterable<TypeFacade<? super T>> hierarchy;

	private TypeTemplate(Class<T> annotatedElement, Type type, TypeTemplate<T> erasedType,
			Iterable<TypeTemplate<? super T>> hierarchyTemplates) {
		super(annotatedElement);
		this.type = type;

		if (erasedType == null)
			this.erasedType = this;
		else
			this.erasedType = new TypeFacade<T>(erasedType, this, IuReferenceKind.ERASURE);

		TypeFacade<? super T> last = null;
		Map<Class<?>, TypeFacade<? super T>> hierarchyByErasure = new LinkedHashMap<>();
		for (var hierarchyTemplate : hierarchyTemplates) {
			var templateReference = hierarchyTemplate.reference();

			IuAnnotatedElement referrer;
			if (templateReference == null || templateReference.referrer() == hierarchyTemplate)
				referrer = this;
			else {
				TypeTemplate<?> referrerTemplate = (TypeTemplate<?>) templateReference.referrer();
				referrer = Objects.requireNonNull(hierarchyByErasure.get(referrerTemplate.erasedClass()));
			}

			last = new TypeFacade<>(hierarchyTemplate, referrer, IuReferenceKind.SUPER);

			var replaced = hierarchyByErasure.put(last.erasedClass(), last);
			assert replaced == null : replaced;
		}

		if (hierarchyByErasure.isEmpty()) {
			if (annotatedElement != Object.class && !annotatedElement.isInterface())
				throw new IllegalArgumentException("Missing hierarchy for " + annotatedElement);
		} else
			assert last.erasedClass() == Object.class : hierarchyByErasure;

		this.hierarchy = hierarchyByErasure.values();
	}

	/**
	 * Copy constructor, for extension by {@link TypeFacade}.
	 * 
	 * <p>
	 * Limited scope head recursion. <em>Must not</em> call
	 * {@code new TypeFacade(this, ...)}.
	 * </p>
	 * 
	 * @param copy template to copy from
	 */
	protected TypeTemplate(TypeTemplate<T> copy) {
		super(copy.annotatedElement);
		this.type = copy.type;

		if (copy.erasedType == copy)
			this.erasedType = this;
		else
			this.erasedType = new TypeFacade<>(copy.erasedType, this, IuReferenceKind.ERASURE);

		Map<Class<?>, TypeFacade<? super T>> hierarchyByErasure = new HashMap<>();
		Queue<TypeFacade<? super T>> hierarchy = new ArrayDeque<>();
		for (var copySuperType : copy.hierarchy) {
			@SuppressWarnings("unchecked")
			TypeTemplate<? super T> copyReferrer = (TypeTemplate<? super T>) copySuperType.reference().referrer();
			TypeTemplate<? super T> referrer;
			if (copyReferrer == copy)
				referrer = this;
			else
				referrer = Objects.requireNonNull(hierarchyByErasure.get(copyReferrer.erasedClass()));

			hierarchy.offer(new TypeFacade<>(copySuperType, referrer, IuReferenceKind.SUPER));
		}
		this.hierarchy = hierarchy;
	}

	@Override
	public String name() {
		return annotatedElement.getName();
	}

	@Override
	public IuType<?> declaringType() {
		return this;
	}

	@Override
	public IuTypeReference<T, ?> reference() {
		return null;
	}

	@Override
	public Type deref() {
		return type;
	}

	@Override
	public TypeTemplate<T> erase() {
		return erasedType;
	}

	@Override
	public Class<T> erasedClass() {
		return annotatedElement;
	}

	@Override
	public Iterable<TypeFacade<? super T>> hierarchy() {
		return hierarchy;
	}

	@Override
	public TypeFacade<? super T> referTo(Type referentType) {
		var erasedClass = TypeFactory.getErasedClass(referentType);
		for (var superType : hierarchy)
			if (superType.erasedClass() == erasedClass)
				return superType;
		throw new IllegalArgumentException(referentType + " present in type hierarchy for " + this);
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
	public String toString() {
		return "IuType[" + type + ']';
	}

}
