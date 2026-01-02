/*
 * Copyright © 2026 Indiana University
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
package iu.logging;

import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import edu.iu.IdGenerator;
import edu.iu.IuException;

@SuppressWarnings("javadoc")
public class IuLoggingTestCase {

	protected final static String NODE_ID = IuException.unchecked(() -> InetAddress.getLocalHost().getHostName());
	protected final static String DEFAULT_ENDPOINT;
	protected final static String DEFAULT_APPLICATION;
	protected final static String DEFAULT_ENVIRONMENT;
	protected final static String DEFAULT_MODULE;
	protected final static String DEFAULT_RUNTIME;
	protected final static String DEFAULT_COMPONENT;
	protected static final ByteArrayOutputStream OUT = new ByteArrayOutputStream();
	protected static final ByteArrayOutputStream ERR = new ByteArrayOutputStream();

	static {
		DEFAULT_ENDPOINT = IdGenerator.generateId();
		DEFAULT_APPLICATION = IdGenerator.generateId();
		DEFAULT_ENVIRONMENT = IdGenerator.generateId();
		DEFAULT_MODULE = IdGenerator.generateId();
		DEFAULT_RUNTIME = IdGenerator.generateId();
		DEFAULT_COMPONENT = IdGenerator.generateId();
	}

	static {
		mockStatic(Files.class).close();
		System.setOut(new PrintStream(OUT));
		System.setErr(new PrintStream(ERR));
	}

	@BeforeEach
	public void setupStreams() {
		OUT.reset();
		ERR.reset();
	}

	@BeforeAll
	public static void setSystemProperties() {
		System.setProperty("iu.endpoint", DEFAULT_ENDPOINT);
		System.setProperty("iu.application", DEFAULT_APPLICATION);
		System.setProperty("iu.environment", DEFAULT_ENVIRONMENT);
		System.setProperty("iu.module", DEFAULT_MODULE);
		System.setProperty("iu.runtime", DEFAULT_RUNTIME);
		System.setProperty("iu.component", DEFAULT_COMPONENT);
	}

}
