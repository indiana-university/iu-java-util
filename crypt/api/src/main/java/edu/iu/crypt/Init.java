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
package edu.iu.crypt;

import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import iu.crypt.spi.IuCryptSpi;

/**
 * Initialization stub to be <em>explicitly</em> loaded from the bootstrap
 * module in control of the implementation's {@link ModuleLayer}, before
 * attempting to use any crypto functions, while the implementation module's
 * {@link ClassLoader} is in control of the
 * {@link Thread#getContextClassLoader()} current thread's context.
 * 
 * <p>
 * Note: When both iu.util.crypt and iu.util.crypt.impl are in named modules
 * loaded by the {@link ClassLoader#getSystemClassLoader() System ClassLoader},
 * explicit initialization is not needed.
 * </p>
 */
public final class Init {

	private static final Logger LOG = Logger.getLogger(Init.class.getName());

	private Init() {
	}

	/** {@link IuCryptSpi} instance */
	static final IuCryptSpi SPI;

	static {
		SPI = ServiceLoader.load(IuCryptSpi.class).findFirst().get();
		LOG.config("init iu-java-crypt SPI " + SPI);
	}

	/**
	 * Verifies the SPI has fully initialized.
	 */
	public static void init() {
		Objects.requireNonNull(SPI);
	}

}
