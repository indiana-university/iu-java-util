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
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 * Implementation of {@link IuType}.
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
	 * <em>Should</em> be called at the end of {@link TypeTemplate} initialization
	 * to propagate type arguments through the reference chain and prevent further
	 * modification.
	 * 
	 * @param typeParameters fully initialized parameters from the
	 *                       {@link TypeTemplate} owning the root reference to this
	 *                       facade
	 */
	public void sealTypeParameters(Map<String, TypeFacade<?>> typeParameters) {
		this.typeParameters = new LinkedHashMap<>();
		
		// step through template.typeParameters
		for (var templateTypeParameterEntry : template.typeParameters.entrySet()) {
			final var templateTypeParameterName = templateTypeParameterEntry.getKey();
			final var templateTypeArgument = templateTypeParameterEntry.getValue();

			final var templateGenericType = templateTypeArgument.deref();
			if (templateGenericType instanceof TypeVariable) {
				// attempt to map those that resolve to type variable ...
				var typeVariable = (TypeVariable<?>) templateGenericType;
				var typeVariableName = typeVariable.getName();
				var typeArgument = typeParameters.get(typeVariableName);
				if (typeArgument != null)
					// ... to a type argument from incoming typeParameters
					this.typeParameters.put(templateTypeParameterName, typeArgument);
				else
					// pass through as-is if not replaced by incoming parameter
					this.typeParameters.put(templateTypeParameterName, templateTypeArgument);
			} else
				// pass through as-is if generic type is not a variable
				this.typeParameters.put(templateTypeParameterName, templateTypeArgument);
		}
		this.typeParameters = Collections.unmodifiableMap(this.typeParameters);

		// TODO: REMOVE
//		Queue<TypeFacade<? super T>> hierarchy = new ArrayDeque<>();
//		template.postInit(() -> {
//			for (var superType : template.hierarchy())
//				hierarchy.offer(
//						new TypeFacade<>((TypeTemplate<? super T>) superType.template, this, IuReferenceKind.SUPER));
//		});
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
