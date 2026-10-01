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
package edu.iu.validation;

/**
 * Every constraint failure found by one validation pass.
 * 
 * <p>
 * Violations are ordered by {@link IuConstraintViolation#path() path}, then by
 * constraint name, so the same invalid object always produces the same report.
 * </p>
 * 
 * @see IuValidator#validate(Class, Object)
 */
public interface IuValidationResult extends Iterable<IuConstraintViolation> {

	/**
	 * Determines whether the pass found no violations.
	 * 
	 * @return true if there are no violations; else false
	 */
	boolean isValid();

	/**
	 * Gets the number of violations.
	 * 
	 * @return violation count; zero when {@link #isValid()}
	 */
	int count();

	/**
	 * Gets the type the validation pass started from.
	 * 
	 * @return root type; never null
	 */
	Class<?> rootType();

	/**
	 * Renders every violation as a multi-line report suitable for a log record,
	 * with invalid values redacted.
	 * 
	 * <p>
	 * For example:
	 * </p>
	 * 
	 * <pre>
	 * Invalid edu.iu.sis.enroll.EnrlHeader: 3 constraint violations
	 *   acadCareer       @Pattern  must match "\p{Alpha}{4}"
	 *   details[2].strm  @Size     size must be between 4 and 4
	 *   emplid           @NotNull  must not be null
	 * </pre>
	 * 
	 * @return multi-line report; a single line stating validity when
	 *         {@link #isValid()}
	 */
	default String report() {
		return report(false);
	}

	/**
	 * Renders every violation as a multi-line report suitable for a log record.
	 * 
	 * @param includeValues true to append each
	 *                      {@link IuConstraintViolation#invalidValue() invalid
	 *                      value} to its line; false to redact. Validated input is
	 *                      frequently sensitive, so pass true only for a
	 *                      destination that is cleared to receive it.
	 * @return multi-line report; a single line stating validity when
	 *         {@link #isValid()}
	 */
	String report(boolean includeValues);

	/**
	 * Throws if this pass found any violations.
	 * 
	 * @throws IuValidationException if {@link #isValid()} is false
	 */
	void checkValid() throws IuValidationException;

}
