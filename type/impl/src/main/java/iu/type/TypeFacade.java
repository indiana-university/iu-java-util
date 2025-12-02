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
package iu.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Predicate;

import edu.iu.type.InstanceReference;
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
 * @param <D> declaring type
 * @param <T> generic type
 */
final class TypeFacade<D, T> extends ElementBase implements IuType<D, T>, ParameterizedFacade {

	/**
	 * Holds a reference to the template this facade delegates to.
	 */
	final TypeTemplate<D, T> template;

	/**
	 * Parameterized element mix-in.
	 * 
	 * <p>
	 * May be used by related components to apply type arguments to managed
	 * instances before sealing. Once sealed, the public interface (i.e.,
	 * {@link #typeParameter(String)}) is preferred.
	 * </p>
	 */
	final ParameterizedElement parameterizedElement = new ParameterizedElement();

	private final TypeReference<T, ?> reference;
	private Iterable<TypeFacade<?, ? super T>> hierarchy;

	/**
	 * Constructor for a non-named, non-indexed reference.
	 * 
	 * @param template      type template
	 * @param referrer      referrer element
	 * @param referenceKind reference kind
	 */
	TypeFacade(TypeTemplate<D, T> template, ElementBase referrer, IuReferenceKind referenceKind) {
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
	TypeFacade(TypeTemplate<D, T> template, ElementBase referrer, IuReferenceKind referenceKind, String referenceName) {
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
	TypeFacade(TypeTemplate<D, T> template, ElementBase referrer, IuReferenceKind referenceKind, int referenceIndex) {
		this(template, a -> new TypeReference<>(referenceKind, referrer, a, referenceIndex));
	}

	private TypeFacade(TypeTemplate<D, T> template, Function<TypeFacade<D, T>, TypeReference<T, ?>> referenceFactory) {
		this.template = template;
		reference = referenceFactory.apply(this);

		var referrer = reference.referrer();
		referrer.postInit(new Runnable() {
			{ // coverage assertion
				toString();
			}

			public void run() {
				template.postInit(() -> {
					final var templateHierarchy = template.hierarchy();
					Queue<TypeFacade<?, ? super T>> hierarchy = new ArrayDeque<>();
					for (final var templateSuperType : templateHierarchy)
						hierarchy.offer(new TypeFacade<>(templateSuperType.template, TypeFacade.this,
								templateSuperType.reference.kind()));
					TypeFacade.this.hierarchy = hierarchy;

					// i.e. resolve Iterable<T> from Collection<E> implements Iterable<E>
					// template: {T=IuType[E TYPE_PARAM(T) Iterable<E>]}
					parameterizedElement.apply(template.typeParameters());

					// referrer: {E=IuType[E TYPE_PARAM(E) Collection]}
					if (referrer instanceof ParameterizedFacade parameterizedReferrer)
						parameterizedElement.apply(parameterizedReferrer.typeParameters());

					parameterizedElement.seal(template.annotatedElement, template);
					seal();
				});
			}

			@Override
			public String toString() {
				return "TypeFacade-post(" + reference + ')';
			}

		});
	}

	@Override
	public IuTypeReference<T, ?> reference() {
		return reference;
	}

	@Override
	public Map<String, TypeFacade<?, ?>> typeParameters() {
		return parameterizedElement.typeParameters();
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
	public <S> IuType<D, ? extends S> sub(Class<S> subclass) throws ClassCastException {
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
	public IuType<?, D> declaringType() {
		return template.declaringType();
	}

	@Override
	public Type deref() {
		return template.deref();
	}

	@Override
	public IuType<D, T> erase() {
		return template.erase();
	}

	@Override
	public Class<T> erasedClass() {
		return template.erasedClass();
	}

	@Override
	public Iterable<TypeFacade<?, ? super T>> hierarchy() {
		checkSealed();
		return hierarchy;
	}

	@Override
	public IuType<?, ? super T> referTo(Type referentType) {
		return TypeUtils.referTo(this, hierarchy(), referentType);
	}

	@Override
	public Iterable<? extends IuType<T, ?>> enclosedTypes() {
		return template.enclosedTypes();
	}

	@Override
	public Iterable<? extends IuConstructor<T>> constructors() {
		return template.constructors();
	}

	@Override
	public Iterable<? extends IuField<? super T, ?>> fields() {
		return template.fields();
	}

	@Override
	public Iterable<? extends IuProperty<? super T, ?>> properties() {
		return template.properties();
	}

	@Override
	public Iterable<? extends IuMethod<? super T, ?>> methods() {
		return template.methods();
	}

	@Override
	public void observe(T instance) {
		template.observe(instance);
	}

	@Override
	public void destroy(T instance) {
		template.destroy(instance);
	}

	@Override
	public Runnable subscribe(InstanceReference<T> instanceReference) {
		return template.subscribe(instanceReference);
	}

	@Override
	public String toString() {
		return "IuType[" + reference + ']';
	}

}
