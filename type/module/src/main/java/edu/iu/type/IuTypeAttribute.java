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
 * Represents a attribute of an object that may be accessed or modified.
 *
 * @param <T> attribute type
 */
public interface IuTypeAttribute<T> {

	/**
	 * Gets the attribute name.
	 * 
	 * @return attribute name
	 */
	String name();

	/**
	 * Gets the attribute type.
	 * 
	 * @return attribute type
	 */
	IuType<T> type();

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
	 * Determines whether or not the attribute should be included when converting to
	 * serialized form to be converted back to object form by the same module.
	 * 
	 * @return true if the attribute is serializable, else false
	 * @see IuField#isSerializable()
	 * @see IuProperty#isSerializable()
	 */
	boolean isSerializable();

}