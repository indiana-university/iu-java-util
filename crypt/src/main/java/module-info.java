/*
 * Copyright © 2024 Indiana University All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * Low-level web cryptography support.
 * <p>
 * Provides full implementations of:
 * </p>
 * <ul>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7515">RFC-7515 JSON Web
 * Signature (JWS)</a></li>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC-7516 JSON Web
 * Encryption (JWE)</a></li>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7517">RFC-7517 JSON Web
 * Key (JWK)</a></li>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7518">RFC-7518 JSON Web
 * Algorithms (JWA)</a></li>
 * </ul>
 */
module iu.util.crypt {
	exports edu.iu.crypt;

	requires iu.util;
	requires transitive iu.util.client;
}
