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
package iu.type;

import edu.iu.type.DefaultInterceptor;

final class BackwardsCompatibility {

	static Class<?> getNonLegacyClass(Class<?> maybeLegacyClass) throws ClassNotFoundException {
		var className = maybeLegacyClass.getName();

		String nonLegacyClassName;
		if (className.startsWith("javax."))
			nonLegacyClassName = "jakarta" + className.substring(5);
		else if (className.equals("edu.iu.spi.DefaultInterceptor"))
			nonLegacyClassName = DefaultInterceptor.class.getName();
		else
			nonLegacyClassName = className;

		return Thread.currentThread().getContextClassLoader().loadClass(nonLegacyClassName);
	}

	static Class<?> getLegacyClass(Class<?> mayHaveLegacyEquivalent) throws ClassNotFoundException {
		var className = mayHaveLegacyEquivalent.getName();

		ClassNotFoundException nonLegacyNotFound;
		try {
			return Thread.currentThread().getContextClassLoader().loadClass(className);
		} catch (ClassNotFoundException e) {
			nonLegacyNotFound = e;
		}

		String legacyClassName;
		if (className.startsWith("jakarta."))
			legacyClassName = "javax" + className.substring(7);
		else if (className.equals(DefaultInterceptor.class.getName()))
			legacyClassName = "edu.iu.spi.DefaultInterceptor";
		else
			throw nonLegacyNotFound;

		try {
			return Thread.currentThread().getContextClassLoader().loadClass(legacyClassName);
		} catch (ClassNotFoundException e) {
			e.addSuppressed(nonLegacyNotFound);
			throw e;
		}
	}

	private BackwardsCompatibility() {
	}

}
