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

import java.util.function.Consumer;

/**
 * Manages <strong>reference</strong> to a {@link IuResource resource}.
 * 
 * @param <R> <strong>referrer</strong> type
 * @param <T> {@link IuResourceKey#type() resource type}
 */
public interface IuResourceReference<R, T> extends Consumer<R> {

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
	IuType<?, ? super T> type();

	/**
	 * Gets the <strong>referrer</strong> type.
	 * 
	 * @return <strong>referrer</strong> type
	 */
	IuType<?, R> referrerType();

	/**
	 * Determines whether or not the reference has been bound.
	 * 
	 * @return true if {@link #bind(IuResource) bound} with a non-null resource;
	 *         else false
	 */
	boolean isBound();

	/**
	 * Binds a {@link IuResource resource} to all <strong>referrer
	 * instances</strong>.
	 * 
	 * <p>
	 * A <strong>reference</strong> to the bound resource will be provided to all
	 * <strong>referrer instances</strong> {@link #accept(Object) accepted} prior to
	 * binding. Once bound, new <strong>referrer instances</strong> will be provided
	 * a <strong>reference</strong> to the {@link IuResource resource} when
	 * {@link #accept(Object) accepted}.
	 * </p>
	 * 
	 * @param resource {@link IuResource} to bind to the <strong>reference</strong>;
	 *                 may be null to unbind the reference
	 */
	void bind(IuResource<T> resource);

	/**
	 * Accepts a <strong>referrer instance</strong> as a {@link #bind(IuResource)
	 * binding target}.
	 * 
	 * @param referrer <strong>referrer instance</strong>
	 */
	@Override
	void accept(R referrer);

}
