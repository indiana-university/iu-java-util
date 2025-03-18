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
package edu.iu.type.base;

import edu.iu.IuObject;

/**
 * Prevents delegation to classes unrelated to the base platform.
 * 
 * <p>
 * Always allows delegation for class names for which
 * {@link IuObject#isPlatformName(String)} returns true, <em>except</em> those
 * starting with {@code javax.} or {@code jakarta.}. <em>Optionally</em> allows
 * delegation to additional packages, including {@code javax.} and
 * {@code jakarta.} packages. Allowed packages are explicit: i.e., allowing
 * access to {@code edu.iu} doesn't allow access to {@code edu.iu.type}.
 * </p>
 */
public class FilteringClassLoader extends ClassLoader {

	private final Iterable<String> allowedPackages;

	/**
	 * Constructor.
	 * 
	 * @param allowedPackages list of additional packages visible to the parent
	 *                        loader to allow delegation to
	 * @param parent          {@link ClassLoader} for parent delegation
	 */
	public FilteringClassLoader(Iterable<String> allowedPackages, ClassLoader parent) {
		super(parent);
		this.allowedPackages = allowedPackages;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		var match = !name.startsWith("jakarta.") && //
				IuObject.isPlatformName(name);

		if (!match)
			for (final var allowedPackage : allowedPackages) {
				if (!name.startsWith(allowedPackage))
					continue;

				final var length = allowedPackage.length();
				final var lastDot = name.lastIndexOf('.');
				if (length == lastDot) {
					match = true;
					break;
				}
			}

		if (match)
			return super.loadClass(name, resolve);
		else
			throw new ClassNotFoundException(name);
	}

}
