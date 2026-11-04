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
package edu.iu.redis;

import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Redis configuration interface.
 */
public interface IuRedisConfiguration {

	/**
	 * Returns the password to be used for HTTP Basic authentication.
	 *
	 * @return the password
	 */
	String getPassword();

	/**
	 * Returns the host to be used for Redis connection.
	 *
	 * @return the host
	 */
	String getHost();

	/**
	 * Returns the port to be used for Redis connection.
	 *
	 * @return the port
	 */
	String getPort();

	/**
	 * Returns the username to be used for HTTP Basic authentication.
	 *
	 * @return the username
	 */
	default String getUsername() {
		return null;
	}

	/**
	 * Gets the root trusted signing certificate for verifying TLS connections.
	 * 
	 * @return {@link X509Certificate}
	 */
	default X509Certificate getTrustedCert() {
		return null;
	}

	/**
	 * Connecting to Redis Cluster with SSL enabled.
	 * 
	 * @return true if SSL is enabled otherwise
	 */
	default boolean getSsl() {
		return true;
	}

	/**
	 * Returns the key expiration duration.
	 *
	 * @return the key expiration duration, default is 15 minutes
	 */
	default Duration getKeyExpiration() {
		return Duration.ofMinutes(15);
	}

	/**
	 * Returns the prefix every key stored through this connection is named under.
	 *
	 * <p>
	 * The prefix is what makes a Redis instance shareable. It scopes a listing to
	 * one store, and it scopes an eviction question or an incident to one
	 * application, so a deployment that shares an instance between applications --
	 * or between distinct stores within one application, such as a session store
	 * and a cache -- <em>should</em> give each one its own prefix. The default is
	 * deliberately non-empty rather than absent, so that a store is never
	 * accidentally unnamespaced; it is not, however, distinguishing on its own.
	 * </p>
	 *
	 * <p>
	 * A prefix is read once, when the connection is created, and applies for that
	 * connection's life: changing it moves every key, so it cannot be varied under
	 * a running connection.
	 * </p>
	 *
	 * @return key prefix, default is {@code iu}
	 */
	default String getKeyPrefix() {
		return "iu";
	}

}