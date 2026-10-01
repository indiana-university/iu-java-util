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

import edu.iu.IuBadRequestException;

/**
 * Thrown when a validated object has at least one constraint violation.
 * 
 * <p>
 * Extends {@link IuBadRequestException} so an outbound web request boundary
 * already configured to answer that exception with {@code 400 BAD REQUEST}
 * needs no change. {@link #getMessage()} is the redacted
 * {@link IuValidationResult#report() report}, so the violations survive into a
 * log record even where nothing inspects {@link #getResult()}.
 * </p>
 */
public class IuValidationException extends IuBadRequestException {

	private static final long serialVersionUID = 1L;

	private final transient IuValidationResult result;

	/**
	 * Constructor.
	 * 
	 * @param result validation result; must have at least one violation
	 * @throws IllegalArgumentException if {@code result} is
	 *                                  {@link IuValidationResult#isValid() valid}
	 */
	public IuValidationException(IuValidationResult result) {
		super(result.report());
		if (result.isValid())
			throw new IllegalArgumentException("expected at least one violation");
		this.result = result;
	}

	/**
	 * Gets the violations that caused this exception.
	 * 
	 * @return validation result; null if this exception was deserialized, since the
	 *         result is not {@link java.io.Serializable}
	 */
	public IuValidationResult getResult() {
		return result;
	}

}
