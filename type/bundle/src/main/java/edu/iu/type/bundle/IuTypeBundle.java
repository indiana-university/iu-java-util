/*
 * Copyright © 2025 Indiana University
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
package edu.iu.type.bundle;

import edu.iu.type.IuType;
import iu.type.bundle.TypeBundleSpi;

/**
 * Provides access to runtime metadata related to the bundled Type Introspection
 * module.
 */
public final class IuTypeBundle {

	/**
	 * Gets a reference to the Type Introspection implementation module.
	 * 
	 * @return {@link Module}
	 */
	public static Module getModule() {
		IuType.of(Object.class); // verifies implementation bootstrap
		return TypeBundleSpi.getModule();
	}

	/**
	 * Shuts down the bundled Type Introspection module.
	 * 
	 * <p>
	 * This method <em>should</em> be called after releasing all application
	 * resources related to loaded components to close all {@link ClassLoader}
	 * resources and clean up temp files. If any references are left uncleared,
	 * shutdown may fail.
	 * </p>
	 * 
	 * @throws Exception if an error occurs shutting down the module
	 */
	public static void shutdown() throws Exception {
		TypeBundleSpi.shutdown();
	}

	private IuTypeBundle() {
	}

}
