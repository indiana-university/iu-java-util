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
package edu.iu.auth.nonce;

import java.time.Duration;
import java.util.function.Consumer;

import edu.iu.auth.IuOneTimeNumber;

/**
 * Provides configuration properties for tuning {@link IuOneTimeNumber}
 * instances.
 */
public interface IuOneTimeNumberConfig {

	/**
	 * Gets the maximum time to allow a pending one-time number value to be
	 * accepted.
	 * 
	 * @return {@link Duration}
	 */
	default Duration getTimeToLive() {
		return Duration.ofMinutes(2L);
	}

	/**
	 * Gets the maximum number of concurrent nonce requests to allow per client.
	 * 
	 * @return maximum number of concurrent nonce requests
	 */
	default int getMaxConcurrency() {
		return 5;
	}

	/**
	 * Subscribes the one-time number generator to external
	 * {@link IuAuthorizationChallenge} events.
	 * 
	 * @param challengeSubscriber Receives a {@link Consumer} for publishing
	 *                            {@link IuAuthorizationChallenge} events received
	 *                            from other nodes.
	 */
	default void subscribe(Consumer<IuAuthorizationChallenge> challengeSubscriber) {
	}

	/**
	 * Broadcasts a {@link IuAuthorizationChallenge} event to all subscribers.
	 * 
	 * @param challenge {@link IuAuthorizationChallenge} event
	 */
	default void publish(IuAuthorizationChallenge challenge) {
	}

}
