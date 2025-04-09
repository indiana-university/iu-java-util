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
package edu.iu.web;

import java.net.URI;

import com.sun.net.httpserver.HttpHandler;

/**
 * Encapsulates a reference to an {@link HttpHandler} by context path.
 */
public interface IuWebContext {

	/**
	 * Gets the application.
	 * 
	 * @return application code
	 */
	String getApplication();

	/**
	 * Gets the environment.
	 * 
	 * @return environment code
	 */
	String getEnvironment();

	/**
	 * Gets the module.
	 * 
	 * @return module name
	 */
	String getModule();

	/**
	 * Gets the runtime.
	 * 
	 * @return runtime name
	 */
	String getRuntime();

	/**
	 * Gets the component.
	 * 
	 * @return component name
	 */
	String getComponent();

	/**
	 * Gets the support pre-text.
	 * 
	 * @return support pre-text
	 */
	String getSupportPreText();

	/**
	 * Gets the support URL.
	 * 
	 * @return support URL
	 */
	String getSupportUrl();

	/**
	 * Gets the support label.
	 * 
	 * @return support label
	 */
	String getSupportLabel();

	/**
	 * Gets the context path relative to the server root.
	 * 
	 * @return context path, with leading slash
	 */
	URI getRootUri();

	/**
	 * Allow-list of domains permitted to submit cross-origin fetch requests.
	 * 
	 * @return set of domains
	 */
	Iterable<String> getOriginAllow();

	/**
	 * Gets the {@link HttpHandler} for requests relative to {@link #getRootUri()}.
	 * 
	 * @return {@link HttpHandler}
	 */
	HttpHandler getHandler();

	/**
	 * Gets the {@link ClassLoader} to use for binding threads to the context.
	 * 
	 * @return {@link ClassLoader}
	 */
	// TODO: Conflict between this method signature and the 
	// org.apache.catalina.core.StandardContext.getLoader() method
	// So IuTomcatContext can't extend StandardContext and implement this interface, as-is
//	ClassLoader getLoader();

}
