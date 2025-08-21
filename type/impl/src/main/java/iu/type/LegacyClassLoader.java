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
package iu.type;

import java.net.URL;
import java.net.URLClassLoader;

import edu.iu.IuObject;
import edu.iu.type.IuComponent.Kind;

/**
 * Class loader for {@link Kind#isModular() legacy} components.
 */
class LegacyClassLoader extends URLClassLoader {

	private final boolean web;

	/**
	 * Constructor for use by {@link ComponentFactory}
	 * 
	 * @param web       true for <a href=
	 *                  "https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0#web-application-class-loader">web
	 *                  classloading semantics</a>; false for normal parent
	 *                  delegation semantics
	 * @param classpath class path URLs
	 * @param parent    parent class loader
	 */
	LegacyClassLoader(boolean web, URL[] classpath, ClassLoader parent) {
		super(classpath, parent);
		this.web = web;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (!web || IuObject.isPlatformName(name))
			return super.loadClass(name, resolve);

		synchronized (getClassLoadingLock(name)) {
			Class<?> rv = this.findLoadedClass(name);
			if (rv != null)
				return rv;

			try {
				rv = findClass(name);
				if (resolve)
					resolveClass(rv);
				return rv;
			} catch (ClassNotFoundException e) {
				// will attempt throw again when called from
				// super.loadClass if also not found in parent
			}

			return super.loadClass(name, resolve);
		}
	}

}
