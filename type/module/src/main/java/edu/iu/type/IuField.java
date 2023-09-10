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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Represents a field reflected from the base class of a generic type.
 * 
 * @param <F> field type
 */
public class IuField<F> extends IuMember<F, Field> implements IuTypeAttribute<F> {

	IuField(IuType<?> declaringType, IuType<F> type, Field field) {
		super(declaringType, type, field);
	}

	/**
	 * Gets the field name
	 * 
	 * @return field name
	 */
	@Override
	public String name() {
		return deref().getName();
	}

	/**
	 * Determines if the {@code transient} modifier is present on the field.
	 * 
	 * @return true if the transient modifier is missing; returns false if present.
	 */
	@Override
	public boolean isSerializable() {
		return Modifier.isTransient(deref().getModifiers());
	}

	/**
	 * Gets the value of a static field.
	 * 
	 * <p>
	 * The field must be public or accessible by the opening access to the
	 * {@code iu.util.object} module. Otherwise {@link IllegalStateException} is
	 * thrown.
	 * </p>
	 * 
	 * @return field value.
	 */
	public F get() {
		return get(null);
	}

	/**
	 * Gets the value of a field on a bound object.
	 * 
	 * <p>
	 * The field must be public or accessible by the opening access to the
	 * {@code iu.util.object} module. Otherwise {@link IllegalStateException} is
	 * thrown.
	 * </p>
	 * 
	 * @param o object
	 * @return field value.
	 */
	@Override
	public F get(Object o) {
		var field = deref();
		if (!field.canAccess(o))
			field.setAccessible(true);
		
		try {
			return this.type().baseClass().cast(field.get(o));
		} catch (IllegalAccessException e) {
			throw IuException.handleUnchecked(e);
		}
	}

	/**
	 * Sets the value of a static field.
	 * 
	 * <p>
	 * The field must be public or accessible by the opening access to the
	 * {@code iu.util.object} module. Otherwise {@link IllegalStateException} is
	 * thrown.
	 * </p>
	 * 
	 * @param value field value
	 * @throws ClassCastException If value is not an instance of the field type.
	 */
	public void set(F value) throws ClassCastException {
		set(null, value);
	}

	/**
	 * sets the value of a field on a bound object
	 * 
	 * <p>
	 * The field must be public or accessible by the opening access to the
	 * {@code iu.util.object} module. Otherwise {@link IllegalStateException} is
	 * thrown.
	 * </p>
	 * 
	 * @param o     object
	 * @param value field value
	 * @throws ClassCastException If value is not an instance of the field type.
	 */
	@Override
	public void set(Object o, F value) throws ClassCastException {
		var field = deref();
		if (!field.canAccess(o))
			field.setAccessible(true);
		try {
			field.set(o, value);
		} catch (IllegalAccessException e) {
			throw IuException.handleUnchecked(e);
		}
	}

	@Override
	public String toString() {
		return "field [" + deref() + "; " + type() + "]";
	}

}