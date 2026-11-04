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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.IuType;
import edu.iu.type.testresources.InterceptorSupport;
import edu.iu.type.testresources.InterceptorSupport.AnInterceptorTarget;
import edu.iu.type.testresources.InterceptorSupport.Blocked;
import edu.iu.type.testresources.InterceptorSupport.CalledTwice;
import edu.iu.type.testresources.InterceptorSupport.ChecksParameters;
import edu.iu.type.testresources.InterceptorSupport.ClassLoaderDefaultRecording;
import edu.iu.type.testresources.InterceptorSupport.ConstructBlocked;
import edu.iu.type.testresources.InterceptorSupport.Constructed;
import edu.iu.type.testresources.InterceptorSupport.ConstructedByObject;
import edu.iu.type.testresources.InterceptorSupport.ContainerDefaultRecording;
import edu.iu.type.testresources.InterceptorSupport.DefaultRecording;
import edu.iu.type.testresources.InterceptorSupport.DestroyFails;
import edu.iu.type.testresources.InterceptorSupport.DoublesParameters;
import edu.iu.type.testresources.InterceptorSupport.ExcludesDefaults;
import edu.iu.type.testresources.InterceptorSupport.ExcludesDefaultsOnMethod;
import edu.iu.type.testresources.InterceptorSupport.FailsInInterceptor;
import edu.iu.type.testresources.InterceptorSupport.OnClass;
import edu.iu.type.testresources.InterceptorSupport.OnMethod;
import edu.iu.type.testresources.InterceptorSupport.OnSelf;
import edu.iu.type.testresources.InterceptorSupport.Ordered;
import edu.iu.type.testresources.InterceptorSupport.Plain;
import edu.iu.type.testresources.InterceptorSupport.SharesData;
import edu.iu.type.testresources.InterceptorSupport.UsesNoParameter;
import edu.iu.type.testresources.InterceptorSupport.UsesOverriding;
import edu.iu.type.testresources.InterceptorSupport.UsesPrivateSub;
import edu.iu.type.testresources.InterceptorSupport.UsesStatic;
import edu.iu.type.testresources.InterceptorSupport.UsesTwo;
import edu.iu.type.testresources.InterceptorSupport.UsesVoid;
import edu.iu.type.testresources.InterceptorSupport.UsesWrongParameter;

@SuppressWarnings({ "javadoc", "unchecked", "rawtypes" })
public class InterceptorTests {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument .*");
		InterceptorSupport.LOG.clear();
		InterceptorSupport.created = 0;
	}

	@AfterEach
	public void teardown() {
		InterceptorSupport.LOG.clear();
	}

	private static Object exec(Object target, String name, Class<?>[] types, Object... args) throws Exception {
		final var all = new Object[args.length + 1];
		all[0] = target;
		System.arraycopy(args, 0, all, 1, args.length);
		return IuType.of(target.getClass()).method(name, types).exec(all);
	}

	private static Object exec(Object target, String name) throws Exception {
		return exec(target, name, new Class<?>[0]);
	}

	private static Class<?> define(ClassLoader parent) throws Exception {
		final var name = Plain.class.getName();
		final byte[] bytes;
		try (final var in = Plain.class.getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
			bytes = in.readAllBytes();
		}
		final var loader = new ClassLoader(parent) {
			Class<?> define() {
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		return loader.define();
	}

	private static void destroy(Object target) {
		((IuType) IuType.of(target.getClass())).destroy(target);
	}

	@Test
	public void testClassInterceptor() throws Exception {
		final var target = new OnClass();
		assertEquals("a", exec(target, "echo", new Class<?>[] { String.class }, "a"));
		assertEquals("b", exec(target, "echo", new Class<?>[] { String.class }, "b"));
		assertEquals(List.of("recording echo", "recording echo"), InterceptorSupport.LOG);
		assertEquals(1, InterceptorSupport.created);

		InterceptorSupport.LOG.clear();
		destroy(target);
		assertEquals(List.of("recording destroyed"), InterceptorSupport.LOG);

		assertEquals("a", exec(target, "echo", new Class<?>[] { String.class }, "a"));
		assertEquals(2, InterceptorSupport.created);
		destroy(target);
	}

	@Test
	public void testLifecycleCallbacksAreNotIntercepted() throws Exception {
		final var target = new OnClass();
		exec(target, "init");
		assertEquals(List.of("init"), InterceptorSupport.LOG);
		assertEquals(0, InterceptorSupport.created);
	}

	@Test
	public void testInterfaceDefaultMethod() throws Exception {
		final var target = new InterceptorSupport.ImplementsDefault();
		assertEquals("default", IuType.of(InterceptorSupport.WithDefault.class).method("value").exec(target));
		// interceptors are resolved from the declaring type of the method
		assertEquals(List.of(), InterceptorSupport.LOG);
		assertTrue(IuType.of(OnClass.class).method("echo", String.class).declaringType().methods().iterator().hasNext());
	}

	@Test
	public void testInvalidAroundConstructReturnType() {
		assertThrows(IllegalArgumentException.class,
				() -> IuType.of(InterceptorSupport.UsesBadConstructReturn.class).constructor().exec());
	}

	@Test
	public void testExcludeClassInterceptors() throws Exception {
		assertEquals("excluded", exec(new OnClass(), "excluded"));
		assertEquals(List.of(), InterceptorSupport.LOG);
	}

	@Test
	public void testStaticMethod() throws Exception {
		assertEquals("a", IuType.of(OnClass.class).method("staticEcho", String.class).exec("a"));
		assertEquals(List.of("recording staticEcho"), InterceptorSupport.LOG);
	}

	@Test
	public void testMethodInterceptor() throws Exception {
		final var target = new OnMethod();
		assertEquals("a", exec(target, "echo", new Class<?>[] { String.class }, "a"));
		assertEquals("plain", exec(target, "plain"));
		assertEquals(List.of("recording echo"), InterceptorSupport.LOG);
		destroy(target);
	}

	@Test
	public void testTargetAroundInvoke() throws Exception {
		assertEquals("self", exec(new OnSelf(), "value"));
		assertEquals(List.of("self"), InterceptorSupport.LOG);
	}

	@Test
	public void testOrderAndContextData() throws Exception {
		final var target = new Ordered();
		assertEquals("ordered", exec(target, "value"));
		assertEquals(List.of("super", "sub", "recording value", "read null", "self", "value"), InterceptorSupport.LOG);
		destroy(target);
	}

	@Test
	public void testSharedContextData() throws Exception {
		assertEquals("data", exec(new SharesData(), "value"));
		assertEquals(List.of("read from data"), InterceptorSupport.LOG);
	}

	@Test
	public void testSetParameters() throws Exception {
		assertEquals(14, exec(new DoublesParameters(), "add", new Class<?>[] { int.class, int.class }, 3, 4));
	}

	@Test
	public void testInvalidParameters() throws Exception {
		assertNull(exec(new ChecksParameters(), "name", new Class<?>[] { String.class }, "a"));
		assertEquals(3, exec(new ChecksParameters(), "add", new Class<?>[] { int.class, int.class }, 1, 2));
		assertEquals(List.of("null", "length", "type", "primitive", "timer null constructor null target true"),
				InterceptorSupport.LOG);
	}

	@Test
	public void testBlocked() throws Exception {
		assertEquals("blocked", exec(new Blocked(), "value"));
	}

	@Test
	public void testProceedTwice() throws Exception {
		assertEquals("xx", exec(new CalledTwice(), "value"));
		assertEquals(List.of("value", "value"), InterceptorSupport.LOG);
	}

	@Test
	public void testInterceptorFailure() throws Exception {
		assertEquals("failed in interceptor",
				assertThrows(IllegalStateException.class, () -> exec(new FailsInInterceptor(), "value")).getMessage());
	}

	@Test
	public void testInterceptorFailsOnDestroy() throws Exception {
		final var target = new DestroyFails();
		assertEquals("value", exec(target, "value"));
		assertEquals("failed on destroy", assertThrows(IllegalStateException.class, () -> destroy(target)).getMessage());
	}

	@Test
	public void testTargetAndInterceptorFailOnDestroy() throws Exception {
		final var target = new InterceptorSupport.DestroyFailsBoth();
		assertEquals("value", exec(target, "value"));
		final var error = assertThrows(IllegalStateException.class, () -> destroy(target));
		assertEquals("target failed on destroy", error.getMessage());
		assertEquals("failed on destroy", error.getSuppressed()[0].getMessage());
	}

	@Test
	public void testOverriddenAndPrivate() throws Exception {
		assertEquals("value", exec(new UsesOverriding(), "value"));
		assertEquals("value", exec(new UsesPrivateSub(), "value"));
		assertEquals(List.of("own", "hidden"), InterceptorSupport.LOG);
	}

	@Test
	public void testInvalidInterceptors() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> exec(new UsesStatic(), "value"));
		assertThrows(IllegalArgumentException.class, () -> exec(new UsesVoid(), "value"));
		assertThrows(IllegalArgumentException.class, () -> exec(new UsesWrongParameter(), "value"));
		assertThrows(IllegalArgumentException.class, () -> exec(new UsesNoParameter(), "value"));
		assertThrows(IllegalArgumentException.class, () -> exec(new UsesTwo(), "value"));
	}

	@Test
	public void testAroundConstruct() throws Exception {
		final var constructor = IuType.of(Constructed.class).constructor(String.class);
		final var target = (Constructed) constructor.exec("a");
		assertEquals("a intercepted", target.value);
		assertEquals(List.of("construct 1 true", "constructed a intercepted"), InterceptorSupport.LOG);

		InterceptorSupport.LOG.clear();
		assertEquals("a intercepted", exec(target, "value"));
		assertEquals(List.of("invoked"), InterceptorSupport.LOG);
	}

	@Test
	public void testAroundConstructReturningObject() throws Exception {
		assertInstanceOf(ConstructedByObject.class, IuType.of(ConstructedByObject.class).constructor().exec());
	}

	@Test
	public void testAroundConstructThatDoesNotProceed() {
		final var constructor = IuType.of(ConstructBlocked.class).constructor();
		assertThrows(IllegalStateException.class, constructor::exec);
	}

	@Test
	public void testInterceptorInstancesAreCollected() throws Exception {
		for (var i = 0; i < 10; i++)
			exec(new OnClass(), "echo", new Class<?>[] { String.class }, "a");

		final var keep = new Object();
		for (var i = 0; i < 100 && InterceptorInstances.size() > 0; i++) {
			System.gc();
			Thread.sleep(10L);
			InterceptorInstances.of(keep);
			InterceptorInstances.release(keep);
		}
		assertEquals(0, InterceptorInstances.size());
		Reference.reachabilityFence(keep);
		assertNull(InterceptorInstances.release(new Object()));
	}

	@Test
	public void testDefaultInterceptors() throws Exception {
		final var unregister = DefaultInterceptors.register(List.of(DefaultRecording.class,
				ClassLoaderDefaultRecording.class, ContainerDefaultRecording.class, Plain.class));
		assertNotNull(unregister);
		try {
			assertEquals("plain", exec(new Plain(), "value"));
			assertEquals(List.of("class loader default value", "container default value", "default value"),
					InterceptorSupport.LOG);

			InterceptorSupport.LOG.clear();
			assertEquals("excluded", exec(new ExcludesDefaults(), "value"));
			assertEquals("excluded", exec(new ExcludesDefaultsOnMethod(), "value"));
			assertEquals(List.of(), InterceptorSupport.LOG);

			assertEquals("value", exec(new AnInterceptorTarget(), "value"));
			assertEquals(List.of("recording value"), InterceptorSupport.LOG);

			assertEquals(List.of(), DefaultInterceptors.applicableTo(String.class));
			assertEquals(List.of(), DefaultInterceptors.applicableTo(define(null)));
			assertEquals(List.of(ContainerDefaultRecording.class),
					DefaultInterceptors.applicableTo(define(getClass().getClassLoader())));
		} finally {
			unregister.run();
		}

		InterceptorSupport.LOG.clear();
		assertEquals("plain", exec(new Plain(), "value"));
		assertEquals(List.of(), InterceptorSupport.LOG);
	}

	@Test
	public void testNoDefaultInterceptorsToRegister() {
		final var version = DefaultInterceptors.version();
		assertNull(DefaultInterceptors.register(List.of(Plain.class)));
		assertEquals(version, DefaultInterceptors.version());
		assertTrue(DefaultInterceptors.applicableTo(Plain.class).isEmpty());
	}

}
