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

import java.beans.PropertyDescriptor;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.type.IuProperty;
import edu.iu.type.IuReferenceKind;

/**
 * Facade implementation of {@link IuProperty}.
 * 
 * @param <D> declaring type
 * @param <T> property type
 */
final class PropertyFacade<D, T> extends ElementBase implements IuProperty<D, T>, DeclaredAttribute<D, T> {

	private final PropertyDescriptor propertyDescriptor;
	private final TypeFacade<?, T> type;
	private final TypeFacade<?, D> declaringType;
	private final MethodFacade<D, T> read;
	private final MethodFacade<D, Void> write;

	/**
	 * Facade constructor.
	 * 
	 * @param propertyDescriptor    property descriptor
	 * @param typeTemplate          fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure is the property type
	 * @param declaringTypeTemplate fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure declared the property
	 */
	PropertyFacade(PropertyDescriptor propertyDescriptor, TypeTemplate<?, T> typeTemplate,
			TypeTemplate<?, D> declaringTypeTemplate) {
		this.propertyDescriptor = propertyDescriptor;
		this.type = new TypeFacade<>(typeTemplate, this, IuReferenceKind.PROPERTY, name());
		this.declaringType = new TypeFacade<>(declaringTypeTemplate, this, IuReferenceKind.DECLARING_TYPE);

		final Runnable postRead;
		final var read = propertyDescriptor.getReadMethod();
		if (read == null) {
			this.read = null;
			postRead = this::seal;
		} else {
			this.read = new MethodFacade<>(read, typeTemplate, declaringTypeTemplate);
			postRead = () -> this.read.postInit(this::seal);
		}

		final Runnable seal;
		final var write = propertyDescriptor.getWriteMethod();
		if (write == null) {
			Objects.requireNonNull(read);
			this.write = null;
			seal = postRead;
		} else {
			this.write = new MethodFacade<>(write, TypeFactory.resolveRawClass(Void.class), declaringTypeTemplate);
			seal = () -> this.write.postInit(postRead);
		}

		declaringTypeTemplate.postInit(() -> typeTemplate.postInit(seal));
	}

	@Override
	public String name() {
		return propertyDescriptor.getName();
	}

	@Override
	public TypeFacade<?, T> type() {
		checkSealed();
		return type;
	}

	@Override
	public TypeFacade<?, D> declaringType() {
		checkSealed();
		return declaringType;
	}

	@Override
	public MethodFacade<D, T> read() {
		checkSealed();
		return read;
	}

	@Override
	public MethodFacade<D, Void> write() {
		checkSealed();
		return write;
	}

	@Override
	public T get(Object o) {
		checkSealed();
		if (read == null)
			throw new IllegalStateException("Property " + name() + " is not readable for " + type);
		else
			return IuException.unchecked(() -> read.exec(o));
	}

	@Override
	public void set(Object o, T value) {
		checkSealed();
		if (write == null)
			throw new IllegalStateException("Property " + name() + " is not writable for " + type);
		else
			IuException.unchecked(() -> write.exec(o, value));
	}

	@Override
	public String toString() {
		if (declaringType == null)
			return "<uninitialized>";

		return TypeUtils.printType(declaringType().template.deref()) + "." + name() + ':'
				+ TypeUtils.printType(type.template.deref());
	}
	
}
