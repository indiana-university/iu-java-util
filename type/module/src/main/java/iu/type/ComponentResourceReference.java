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

import edu.iu.IuVisitor;
import edu.iu.type.IuAttribute;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;

/**
 * Implementation of {@link IuResource};
 * 
 * @param <D> declaring type
 * @param <T> resource type
 */
class ComponentResourceReference<D, T> implements IuResourceReference<D, T> {

	private final ResourceKey<T> key;
	private final IuAttribute<D, ? super T> attribute;
	private final IuVisitor<D> visitor = new IuVisitor<>();
	private volatile IuResource<T> boundResource;

	/**
	 * Constructor.
	 * 
	 * @param attribute facade for the attribute backing the resource
	 */
	@SuppressWarnings("unchecked")
	ComponentResourceReference(DeclaredAttribute<D, ? super T> attribute) {
		this.attribute = attribute;

		attribute.declaringType().template.observeNewInstances(this);

		final var resource = attribute.annotation(Resource.class);
		if (resource == null)
			throw new IllegalArgumentException("Missing @Resource: " + attribute);

		String name = resource.name();
		if (name.isEmpty())
			name = attribute.name();

		TypeTemplate<?, ?> type;
		if (resource.type() != Object.class)
			type = TypeFactory.resolveRawClass(resource.type());
		else
			type = attribute.type().template;

		if (!attribute.type().erasedClass().isAssignableFrom(type.erasedClass()))
			throw new IllegalArgumentException("attribute " + attribute + " is not assignable from " + type);

		key = new ResourceKey<>(name, (TypeTemplate<?, T>) type);
	}

	@Override
	public String name() {
		return key.name();
	}

	@Override
	public IuType<?, T> type() {
		return key.type();
	}

	@Override
	public IuType<?, D> referrerType() {
		return attribute.declaringType();
	}

	@Override
	public synchronized void bind(IuResource<T> resource) {
		if (!resource.name().equals(name()) //
				|| !type().erasedClass().isAssignableFrom(resource.type().erasedClass()))
			throw new IllegalArgumentException("Resource " + resource + " does not apply to " + this);

		boundResource = resource;

		visitor.visit(referrer -> {
			if (referrer != null)
				attribute.set(referrer, boundResource.get());
			return null;
		});
	}

	@Override
	public synchronized void accept(D referrer) {
		if (boundResource != null)
			attribute.set(referrer, boundResource.get());
		visitor.accept(referrer);
	}

	@Override
	public String toString() {
		return "ComponentResourceReference [key=" + key + ", attribute=" + attribute + ", boundResource="
				+ boundResource + "]";
	}

}
