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
package iu.type;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import edu.iu.IuException;
import edu.iu.type.IuField;
import edu.iu.type.IuReferenceKind;

/**
 * Facade implementation for {@link IuField}.
 *
 * @param <D> declaring type
 * @param <T> field type
 */
final class FieldFacade<D, T> extends DeclaredElementBase<D, Field> implements IuField<D, T>, DeclaredAttribute<D, T> {

	private final TypeFacade<?, T> type;

	/**
	 * Facade constructor.
	 * 
	 * @param typeTemplate          fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure is the field type
	 * @param declaringTypeTemplate fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure declared the field
	 * @param field                 {@link Field}
	 */
	FieldFacade(Field field, TypeTemplate<?, T> typeTemplate, TypeTemplate<?, D> declaringTypeTemplate) {
		super(field, typeTemplate.deref(), declaringTypeTemplate);
		assert field.getType() == typeTemplate.erasedClass();
		field.setAccessible(true);

		this.type = new TypeFacade<>(typeTemplate, this, IuReferenceKind.FIELD, field.getName());
		
		declaringTypeTemplate.postInit(() -> {
			this.type.parameterizedElement.apply(declaringTypeTemplate.typeParameters());
			this.seal();
		});
	}

	@Override
	public TypeFacade<?, T> type() {
		return type;
	}

	@Override
	public String name() {
		return annotatedElement.getName();
	}

	@Override
	public T get(Object o) {
		return type.autoboxClass().cast(IuException.unchecked(o, annotatedElement::get));
	}

	@Override
	public void set(Object o, T value) {
		IuException.unchecked(o, value, annotatedElement::set);
	}

	@Override
	public boolean serializable() {
		var mod = annotatedElement.getModifiers();
		return (mod | Modifier.TRANSIENT) != mod && (mod | Modifier.STATIC) != mod;
	}

	@Override
	public String toString() {
		if (declaringType == null || type == null)
			return "<uninitialized>";

		return TypeUtils.printType(declaringType.template.deref()) + "#" + name() + ':'
				+ TypeUtils.printType(type.template.deref());
	}

}
