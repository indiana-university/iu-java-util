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
package edu.iu.type;

import edu.iu.IuObject;

/**
 * Hash key for coordinating {@link IuResource} and {@link IuResourceReference}
 * instances.
 * 
 * @param <T> resource type
 */
public class IuResourceKey<T> {

	/**
	 * Gets the default name for a resource type.
	 * 
	 * <p>
	 * The default name:
	 * </p>
	 * <ul>
	 * <li>Starts with a lower case letter</li>
	 * <li>Matches the simple class name of the first non-platform interface
	 * implemented by the resource class, if present</li>
	 * <li>else matches the simple class name of the resource class</li>
	 * </ul>
	 * 
	 * @param type resource class
	 * @return default name
	 */
	public static String getDefaultResourceName(Class<?> type) {
		if (!type.isInterface())
			for (final var i : type.getInterfaces())
				if (!IuType.isPlatformType(i.getName())) {
					type = i;
					break;
				}
		StringBuilder sb = new StringBuilder(type.getSimpleName());
		sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
		return sb.toString();
	}

	/**
	 * Gets a resource key instance based on the type's default resource name.
	 * 
	 * @param <T>  resource type
	 * @param type resource class
	 * @return resource key
	 */
	public static <T> IuResourceKey<T> of(Class<T> type) {
		return new IuResourceKey<>(getDefaultResourceName(type), type);
	}

	/**
	 * Gets a resource key instance.
	 * 
	 * @param <T>  resource type
	 * @param name resource name
	 * @param type resource class
	 * @return resource key
	 */
	public static <T> IuResourceKey<T> of(String name, Class<T> type) {
		return new IuResourceKey<>(name, type);
	}

	/**
	 * Gets a resource key instance for a {@link IuResource}.
	 * 
	 * @param <T>      resource type
	 * @param resource resource
	 * @return resource key
	 */
	public static <T> IuResourceKey<T> from(IuResource<T> resource) {
		return new IuResourceKey<>(resource.name(), resource.type().erasedClass());
	}

	/**
	 * Gets a resource key instance for a {@link IuResourceReference}.
	 * 
	 * @param <T>               resource type
	 * @param resourceReference resource reference
	 * @return resource key
	 */
	@SuppressWarnings("unchecked")
	public static <T> IuResourceKey<T> from(IuResourceReference<?, ? extends T> resourceReference) {
		return new IuResourceKey<>(resourceReference.name(), (Class<T>) resourceReference.type().erasedClass());
	}

	private final String name;
	private final Class<T> type;

	/**
	 * Gets the resource name.
	 * 
	 * @return resource name
	 */
	public String name() {
		return name;
	}

	/**
	 * Gets the resource type
	 * 
	 * @return resource type
	 */
	public Class<T> type() {
		return type;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		IuResourceKey<?> other = (IuResourceKey<?>) obj;
		return IuObject.equals(name, other.name) && IuObject.equals(type, other.type);
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append(name);
		if (type != Object.class)
			sb.append('!').append(type.getName());
		return sb.toString();
	}

	private IuResourceKey(String name, Class<T> type) {
		this.name = name;
		this.type = type;
	}

}
