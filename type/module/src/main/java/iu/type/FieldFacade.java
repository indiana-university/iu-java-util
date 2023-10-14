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

import java.lang.reflect.Field;

import edu.iu.type.IuField;
import edu.iu.type.IuReferenceKind;

/**
 * Facade implementation for {@link IuField}.
 *
 * @param <D> declaring type
 * @param <T> field type
 */
final class FieldFacade<D, T> extends AnnotatedElementBase<Field> implements IuField<T> {

	private final TypeFacade<T> type;
	private final TypeFacade<D> declaringType;

	/**
	 * Facade constructor.
	 * 
	 * @param typeTemplate          fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure is the field type
	 * @param declaringTypeTemplate fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure declared the field
	 * @param field                 {@link Field}
	 */
	FieldFacade(Field field, TypeTemplate<T> typeTemplate, TypeTemplate<D> declaringTypeTemplate) {
		super(field, null);
		assert field.getType() == typeTemplate.erasedClass();
		this.declaringType = new TypeFacade<>(declaringTypeTemplate, this, IuReferenceKind.DECLARING_TYPE);
		
		this.type = new TypeFacade<>(typeTemplate, this, IuReferenceKind.FIELD, field.getName());
		this.type.sealTypeParameters(declaringType.typeParameters());
	}

	@Override
	public TypeFacade<T> type() {
		return type;
	}

	@Override
	public TypeFacade<D> declaringType() {
		return declaringType;
	}

	@Override
	public String name() {
		return annotatedElement.getName();
	}

	@Override
	public T get(Object o) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void set(Object o, T value) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public boolean serializable() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String toString() {
		return TypeUtils.printType(declaringType.template.deref()) + "#" + name();
	}

}
