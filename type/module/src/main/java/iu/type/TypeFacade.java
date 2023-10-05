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
import java.util.function.Predicate;

import edu.iu.type.IuConstructor;
import edu.iu.type.IuField;
import edu.iu.type.IuMethod;
import edu.iu.type.IuProperty;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

class TypeFacade<T> implements IuType<T> {

	// IuType#of(Type): All fields MUST be final
	private final Type type;
	private final IuTypeReference<T, ?> reference;
	private final TypeFacade<T> erasedType;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private TypeFacade(IuReferenceKind kind, TypeFacade<?> referrer, TypeFacade<T> referentTemplate) {
		this.type = referentTemplate.type;
		this.erasedType = referentTemplate.erasedType;
		this.reference = new TypeReference(kind, referrer, this, null, 0);
	}

	TypeFacade(Class<T> type) {
		this.type = type;
		this.reference = null;
		this.erasedType = this;
	}

	TypeFacade(Type type, TypeFacade<T> erasureTemplate) {
		assert !(type instanceof Class) : type;
		assert erasureTemplate.deref() instanceof Class : erasureTemplate;
		this.type = type;
		this.reference = null;
		this.erasedType = new TypeFacade<>(IuReferenceKind.ERASURE, this, erasureTemplate);
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
	public Iterable<? extends IuType<?>> hierarchy() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
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
	public IuConstructor<T> constructors(Type... parameterTypes) {
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
	public IuMethod<?> methods(String name, Type... parameterTypes) {
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
		return type.toString();
	}

}
