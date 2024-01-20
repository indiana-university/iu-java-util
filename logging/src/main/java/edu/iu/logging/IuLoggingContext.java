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
package edu.iu.logging;

import edu.iu.UnsafeRunnable;
import iu.logging.LogEventFactory;

/**
 * Logging Context Interface
 */
public interface IuLoggingContext {

	/**
	 * Get the authenticated principal.
	 * 
	 * @return String representation of the authenticated principal. Defaults to
	 *         null.
	 */
	default String getAuthenticatedPrincipal() {
		return null;
	}

	/**
	 * Get the called URL.
	 * 
	 * @return String representation of the called URL. Defaults to null.
	 */
	default String getCalledUrl() {
		return null;
	}

	/**
	 * Get the remote address.
	 * 
	 * @return String representation of the remote address. Defaults to null.
	 */
	default String getRemoteAddr() {
		return null;
	}

	/**
	 * Get the request number.
	 * 
	 * @return String representation of the request number. Defaults to null.
	 */
	default String getReqNum() {
		return null;
	}

	/**
	 * Get the user principal.
	 * 
	 * @return String representation of the user principal. Defaults to null.
	 */
	default String getUserPrincipal() {
		return null;
	}

	/**
	 * Run a task with the given context.
	 * 
	 * @param context IuLoggingContext to which a task will be bound.
	 * @param task    UnsafeRunnable to run with the given context.
	 */
	static void bound(IuLoggingContext context, UnsafeRunnable task) {
		LogEventFactory.bound(context, task);
	}

	/**
	 * Get the current context.
	 * 
	 * @return IuLoggingContext representing the current context.
	 */
	static IuLoggingContext getCurrentContext() {
		return LogEventFactory.getCurrentContext();
	}
}
