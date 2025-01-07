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
package edu.iu.redis;

import java.time.Duration;

/**
 * Redis configuration interface.
 */
public interface IuRedisConfiguration {
	/**
	 * Returns the username to be used for HTTP Basic authentication.
	 *
	 * @return the username
	 */
	String getUsername();

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
	 * @return the key expiration duration
	 */
	default Duration getKeyExpiration() {
		return Duration.ofMinutes(5);
	}
}