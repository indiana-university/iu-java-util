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
package iu.crypt;

import java.util.Objects;

import edu.iu.crypt.WebKey.Algorithm;

/**
 * Common base class for JSON web security object builders.
 * 
 * @param <B> builder type
 */
abstract class WebKeyReferenceBuilder<B extends WebKeyReferenceBuilder<B>> extends CertificateReferenceBuilder<B> {

	private String id;
	private Algorithm algorithm;

	/**
	 * Sets key ID
	 * 
	 * @param id key ID
	 * @return this
	 */
	public B id(String id) {
		Objects.requireNonNull(id);

		if (this.id == null)
			this.id = id;
		else if (!id.equals(this.id))
			throw new IllegalStateException("ID already set");

		return next();
	}

	/**
	 * Sets algorithm
	 * 
	 * @param algorithm
	 * @return this
	 */
	public B algorithm(Algorithm algorithm) {
		Objects.requireNonNull(algorithm);

		if (this.algorithm == null)
			this.algorithm = algorithm;
		else if (!algorithm.equals(this.algorithm))
			throw new IllegalStateException("Algorithm already set to " + this.algorithm);

		return next();
	}

	/**
	 * Gets the key ID
	 * 
	 * @return key ID
	 */
	String id() {
		return id;
	}

	/**
	 * Gets the algorithm
	 * 
	 * @return algorithm
	 */
	Algorithm algorithm() {
		return algorithm;
	}
}
