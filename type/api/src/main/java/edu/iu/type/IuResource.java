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

/**
 * Facade interface for a resource in a {@link IuComponent}.
 * 
 * @param <T> resource type
 */
public interface IuResource<T> {

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
	 * Gets the resource name.
	 * 
	 * @return resource name
	 */
	String name();

	/**
	 * Gets the resource type.
	 * 
	 * @return resource type
	 */
	IuType<T> type();

	/**
	 * Gets the resource instance.
	 * 
	 * <p>
	 * When {@link #shared() shared}, returns the same singleton instance each time
	 * this method is invoked. When not shared, returns a new instance of the
	 * resource on each invocation.
	 * </p>
	 * 
	 * @return resource instance
	 */
	T get();

}
