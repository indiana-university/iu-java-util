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
package edu.iu.type;

/**
 * Facade interface for an attribute: a field or bean property.
 * 
 * @param <D> declaring type
 * @param <T> attribute value type
 */
public interface IuAttribute<D, T> extends IuNamedElement<D> {

	/**
	 * Gets the type.
	 * 
	 * @return type
	 */
	IuType<?, T> type();

	/**
	 * Gets the attribute value.
	 * 
	 * @param o object
	 * @return attribute value.
	 */
	T get(Object o);

	/**
	 * Gets the attribute value.
	 * 
	 * @param o     object
	 * @param value attribute value
	 */
	void set(Object o, T value);

	/**
	 * Determines whether or not the attribute should be included when serializing
	 * declaring type.
	 * 
	 * <p>
	 * Note that is check has nothing to do with the {@link java.io.Serializable}
	 * interface or any of its related types or behaviors. Java serialization
	 * streams <em>should not</em> be used by applications, and will not be
	 * supported by any IU Java Utilities or IU JEE modules.
	 * </p>
	 * 
	 * <p>
	 * Serialization in this context refers to a back-end, cache, or configuration
	 * storage scenario, as a check to verify that an attribute may be retrieved if
	 * stored from the same version of the type.
	 * </p>
	 * 
	 * @return True if the attribute should be included in serialized form; else
	 *         false
	 */
	boolean serializable();

}