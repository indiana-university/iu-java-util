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
package edu.iu.type.testresources;

import java.util.ArrayList;
import java.util.List;

import edu.iu.type.DefaultInterceptor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.ExcludeClassInterceptors;
import jakarta.interceptor.ExcludeDefaultInterceptors;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.Interceptors;
import jakarta.interceptor.InvocationContext;

/**
 * Interceptor and target fixtures.
 */
@SuppressWarnings({ "javadoc", "unused" })
public class InterceptorSupport {

	public static final List<String> LOG = new ArrayList<>();
	public static int created;

	public static class Recording {
		public Recording() {
			created++;
		}

		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			LOG.add("recording " + ctx.getMethod().getName());
			return ctx.proceed();
		}

		@PreDestroy
		public void destroy() {
			LOG.add("recording destroyed");
		}
	}

	public static class Super {
		@AroundInvoke
		public Object superAround(InvocationContext ctx) throws Exception {
			LOG.add("super");
			return ctx.proceed();
		}
	}

	public static class Sub extends Super {
		@AroundInvoke
		public Object subAround(InvocationContext ctx) throws Exception {
			LOG.add("sub");
			return ctx.proceed();
		}
	}

	public static class Overriding extends Super {
		@Override
		public Object superAround(InvocationContext ctx) throws Exception {
			LOG.add("overriding");
			return ctx.proceed();
		}

		@AroundInvoke
		public Object own(InvocationContext ctx) throws Exception {
			LOG.add("own");
			return ctx.proceed();
		}

		// same name, different signature: an overload, not an override
		public Object superAround() {
			return null;
		}
	}

	public static class BadConstructReturn {
		@AroundConstruct
		public String around(InvocationContext ctx) throws Exception {
			return null;
		}
	}

	@Interceptors(BadConstructReturn.class)
	public static class UsesBadConstructReturn {
		public UsesBadConstructReturn() {
		}
	}

	public interface WithDefault {
		default String value() {
			return "default";
		}
	}

	public static class ImplementsDefault implements WithDefault {
	}

	public static class PrivateSuper {
		@AroundInvoke
		private Object hidden(InvocationContext ctx) throws Exception {
			LOG.add("hidden");
			return ctx.proceed();
		}
	}

	public static class PrivateSub extends PrivateSuper {
		private Object hidden(InvocationContext ctx) throws Exception {
			throw new AssertionError("not an override of a private method");
		}
	}

	public static class Data {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			ctx.getContextData().put("data", "from data");
			return ctx.proceed();
		}
	}

	public static class ReadsData {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			LOG.add("read " + ctx.getContextData().get("data"));
			return ctx.proceed();
		}
	}

	public static class Doubling {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			final var params = ctx.getParameters();
			ctx.setParameters(new Object[] { (Integer) params[0] * 2, (Integer) params[1] * 2 });
			return ctx.proceed();
		}
	}

	public static class BadParameters {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			if (ctx.getParameters().length == 1) {
				ctx.setParameters(new Object[] { null });
				return ctx.proceed();
			}

			try {
				ctx.setParameters(null);
			} catch (IllegalArgumentException e) {
				LOG.add("null");
			}
			try {
				ctx.setParameters(new Object[0]);
			} catch (IllegalArgumentException e) {
				LOG.add("length");
			}
			try {
				ctx.setParameters(new Object[] { "a", "b" });
			} catch (IllegalArgumentException e) {
				LOG.add("type");
			}
			try {
				ctx.setParameters(new Object[] { null, 1 });
			} catch (IllegalArgumentException e) {
				LOG.add("primitive");
			}
			LOG.add("timer " + ctx.getTimer() + " constructor " + ctx.getConstructor() + " target " + (ctx.getTarget() != null));
			return ctx.proceed();
		}
	}

	public static class Blocking {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			return "blocked";
		}
	}

	public static class Twice {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			return ctx.proceed().toString() + ctx.proceed();
		}
	}

	public static class Failing {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			throw new IllegalStateException("failed in interceptor");
		}
	}

	public static class FailsOnDestroy {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			return ctx.proceed();
		}

		@PreDestroy
		public void destroy() {
			throw new IllegalStateException("failed on destroy");
		}
	}

	public static class Constructing {
		@AroundConstruct
		public void around(InvocationContext ctx) throws Exception {
			LOG.add("construct " + ctx.getConstructor().getParameterCount() + " " + (ctx.getMethod() == null));
			ctx.setParameters(new Object[] { ctx.getParameters()[0] + " intercepted" });
			ctx.proceed();
			LOG.add("constructed " + ((Constructed) ctx.getTarget()).value);
		}

		@AroundInvoke
		public Object invoked(InvocationContext ctx) throws Exception {
			LOG.add("invoked");
			return ctx.proceed();
		}
	}

	public static class ConstructingObject {
		@AroundConstruct
		public Object around(InvocationContext ctx) throws Exception {
			return ctx.proceed();
		}
	}

	public static class ConstructBlocking {
		@AroundConstruct
		public void around(InvocationContext ctx) throws Exception {
		}
	}

	public static class StaticInterceptor {
		@AroundInvoke
		public static Object around(InvocationContext ctx) throws Exception {
			return ctx.proceed();
		}
	}

	public static class VoidInterceptor {
		@AroundInvoke
		public void around(InvocationContext ctx) throws Exception {
		}
	}

	public static class WrongParameterInterceptor {
		@AroundInvoke
		public Object around(Object ctx) throws Exception {
			return null;
		}
	}

	public static class NoParameterInterceptor {
		@AroundInvoke
		public Object around() throws Exception {
			return null;
		}
	}

	public static class TwoInterceptor {
		@AroundInvoke
		public Object one(InvocationContext ctx) throws Exception {
			return ctx.proceed();
		}

		@AroundInvoke
		public Object two(InvocationContext ctx) throws Exception {
			return ctx.proceed();
		}
	}

	@Interceptor
	@Interceptors(Recording.class)
	public static class AnInterceptorTarget {
		public String value() {
			return "value";
		}
	}

	@DefaultInterceptor
	public static class DefaultRecording {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			LOG.add("default " + ctx.getMethod().getName());
			return ctx.proceed();
		}
	}

	@DefaultInterceptor(scope = DefaultInterceptor.Scope.CLASS_LOADER)
	public static class ClassLoaderDefaultRecording {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			LOG.add("class loader default " + ctx.getMethod().getName());
			return ctx.proceed();
		}
	}

	@DefaultInterceptor(scope = DefaultInterceptor.Scope.CONTAINER)
	public static class ContainerDefaultRecording {
		@AroundInvoke
		public Object around(InvocationContext ctx) throws Exception {
			LOG.add("container default " + ctx.getMethod().getName());
			return ctx.proceed();
		}
	}

	public static class Plain {
		public String value() {
			return "plain";
		}
	}

	@ExcludeDefaultInterceptors
	public static class ExcludesDefaults {
		public String value() {
			return "excluded";
		}
	}

	public static class ExcludesDefaultsOnMethod {
		@ExcludeDefaultInterceptors
		public String value() {
			return "excluded";
		}
	}

	@Interceptors(Recording.class)
	public static class OnClass {
		public String echo(String value) {
			return value;
		}

		public int add(int a, int b) {
			return a + b;
		}

		public static String staticEcho(String value) {
			return value;
		}

		@ExcludeClassInterceptors
		public String excluded() {
			return "excluded";
		}

		@PostConstruct
		public void init() {
			LOG.add("init");
		}
	}

	public static class OnMethod {
		@Interceptors(Recording.class)
		public String echo(String value) {
			return value;
		}

		public String plain() {
			return "plain";
		}
	}

	public static class OnSelf {
		@AroundInvoke
		private Object self(InvocationContext ctx) throws Exception {
			LOG.add("self");
			return ctx.proceed();
		}

		public String value() {
			return "self";
		}
	}

	@Interceptors({ Sub.class, Recording.class })
	public static class Ordered {
		@Interceptors(ReadsData.class)
		public String value() {
			LOG.add("value");
			return "ordered";
		}

		@AroundInvoke
		private Object self(InvocationContext ctx) throws Exception {
			LOG.add("self");
			return ctx.proceed();
		}
	}

	@Interceptors({ Data.class, ReadsData.class })
	public static class SharesData {
		public String value() {
			return "data";
		}
	}

	@Interceptors(Doubling.class)
	public static class DoublesParameters {
		public int add(int a, int b) {
			return a + b;
		}
	}

	@Interceptors(BadParameters.class)
	public static class ChecksParameters {
		public int add(int a, int b) {
			return a + b;
		}

		public String name(String name) {
			return name;
		}
	}

	@Interceptors(Blocking.class)
	public static class Blocked {
		public String value() {
			throw new AssertionError("blocked");
		}
	}

	@Interceptors(Twice.class)
	public static class CalledTwice {
		public String value() {
			LOG.add("value");
			return "x";
		}
	}

	@Interceptors(Failing.class)
	public static class FailsInInterceptor {
		public String value() {
			throw new AssertionError("failed");
		}
	}

	@Interceptors(FailsOnDestroy.class)
	public static class DestroyFails {
		public String value() {
			return "value";
		}
	}

	@Interceptors(FailsOnDestroy.class)
	public static class DestroyFailsBoth {
		public String value() {
			return "value";
		}

		@PreDestroy
		public void destroy() {
			throw new IllegalStateException("target failed on destroy");
		}
	}

	@Interceptors(Overriding.class)
	public static class UsesOverriding {
		public String value() {
			return "value";
		}
	}

	@Interceptors(PrivateSub.class)
	public static class UsesPrivateSub {
		public String value() {
			return "value";
		}
	}

	@Interceptors(Constructing.class)
	public static class Constructed {
		public final String value;

		public Constructed(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}
	}

	@Interceptors(ConstructingObject.class)
	public static class ConstructedByObject {
		public ConstructedByObject() {
		}
	}

	@Interceptors(ConstructBlocking.class)
	public static class ConstructBlocked {
		public ConstructBlocked() {
		}
	}

	@Interceptors(StaticInterceptor.class)
	public static class UsesStatic {
		public String value() {
			return "value";
		}
	}

	@Interceptors(VoidInterceptor.class)
	public static class UsesVoid {
		public String value() {
			return "value";
		}
	}

	@Interceptors(WrongParameterInterceptor.class)
	public static class UsesWrongParameter {
		public String value() {
			return "value";
		}
	}

	@Interceptors(NoParameterInterceptor.class)
	public static class UsesNoParameter {
		public String value() {
			return "value";
		}
	}

	@Interceptors(TwoInterceptor.class)
	public static class UsesTwo {
		public String value() {
			return "value";
		}
	}

}
