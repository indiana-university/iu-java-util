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
package edu.iu.dao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares effective-dated entity behavior for generated queries.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EffectiveDated {

	/**
	 * Additional correlated columns that are not discovered from mapped id properties.
	 *
	 * @return additional key columns
	 */
	String[] unmappedColumns() default {};

	/**
	 * Effective-dated column names.
	 *
	 * @return effective-dated columns
	 */
	String[] effectiveDatedColumns() default { "effdt" };

	/**
	 * Initial values used by legacy effective-dated update semantics.
	 *
	 * @return initial values
	 */
	String[] initialValues() default { "CURRENT_DATE" };

	/**
	 * Additional mapped key columns for correlated effective-date subqueries.
	 *
	 * @return additional key columns
	 */
	String[] additionalKeyColumns() default {};

	/**
	 * SQL expression used as the default as-of date.
	 *
	 * @return as-of date expression
	 */
	String asOfDate() default "CURRENT_DATE";

	/**
	 * Indicates that generated select statements should automatically constrain to the current row.
	 *
	 * @return {@code true} to apply current-row filtering automatically
	 */
	boolean currentOnly() default false;
}
