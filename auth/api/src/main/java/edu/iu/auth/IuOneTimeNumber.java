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
package edu.iu.auth;

import java.net.InetAddress;

import edu.iu.IuWebUtils;
import edu.iu.auth.nonce.IuOneTimeNumberConfig;
import edu.iu.auth.spi.IuNonceSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * One-time number (nonce) engine.
 *
 * <img src="doc-files/Nonce.svg" alt="UML Sequence Diagram">
 * 
 * <p>
 * Provides securely generated one-time numbers. Clients <em>should</em>
 * optimistically limit concurrent access to a single thread. Single-use
 * tracking is performed internally:
 * </p>
 * 
 * <ul>
 * <li><strong>Nonce</strong> values <em>must</em> be used within the configured
 * time to live interval. PT15S is <em>recommended</em> as a default value.</li>
 * <li>Creating a new <strong>nonce</strong> value causes all previously created
 * <strong>nonce</strong> values for the same client to expire if generated more
 * than PT0.25S in the past.</li>
 * <li>Client is thumbprinted by
 * {@code sha256(utf8(remoteAddr || userAgent))}</li>
 * <li>remoteAddr is resolved by {@link IuWebUtils#getInetAddress(String)} then
 * canonicalized with {@link InetAddress#getAddress()}</li>
 * <li>userAgent is {@link IuWebUtils#validateUserAgent(String) validated}</li>
 * <li>When pruning stale <strong>nonce</strong> challenges, 25ms artificial
 * delay is inserted to prevent excessive use</li>
 * <li>Regardless of validation status, a <strong>nonce</strong> value
 * <em>may</em> only be used once.</li>
 * </ul>
 */
public interface IuOneTimeNumber extends AutoCloseable {

	/**
	 * Initializes a new one-time number generator.
	 * 
	 * @param config configuration properties
	 * @return {@link IuOneTimeNumber}
	 */
	static IuOneTimeNumber initialize(IuOneTimeNumberConfig config) {
		return IuAuthSpiFactory.get(IuNonceSpi.class).initialize(config);
	}

	/**
	 * Creates a one-time number (nonce) value.
	 * 
	 * @param remoteAddress textual representation of the client IP address
	 * @param userAgent     user agent string
	 * @return one-time number
	 */
	String create(String remoteAddress, String userAgent);

	/**
	 * Validates a one-time number (nonce) value.
	 * 
	 * @param remoteAddress textual representation of the client IP address
	 * @param userAgent     user agent string
	 * @param nonce         one-time number
	 */
	void validate(String remoteAddress, String userAgent, String nonce);

}
