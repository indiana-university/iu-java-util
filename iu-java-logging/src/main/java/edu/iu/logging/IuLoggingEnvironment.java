/*
 * Copyright © 2023 Indiana University
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

/**
 * Logging Environment Interface
 */
public interface IuLoggingEnvironment {

	/**
	 * Enumeration of possible runtime modes.
	 */
	public static enum RuntimeMode {
		/**
		 * Development
		 */
		DEVELOPMENT,
		/**
		 * Test
		 */
		TEST,
		/**
		 * Production
		 */
		PRODUCTION
	}

	/**
	 * Get the application.
	 * 
	 * @return String representation of the application. Defaults to null.
	 */
	default String getApplication() {
		return null;
	}

	/**
	 * Get the component.
	 * 
	 * @return String representation of the component. Defaults to null.
	 */
	default String getComponent() {
		return null;
	}

	/**
	 * Get the environment.
	 * 
	 * @return String representation of the environment. Defaults to null.
	 */
	default String getEnvironment() {
		return null;
	}

	/**
	 * Get the hostname.
	 * 
	 * @return String representation of the hostname. Defaults to null.
	 */
	default String getHostname() {
		return null;
	}

	/**
	 * Get the mode.
	 * 
	 * @return RuntimeMode enum with values for DEVELOPMENT, TEST, and PRODUCTION. Defaults to null.
	 */
	default RuntimeMode getMode() {
		return null;
	}

	/**
	 * Get the module.
	 * 
	 * @return String representation of the module. Defaults to null.
	 */
	default String getModule() {
		return null;
	}

	/**
	 * Get the node id.
	 * 
	 * @return String representation of the node id. Defaults to null.
	 */
	default String getNodeId() {
		return null;
	}

	/**
	 * Get the runtime.
	 * 
	 * @return String representation of the runtime. Defaults to null.
	 */
	default String getRuntime() {
		return null;
	}
}
