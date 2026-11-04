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
package iu.validation;

import java.lang.reflect.Method;
import java.util.List;

import edu.iu.IuException;

/**
 * Everything the walk needs to know about one constrained bean property.
 * 
 * <p>
 * A property with no constraints and no cascade is not modeled at all, so the
 * walk never invokes a getter it has no rule for.
 * </p>
 * 
 * @param name               property name
 * @param readMethod         accessor to invoke; any declaration of the property
 *                           will do, since they all dispatch virtually to the
 *                           same implementation. Null for a standalone
 *                           annotation target described by {@link ElementModel},
 *                           whose value is supplied rather than read, so
 *                           {@link #value(Object)} is never called for one
 * @param constraints        constraints on the property value itself, unioned
 *                           over every declaration in the type hierarchy
 * @param elementConstraints constraints on each element of an {@link Iterable},
 *                           {@link java.util.Optional}, or {@link java.util.Map}
 *                           value, declared on the element type argument
 * @param keyConstraints     constraints on each key of a {@link java.util.Map}
 *                           value
 * @param cascade            true if {@link jakarta.validation.Valid} is declared
 *                           on the property
 * @param cascadeElements    true if {@link jakarta.validation.Valid} is declared
 *                           on the element type argument
 * @param cascadeKeys        true if {@link jakarta.validation.Valid} is declared
 *                           on the key type argument
 */
record BeanProperty(String name, Method readMethod, List<ConstraintDeclaration> constraints,
		List<ConstraintDeclaration> elementConstraints, List<ConstraintDeclaration> keyConstraints, boolean cascade,
		boolean cascadeElements, boolean cascadeKeys) {

	/**
	 * Reads this property from a bean.
	 * 
	 * @param bean instance declaring this property
	 * @return property value; may be null
	 */
	Object value(Object bean) {
		return IuException.uncheckedInvocation(() -> readMethod.invoke(bean));
	}

}
