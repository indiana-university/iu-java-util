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
package iu.type.container;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.function.Supplier;

import edu.iu.type.IuResource;
import edu.iu.type.IuType;

/**
 * Combines multiple resources.
 * 
 * @param <T> resource type
 * @param <I> item type
 */
class CompoundResource<T, I> implements IuResource<T> {

	private final String name;
	private final IuType<?, T> type;
	private final boolean shared;
	private final boolean needsAuthentication;
	private final int priority;

	private final IuType<?, I> itemType;
	private final Iterable<IuResource<I>> resources;

	/**
	 * Constructor.
	 * 
	 * @param name      resource name
	 * @param type      compound resource name
	 * @param resources individual resources to combine
	 */
	@SuppressWarnings("unchecked")
	CompoundResource(String name, IuType<?, T> type, Iterable<IuResource<I>> resources) {
		if (type.erasedClass() != Iterable.class)
			throw new IllegalArgumentException();

		itemType = (IuType<?, I>) type.typeParameter("T");
		final var erasedItemType = itemType.erasedClass();

		boolean shared = true;
		boolean needsAuthentication = false;
		int priority = 0;
		for (final var resource : resources) {
			if (!erasedItemType.isAssignableFrom(resource.type().erasedClass()))
				throw new IllegalArgumentException("resource type mismatch " + resource + ", expected " + itemType);
			
			if (!resource.shared())
				shared = false;
			if (resource.needsAuthentication())
				needsAuthentication = true;

			final var resourcePriority = resource.priority();
			if (TypeContainerResource.comparePriority(resourcePriority, priority) > 0)
				priority = resourcePriority;
		}
		this.shared = shared;
		this.needsAuthentication = needsAuthentication;
		this.priority = priority;

		this.name = name;
		this.type = type;
		this.resources = resources;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public IuType<?, T> type() {
		return type;
	}

	@Override
	public boolean needsAuthentication() {
		return needsAuthentication;
	}

	@Override
	public boolean shared() {
		return shared;
	}

	@Override
	public int priority() {
		return priority;
	}

	@Override
	public Supplier<?> factory() {
		return this::get;
	}

	@Override
	public void factory(Supplier<?> factory) {
		throw new UnsupportedOperationException();
	}

	@Override
	public T get() {
		final Queue<I> instances = new ArrayDeque<>();
		resources.forEach(resource -> instances.offer(resource.get()));
		return type.erasedClass().cast(Collections.unmodifiableCollection(instances));
	}

}
