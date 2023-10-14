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
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuConstructor;
import edu.iu.type.IuField;
import edu.iu.type.IuMethod;
import edu.iu.type.IuProperty;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

/**
 * Facade implementation of {@link IuType}.
 * 
 * <p>
 * Always includes a non-null {@link #reference()} for which
 * {@link IuTypeReference#referent()} == {@code this}.
 * </p>
 * 
 * <p>
 * Delegates most lookups to a {@link TypeTemplate}, but propagates independent
 * resolution of type parameters based on arguments provided via referrer, which
 * may or may not be the same {@link TypeTemplate} this facade delegates to.
 * </p>
 * 
 * <p>
 * Each facade instance is fully-formed and strictly immutable once
 * {@link #sealTypeParameters(Map) sealed}. Instantiation and parameter sealing
 * are separate steps in template initialization order, so temporary mutability
 * is necessary to prevent recursion loops; A {@link TypeFacade} passed from the
 * block that created it may be assumed immutable whether or not it was sealed.
 * </p>
 * 
 * @param <T> generic type
 */
final class TypeFacade<T> implements IuType<T> {

	/**
	 * Holds a reference to the template this facade delegates to.
	 */
	final TypeTemplate<T> template;

	private Map<String, TypeFacade<?>> typeParameters;
	private Iterable<TypeFacade<? super T>> hierarchy;
	private final TypeReference<T, ?> reference;

	/**
	 * Constructor for a non-named, non-indexed reference.
	 * 
	 * @param template      type template
	 * @param referrer      referrer element
	 * @param referenceKind reference kind
	 */
	TypeFacade(TypeTemplate<T> template, IuAnnotatedElement referrer, IuReferenceKind referenceKind) {
		this(template, a -> new TypeReference<>(referenceKind, referrer, a));
	}

	/**
	 * Constructor for a named reference.
	 * 
	 * @param template      type template
	 * @param referrer      referrer element
	 * @param referenceKind reference kind
	 * @param referenceName reference name
	 */
	TypeFacade(TypeTemplate<T> template, IuAnnotatedElement referrer, IuReferenceKind referenceKind,
			String referenceName) {
		this(template, a -> new TypeReference<>(referenceKind, referrer, a, referenceName));
	}

	/**
	 * Constructor for an indexed reference.
	 * 
	 * @param template       type template
	 * @param referrer       referrer element
	 * @param referenceKind  reference kind
	 * @param referenceIndex reference index
	 */
	TypeFacade(TypeTemplate<T> template, IuAnnotatedElement referrer, IuReferenceKind referenceKind,
			int referenceIndex) {
		this(template, a -> new TypeReference<>(referenceKind, referrer, a, referenceIndex));
	}

	private TypeFacade(TypeTemplate<T> template, Function<TypeFacade<T>, TypeReference<T, ?>> referenceFactory) {
		this.template = template;
		this.reference = referenceFactory.apply(this);
	}

	/**
	 * <em>Should</em> be called after referrer initialization to propagate type
	 * arguments through the reference chain and prevent further modification to
	 * this facade's internal state.
	 * 
	 * <p>
	 * Until this <em>optional</em> method is called, all type parameter lookups
	 * default to the template.
	 * </p>
	 * 
	 * @param typeArguments type arguments from the referrer
	 */
	void sealTypeParameters(Map<String, TypeFacade<?>> typeArguments) {
		if (this.typeParameters != null)
			throw new IllegalStateException("already sealed");

		this.typeParameters = TypeUtils.sealTypeParameters(template.typeParameters, typeArguments);
	}

	@Override
	public IuTypeReference<T, ?> reference() {
		return reference;
	}

	@Override
	public Map<String, TypeFacade<?>> typeParameters() {
		if (typeParameters == null)
			return template.typeParameters;
		else
			return typeParameters;
	}

	@Override
	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		return template.hasAnnotation(annotationType);
	}

	@Override
	public <A extends Annotation> A annotation(Class<A> annotationType) {
		return template.annotation(annotationType);
	}

	@Override
	public Iterable<? extends Annotation> annotations() {
		return template.annotations();
	}

	@Override
	public boolean permitted(Predicate<String> isUserInRole) {
		return template.permitted(isUserInRole);
	}

	@Override
	public boolean permitted() {
		return template.permitted();
	}

	@Override
	public <S> IuType<? extends S> sub(Class<S> subclass) throws ClassCastException {
		return template.sub(subclass);
	}

	@Override
	public Class<T> autoboxClass() {
		return template.autoboxClass();
	}

	@Override
	public T autoboxDefault() {
		return template.autoboxDefault();
	}

	@Override
	public String name() {
		return template.name();
	}

	@Override
	public IuType<?> declaringType() {
		return template.declaringType();
	}

	@Override
	public Type deref() {
		return template.deref();
	}

	@Override
	public IuType<T> erase() {
		return template.erase();
	}

	@Override
	public Class<T> erasedClass() {
		return template.erasedClass();
	}

	@Override
	public Iterable<TypeFacade<? super T>> hierarchy() {
		if (hierarchy == null)
			return template.hierarchy();
		else
			return hierarchy;
	}

	@Override
	public TypeFacade<? super T> referTo(Type referentType) {
		return TypeUtils.referTo(this, hierarchy(), referentType);
	}

	@Override
	public Iterable<? extends IuType<?>> enclosedTypes() {
		return template.enclosedTypes();
	}

	@Override
	public Iterable<? extends IuConstructor<T>> constructors() {
		return template.constructors();
	}

	@Override
	public Iterable<? extends IuField<?>> fields() {
		return template.fields();
	}

	@Override
	public Iterable<? extends IuProperty<?>> properties() {
		return template.properties();
	}

	@Override
	public Iterable<? extends IuMethod<?>> methods() {
		return template.methods();
	}

	@Override
	public String toString() {
		var sb = new StringBuilder("IuType[").append(TypeUtils.printType(template.deref()));
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
				sb.append(TypeUtils.printType(referrerType.template.deref()));
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
