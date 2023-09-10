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
package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.Properties;
import java.util.Queue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

public class IuTypeTest {

	private static ClassLoader testcomponent;

	@BeforeAll
	public static void setupClass() throws IOException, URISyntaxException {
		var properties = new Properties();
		try (var in = ClassLoader.getSystemClassLoader().getResourceAsStream("META-INF/testcomponent.properties")) {
			properties.load(in);
		}

		Queue<URL> classpath = new ArrayDeque<>();
		classpath.offer(new File(properties.getProperty("testcomponent.jar")).getCanonicalFile().toURI().toURL());
		for (var jar : new File(properties.getProperty("testcomponent.deps")).getCanonicalFile().listFiles())
			classpath.offer(jar.toURI().toURL());
		testcomponent = new URLClassLoader(classpath.toArray(new URL[classpath.size()]),
				ClassLoader.getPlatformClassLoader());
	}

	@Test
	public void testBase() throws ClassNotFoundException {
		var object = IuType.resolve(Object.class);
		assertSame(Object.class, object.baseClass());
		assertSame(object, IuType.resolve((Type) Object.class));

		var myTestBean = ClassLoader.getSystemClassLoader().loadClass("edu.iu.type.testcomponent.TestBean");
		var theirTestBean = testcomponent.loadClass("edu.iu.type.testcomponent.TestBean");
		assertNotSame(myTestBean, theirTestBean);
		assertNotSame(IuType.resolve(myTestBean), IuType.resolve(theirTestBean));
	}

	@Test
	public void testDiscoverResources() throws ClassNotFoundException {
		var resource = testcomponent.getResource("edu/iu/type/testcomponent/TestBean.class"); 
		assertTrue(IuType.discoverResources(testcomponent).contains(resource));
	}
	
	@Test
	public void testReferTo() throws ClassNotFoundException {
		var testBean = testcomponent.loadClass("edu.iu.type.testcomponent.TestBean");
		var testBeanImpl = testcomponent.loadClass("edu.iu.type.testcomponent.TestBeanImpl");
		assertSame(testBean, IuType.resolve(testBeanImpl).referTo(testBean).baseClass());
	}

}
