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

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import edu.iu.IuVisitor;
import edu.iu.type.InstanceReference;
import edu.iu.type.IuAttribute;
import edu.iu.type.IuProperty;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;

/**
 * Implementation of {@link IuResource};
 * 
 * @param <R> referrer type
 * @param <T> resource type
 */
class ComponentResourceReference<R, T> implements IuResourceReference<R, T>, InstanceReference<R> {

	private final String name;
	private final TypeTemplate<?, T> type;
	private final IuAttribute<R, ? super T> attribute;
	private final Map<R, Optional<T>> unboundValues = new WeakHashMap<>();
	private final IuVisitor<R> visitor = new IuVisitor<>();
	private volatile IuResource<T> boundResource;

	/**
	 * Constructor.
	 * 
	 * @param attribute facade for the attribute backing the resource
	 * @param resource  resource annotation associated with the attribute
	 */
	@SuppressWarnings("unchecked")
	ComponentResourceReference(DeclaredAttribute<R, ? super T> attribute, Resource resource) {
		this.attribute = attribute;

		attribute.declaringType().template.subscribe(this);

		if (resource == null)
			throw new IllegalArgumentException("Missing @Resource: " + attribute);

		String name = resource.name();
		if (name.isEmpty())
			name = attribute.name();
		this.name = name;

		TypeTemplate<?, T> type;
		if (resource.type() != Object.class)
			type = TypeFactory.resolveRawClass((Class<T>) resource.type());
		else
			type = (TypeTemplate<?, T>) attribute.type().template;

		final var erased = type.erasedClass();
		if (!attribute.type().erasedClass().isAssignableFrom(erased))
			throw new IllegalArgumentException("attribute " + attribute + " is not assignable from " + type);

		this.type = type;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public TypeTemplate<?, T> type() {
		return type;
	}

	@Override
	public IuType<?, R> referrerType() {
		return attribute.declaringType();
	}

	@Override
	public boolean isBound() {
		return boundResource != null;
	}

	@Override
	public synchronized void bind(IuResource<T> resource) {
		if (resource != null //
				&& (!resource.name().equals(name()) //
						|| !type().erasedClass().isAssignableFrom(resource.type().erasedClass())))
			throw new IllegalArgumentException("Resource " + resource + " does not apply to " + this);

		boundResource = resource;

		visitor.visit(referrer -> {
			if (referrer != null) {
				final T resourceValue;
				if (boundResource == null)
					if (unboundValues.containsKey(referrer))
						resourceValue = unboundValues.get(referrer).orElse(null);
					else
						return null;
				else
					resourceValue = boundResource.get();

				attribute.set(referrer, resourceValue);
			}
			return null;
		});
	}

	@Override
	public synchronized void accept(R referrer) {
		if (visitor.visit(r -> r == referrer ? Optional.empty() : null) != null)
			return;

		if (!(attribute instanceof IuProperty property) //
				|| property.canRead())
			unboundValues.put(referrer, Optional.ofNullable(type.autoboxClass().cast(attribute.get(referrer))));

		if (boundResource != null)
			attribute.set(referrer, boundResource.get());

		visitor.accept(referrer);
	}

	@Override
	public T value(R referrer) {
		return type.autoboxClass().cast(attribute.get(referrer));
	}

	@Override
	public synchronized void clear(R referrer) {
		if (referrer == null)
			return;

		visitor.clear(referrer);

		if (unboundValues.containsKey(referrer))
			attribute.set(referrer, unboundValues.remove(referrer).orElse(null));
	}

	@Override
	public String toString() {
		return "ComponentResourceReference [name=" + name + ", type=" + type + ", attribute=" + attribute
				+ ", boundResource=" + boundResource + "]";
	}

}
