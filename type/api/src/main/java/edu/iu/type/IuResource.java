/*
 * Copyright © 2024 Indiana University
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

/**
 * Facade interface for a resource declared by a {@link IuComponent component}.
 * 
 * @param <T> resource type
 */
public interface IuResource<T> extends Supplier<T> {

	/**
	 * Gets the resource name.
	 * 
	 * @return resource name
	 */
	String name();

	/**
	 * Gets the resource type
	 * 
	 * @return resource type
	 */
	IuType<?, T> type();

	/**
	 * Determines whether or not the resource should be authenticated before handing
	 * off to a managed application.
	 * 
	 * @return true if the resource requires authentication; else false
	 */
	boolean needsAuthentication();

	/**
	 * Determines whether or not the resource is shared.
	 * 
	 * @return true if the resource is shared; else false
	 */
	boolean shared();

	/**
	 * Indicates the initialization priority.
	 * 
	 * @return initialization priority
	 */
	int priority();

	/**
	 * Gets the factory to be used for creating new instances.
	 * 
	 * <p>
	 * By default, returns a {@link Supplier} that:
	 * </p>
	 * <ul>
	 * <li>Returns {@link IuType#autoboxDefault} if the {@link #type() resource
	 * type} is an immutable single-value</li>
	 * <li>Invokes {@link IuConstructor#exec(Object...)} on an an internally managed
	 * implementation class or {@link InvocationHandler} suitable for representing
	 * the resource</li>
	 * </ul>
	 * 
	 * @return factory that supplies new instance of the resource, or an
	 *         {@link InvocationHandler} for backing a {@link Proxy}.
	 */
	Supplier<?> factory();

	/**
	 * Provides a factory to use for creating new instances of the resource.
	 * 
	 * <p>
	 * The factory <em>may</em> return:
	 * </p>
	 * <ul>
	 * <li>A direct instance of the {@link #type() resource class}.</li>
	 * <li>A instance of a class that implements the {@link #type() resource
	 * interface}</li>
	 * <li>An {@link InvocationHandler} to back a {@link Proxy} for the
	 * {@link #type() resource interface}</li>
	 * </ul>
	 * 
	 * <p>
	 * To selective override default behavior, call {@link #factory()} first to get
	 * a reference to the default factory.
	 * </p>
	 * 
	 * @param factory resource implementation factory.
	 */
	void factory(Supplier<?> factory);

	/**
	 * Gets the resource value.
	 * 
	 * <p>
	 * Provides a new or immutable instance when {@link #shared()} returns false;
	 * <em>may</em> provide the same instance on subsequent calls when
	 * {@link #shared()} returns true.
	 * </p>
	 */
	@Override
	T get();

}
