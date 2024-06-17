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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuExceptionTest {

	private static final Object[] O0 = new Object[0];
	private static final Constructor<UnsafeClass> UNSAFE_CONSTRUCTOR;
	private static final Constructor<UnsafeClass> SAFE_CONSTRUCTOR;
	private static final Method UNSAFE_STATIC_METHOD;
	private static final Method SAFE_STATIC_METHOD;
	private static final Method UNSAFE_METHOD;
	private static final Method SAFE_METHOD;

	static {
		try {
			UNSAFE_CONSTRUCTOR = UnsafeClass.class.getDeclaredConstructor(Throwable.class);
			UNSAFE_CONSTRUCTOR.setAccessible(true);
			SAFE_CONSTRUCTOR = UnsafeClass.class.getDeclaredConstructor();
			SAFE_CONSTRUCTOR.setAccessible(true);
			UNSAFE_STATIC_METHOD = UnsafeClass.class.getDeclaredMethod("unsafeStaticMethod", Throwable.class);
			UNSAFE_STATIC_METHOD.setAccessible(true);
			SAFE_STATIC_METHOD = UnsafeClass.class.getDeclaredMethod("safeStaticMethod", Object.class);
			SAFE_STATIC_METHOD.setAccessible(true);
			UNSAFE_METHOD = UnsafeClass.class.getDeclaredMethod("unsafeMethod", Throwable.class);
			UNSAFE_METHOD.setAccessible(true);
			SAFE_METHOD = UnsafeClass.class.getDeclaredMethod("safeMethod", Object.class);
			SAFE_METHOD.setAccessible(true);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static class CustomThrowable extends Throwable {
		private static final long serialVersionUID = 1L;
	}

	private static class CheckedException1 extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static class CheckedException2 extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static class CheckedException3 extends Exception {
		private static final long serialVersionUID = 1L;
	}

	@SuppressWarnings("unused")
	private static class UnsafeClass {
		private static Object safeStaticMethod(Object echo) {
			return echo;
		}

		private static void unsafeStaticMethod(Throwable throwable) throws Throwable {
			throw throwable;
		}

		private UnsafeClass() {
		}

		private UnsafeClass(Throwable throwable) throws Throwable {
			throw throwable;
		}

		private Object safeMethod(Object echo) {
			return echo;
		}

		private void unsafeMethod(Throwable throwable) throws Throwable {
			throw throwable;
		}
	}

	private <T extends Throwable> void assertReturns(Class<T> throwableClass, UnsafeFunction<T, T> consumer)
			throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertSame(throwable, consumer.apply(throwable));
	}

	private <T extends Throwable> void assertThrows(Class<T> throwableClass, UnsafeConsumer<T> consumer)
			throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertSame(throwable, Assertions.assertThrows(throwableClass, () -> consumer.accept(throwable)));
	}

	private <T extends Throwable> void assertThrowsIllegalState(Class<T> throwableClass, UnsafeConsumer<T> consumer)
			throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertSame(throwable,
				Assertions.assertThrows(IllegalStateException.class, () -> consumer.accept(throwable)).getCause());
	}

	private <T extends Throwable> void assertUnsafe(Class<T> throwableClass,
			UnsafeBiFunction<Executable, Object[], Object> invocation) throws Throwable {
		var echo = new Object[] { new Object() };
		var throwable = new Object[] { throwableClass.getDeclaredConstructor().newInstance() };
		assertSame(throwable[0],
				Assertions.assertThrows(throwableClass, () -> invocation.apply(UNSAFE_CONSTRUCTOR, throwable)));
		assertSame(throwable[0],
				Assertions.assertThrows(throwableClass, () -> invocation.apply(UNSAFE_STATIC_METHOD, throwable)));
		assertSame(echo[0], invocation.apply(SAFE_STATIC_METHOD, echo));

		var instance = invocation.apply(SAFE_CONSTRUCTOR, O0);
		var instanceEcho = new Object[] { instance, new Object() };
		var instanceThrowable = new Object[] { instance, throwableClass.getDeclaredConstructor().newInstance() };
		assertSame(instanceThrowable[1],
				Assertions.assertThrows(throwableClass, () -> invocation.apply(UNSAFE_METHOD, instanceThrowable)));
		assertSame(instanceEcho[1], invocation.apply(SAFE_METHOD, instanceEcho));
	}

	private void assertRunnable(Class<? extends Throwable> throwableClass, UnsafeConsumer<UnsafeRunnable> consumer)
			throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertDoesNotThrow(() -> consumer.accept(() -> {
		}));
		assertSame(throwable, Assertions.assertThrows(throwableClass, () -> consumer.accept(() -> {
			throw throwable;
		})));
	}

	private void assertSupplier(Class<? extends Throwable> throwableClass,
			UnsafeFunction<UnsafeSupplier<Object>, Object> function) throws Throwable {
		var echo = new Object();
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertSame(echo, function.apply(() -> echo));
		assertSame(throwable, Assertions.assertThrows(throwableClass, () -> function.apply(() -> {
			throw throwable;
		})));
	}

	private void assertConsumer(Class<? extends Throwable> throwableClass,
			UnsafeConsumer<UnsafeConsumer<Object>> consumer) throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertDoesNotThrow(() -> consumer.accept(o -> {
		}));
		assertSame(throwable, Assertions.assertThrows(throwableClass, () -> consumer.accept(o -> {
			throw throwable;
		})));
	}

	private void assertBiConsumer(Class<? extends Throwable> throwableClass,
			UnsafeConsumer<UnsafeBiConsumer<Object, Object>> consumer) throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertDoesNotThrow(() -> consumer.accept((a, b) -> {
		}));
		assertSame(throwable, Assertions.assertThrows(throwableClass, () -> consumer.accept((a, b) -> {
			throw throwable;
		})));
	}

	private void assertFunction(Class<? extends Throwable> throwableClass, Object echo,
			UnsafeFunction<UnsafeFunction<Object, Object>, Object> consumer) throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertSame(echo, consumer.apply(o -> o));
		assertSame(throwable, Assertions.assertThrows(throwableClass, () -> consumer.apply(o -> {
			throw throwable;
		})));
	}

	private void assertBiFunction(Class<? extends Throwable> throwableClass, Object echo1, Object echo2,
			UnsafeFunction<UnsafeBiFunction<Object, Object, Object[]>, Object[]> consumer) throws Throwable {
		var throwable = throwableClass.getDeclaredConstructor().newInstance();
		assertArrayEquals(new Object[] { echo1, echo2 }, consumer.apply((a, b) -> new Object[] { a, b }));
		assertSame(throwable, Assertions.assertThrows(throwableClass, () -> consumer.apply((a, b) -> {
			throw throwable;
		})));
	}

	@Test
	public void testUncheckedReturnsRuntimeException() throws Throwable {
		assertReturns(RuntimeException.class, IuException::unchecked);
	}

	@Test
	public void testUncheckedThrowsError() throws Throwable {
		assertThrows(Error.class, IuException::unchecked);
	}

	@Test
	public void testUncheckedThrowsIllegalState() throws Throwable {
		assertThrowsIllegalState(Exception.class, IuException::unchecked);
	}

	@Test
	public void testCheckedReturnsException() throws Throwable {
		assertReturns(Exception.class, IuException::checked);
	}

	@Test
	public void testCheckedThrowsError() throws Throwable {
		assertThrows(Error.class, IuException::checked);
	}

	@Test
	public void testCheckedThrowsIllegalState() throws Throwable {
		assertThrowsIllegalState(CustomThrowable.class, IuException::checked);
	}

	@Test
	public void testChecked1ReturnsCheckedException1() throws Throwable {
		assertReturns(CheckedException1.class, e -> IuException.checked(e, CheckedException1.class));
	}

	@Test
	public void testChecked1ThrowsRuntimeException() throws Throwable {
		assertThrows(RuntimeException.class, e -> IuException.checked(e, CheckedException1.class));
	}

	@Test
	public void testChecked1ThrowsError() throws Throwable {
		assertThrows(Error.class, e -> IuException.checked(e, CheckedException1.class));
	}

	@Test
	public void testChecked1ThrowsIllegalState() throws Throwable {
		assertThrowsIllegalState(Exception.class, e -> IuException.checked(e, CheckedException1.class));
	}

	@Test
	public void testChecked2ReturnsCheckedException1() throws Throwable {
		assertReturns(CheckedException1.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class));
	}

	@Test
	public void testChecked2ThrowsCheckedException2() throws Throwable {
		assertThrows(CheckedException2.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class));
	}

	@Test
	public void testChecked2ThrowsRuntimeException() throws Throwable {
		assertThrows(RuntimeException.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class));
	}

	@Test
	public void testChecked2ThrowsError() throws Throwable {
		assertThrows(Error.class, e -> IuException.checked(e, CheckedException1.class, CheckedException2.class));
	}

	@Test
	public void testChecked2ThrowsIllegalState() throws Throwable {
		assertThrowsIllegalState(Exception.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class));
	}

	@Test
	public void testChecked3ReturnsCheckedException1() throws Throwable {
		assertReturns(CheckedException1.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class, CheckedException3.class));
	}

	@Test
	public void testChecked3ThrowsCheckedException2() throws Throwable {
		assertThrows(CheckedException2.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class, CheckedException3.class));
	}

	@Test
	public void testChecked3ThrowsCheckedException3() throws Throwable {
		assertThrows(CheckedException3.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class, CheckedException3.class));
	}

	@Test
	public void testChecked3ThrowsRuntimeException() throws Throwable {
		assertThrows(RuntimeException.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class, CheckedException3.class));
	}

	@Test
	public void testChecked3ThrowsError() throws Throwable {
		assertThrows(Error.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class, CheckedException3.class));
	}

	@Test
	public void testChecked3ThrowsIllegalState() throws Throwable {
		assertThrowsIllegalState(Exception.class,
				e -> IuException.checked(e, CheckedException1.class, CheckedException2.class, CheckedException3.class));
	}

	private Object doInvoke(Executable o, Object[] a) throws Throwable {
		if (o instanceof Constructor)
			return ((Constructor<?>) o).newInstance(a);

		var method = (Method) o;
		var mod = method.getModifiers();
		if ((mod | Modifier.STATIC) == mod)
			return method.invoke(null, a);
		else
			return method.invoke(a[0], Arrays.copyOfRange(a, 1, a.length));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUnsafeUnchecked() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(RuntimeException.class, (o, a) -> IuException.uncheckedInvocation(() -> doInvoke(o, a)));
			mockIuException.verify(() -> IuException.uncheckedInvocation(any(UnsafeSupplier.class)), times(6));
			mockIuException.verify(() -> IuException.unchecked(any(Throwable.class)), times(3));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUnsafeChecked() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(Exception.class, (o, a) -> IuException.checkedInvocation(() -> doInvoke(o, a)));
			mockIuException.verify(() -> IuException.checkedInvocation(any(UnsafeSupplier.class)), times(6));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class)), times(3));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUnsafeChecked1() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(CheckedException1.class,
					(o, a) -> IuException.checkedInvocation(CheckedException1.class, () -> doInvoke(o, a)));
			mockIuException.verify(
					() -> IuException.checkedInvocation(eq(CheckedException1.class), any(UnsafeSupplier.class)),
					times(6));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class), eq(CheckedException1.class)),
					times(3));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUnsafeChecked2() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(CheckedException1.class, (o, a) -> IuException.checkedInvocation(CheckedException1.class,
					CheckedException2.class, () -> doInvoke(o, a)));
			mockIuException.verify(() -> IuException.checkedInvocation(eq(CheckedException1.class),
					eq(CheckedException2.class), any(UnsafeSupplier.class)), times(6));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(3));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUnsafeChecked3() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(CheckedException1.class, (o, a) -> IuException.checkedInvocation(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, () -> doInvoke(o, a)));
			mockIuException.verify(() -> IuException.checkedInvocation(eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class), any(UnsafeSupplier.class)), times(6));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(3));
		}
	}

	@Test
	public void testUncheckedUnsafeRunnable() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertRunnable(RuntimeException.class, r -> IuException.unchecked(r));
			mockIuException.verify(() -> IuException.unchecked(any(UnsafeRunnable.class)), times(2));
			mockIuException.verify(() -> IuException.unchecked(any(RuntimeException.class), eq((String) null)), times(1));
		}
	}

	@Test
	public void testCheckedUnsafeRunnable() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertRunnable(Exception.class, r -> IuException.checked(r));
			mockIuException.verify(() -> IuException.checked(any(UnsafeRunnable.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(Exception.class)), times(1));
		}
	}

	@Test
	public void testChecked1UnsafeRunnable() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertRunnable(CheckedException1.class, r -> IuException.checked(CheckedException1.class, r));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), any(UnsafeRunnable.class)),
					times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class)),
					times(1));
		}
	}

	@Test
	public void testChecked2UnsafeRunnable() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertRunnable(CheckedException1.class,
					r -> IuException.checked(CheckedException1.class, CheckedException2.class, r));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					any(UnsafeRunnable.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(1));
		}
	}

	@Test
	public void testChecked3UnsafeRunnable() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertRunnable(CheckedException1.class, r -> IuException.checked(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, r));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(CheckedException3.class), any(UnsafeRunnable.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUncheckedUnsafeSupplier() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertSupplier(RuntimeException.class, r -> IuException.unchecked(r));
			mockIuException.verify(() -> IuException.unchecked(any(UnsafeSupplier.class)), times(2));
			mockIuException.verify(() -> IuException.unchecked(any(RuntimeException.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCheckedUnsafeSupplier() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertSupplier(Exception.class, r -> IuException.checked(r));
			mockIuException.verify(() -> IuException.checked(any(UnsafeSupplier.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(Exception.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked1UnsafeSupplier() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertSupplier(CheckedException1.class, r -> IuException.checked(CheckedException1.class, r));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), any(UnsafeSupplier.class)),
					times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class)),
					times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked2UnsafeSupplier() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertSupplier(CheckedException1.class,
					r -> IuException.checked(CheckedException1.class, CheckedException2.class, r));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					any(UnsafeSupplier.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked3UnsafeSupplier() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertSupplier(CheckedException1.class, r -> IuException.checked(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, r));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(CheckedException3.class), any(UnsafeSupplier.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUncheckedUnsafeConsumer() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertConsumer(RuntimeException.class, c -> IuException.unchecked(echo, c));
			mockIuException.verify(() -> IuException.unchecked(eq(echo), any(UnsafeConsumer.class)), times(2));
			mockIuException.verify(() -> IuException.unchecked(any(RuntimeException.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCheckedUnsafeConsumer() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertConsumer(RuntimeException.class, c -> IuException.checked(echo, c));
			mockIuException.verify(() -> IuException.checked(eq(echo), any(UnsafeConsumer.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(Exception.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked1UnsafeConsumer() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertConsumer(CheckedException1.class, c -> IuException.checked(CheckedException1.class, echo, c));
			mockIuException.verify(
					() -> IuException.checked(eq(CheckedException1.class), eq(echo), any(UnsafeConsumer.class)),
					times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class)),
					times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked2UnsafeConsumer() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertConsumer(CheckedException1.class,
					c -> IuException.checked(CheckedException1.class, CheckedException2.class, echo, c));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(echo), any(UnsafeConsumer.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked3UnsafeConsumer() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertConsumer(CheckedException1.class, c -> IuException.checked(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, echo, c));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(CheckedException3.class), eq(echo), any(UnsafeConsumer.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUncheckedUnsafeBiConsumer() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiConsumer(RuntimeException.class, c -> IuException.unchecked(echo1, echo2, c));
			mockIuException.verify(() -> IuException.unchecked(eq(echo1), eq(echo2), any(UnsafeBiConsumer.class)),
					times(2));
			mockIuException.verify(() -> IuException.unchecked(any(RuntimeException.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCheckedUnsafeBiConsumer() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiConsumer(RuntimeException.class, c -> IuException.checked(echo1, echo2, c));
			mockIuException.verify(() -> IuException.checked(eq(echo1), eq(echo2), any(UnsafeBiConsumer.class)),
					times(2));
			mockIuException.verify(() -> IuException.checked(any(Exception.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked1UnsafeBiConsumer() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiConsumer(CheckedException1.class,
					c -> IuException.checked(CheckedException1.class, echo1, echo2, c));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(echo1), eq(echo2),
					any(UnsafeBiConsumer.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class)),
					times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked2UnsafeBiConsumer() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiConsumer(CheckedException1.class,
					c -> IuException.checked(CheckedException1.class, CheckedException2.class, echo1, echo2, c));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(echo1), eq(echo2), any(UnsafeBiConsumer.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked3UnsafeBiConsumer() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiConsumer(CheckedException1.class, c -> IuException.checked(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, echo1, echo2, c));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(CheckedException3.class), eq(echo1), eq(echo2), any(UnsafeBiConsumer.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUncheckedUnsafeFunction() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertFunction(RuntimeException.class, echo, f -> IuException.unchecked(echo, f));
			mockIuException.verify(() -> IuException.unchecked(eq(echo), any(UnsafeFunction.class)), times(2));
			mockIuException.verify(() -> IuException.unchecked(any(RuntimeException.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCheckedUnsafeFunction() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertFunction(Exception.class, echo, f -> IuException.checked(echo, f));
			mockIuException.verify(() -> IuException.checked(eq(echo), any(UnsafeFunction.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(Exception.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked1UnsafeFunction() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertFunction(CheckedException1.class, echo, f -> IuException.checked(CheckedException1.class, echo, f));
			mockIuException.verify(
					() -> IuException.checked(eq(CheckedException1.class), eq(echo), any(UnsafeFunction.class)),
					times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class)),
					times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked2UnsafeFunction() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertFunction(CheckedException1.class, echo,
					f -> IuException.checked(CheckedException1.class, CheckedException2.class, echo, f));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(echo), any(UnsafeFunction.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked3UnsafeFunction() throws Throwable {
		var echo = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertFunction(CheckedException1.class, echo, f -> IuException.checked(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, echo, f));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(CheckedException3.class), eq(echo), any(UnsafeFunction.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testUncheckedUnsafeBiFunction() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiFunction(RuntimeException.class, echo1, echo2, f -> IuException.unchecked(echo1, echo2, f));
			mockIuException.verify(() -> IuException.unchecked(eq(echo1), eq(echo2), any(UnsafeBiFunction.class)),
					times(2));
			mockIuException.verify(() -> IuException.unchecked(any(RuntimeException.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCheckedUnsafeBiFunction() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiFunction(Exception.class, echo1, echo2, f -> IuException.checked(echo1, echo2, f));
			mockIuException.verify(() -> IuException.checked(eq(echo1), eq(echo2), any(UnsafeBiFunction.class)),
					times(2));
			mockIuException.verify(() -> IuException.checked(any(Exception.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked1UnsafeBiFunction() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiFunction(CheckedException1.class, echo1, echo2,
					f -> IuException.checked(CheckedException1.class, echo1, echo2, f));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(echo1), eq(echo2),
					any(UnsafeBiFunction.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class)),
					times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked2UnsafeBiFunction() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiFunction(CheckedException1.class, echo1, echo2,
					f -> IuException.checked(CheckedException1.class, CheckedException2.class, echo1, echo2, f));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(echo1), eq(echo2), any(UnsafeBiFunction.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(1));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testChecked3UnsafeBiFunction() throws Throwable {
		var echo1 = new Object();
		var echo2 = new Object();
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertBiFunction(CheckedException1.class, echo1, echo2, f -> IuException.checked(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, echo1, echo2, f));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(CheckedException3.class), eq(echo1), eq(echo2), any(UnsafeBiFunction.class)), times(2));
			mockIuException.verify(() -> IuException.checked(any(CheckedException1.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(1));
		}
	}

	@Test
	public void testLeavesResourceOpen() throws Exception {
		var closeable = mock(AutoCloseable.class);
		assertSame(closeable, IuException.initialize(closeable, c -> {
			assertSame(closeable, c);
			return c;
		}));
		verify(closeable, times(0)).close();
	}

	@Test
	public void testClosesOnException() throws Exception {
		var closeable = mock(AutoCloseable.class);
		var exception = new Exception();
		assertSame(exception, Assertions.assertThrows(Exception.class, () -> IuException.initialize(closeable, c -> {
			throw exception;
		})));
		verify(closeable, times(1)).close();
	}

	@Test
	public void testClosesAndSuppressesCloseExceptionOnError() throws Exception {
		var exception = new Exception();
		var closeable = mock(AutoCloseable.class);
		doThrow(exception).when(closeable).close();
		assertSame(exception, Assertions.assertThrows(Error.class, () -> IuException.initialize(closeable, c -> {
			throw new Error();
		})).getSuppressed()[0]);
		verify(closeable, times(1)).close();
	}

	@Test
	public void testSuppress() {
		final var exception = new Exception();
		assertSame(exception, IuException.suppress(null, () -> {
			throw exception;
		}));
	}

	@Test
	public void testSuppressMulti() {
		final var exception = new Exception();
		final var e2 = new Exception();

		assertSame(exception, Assertions.assertThrows(Exception.class, () -> IuException.suppress(() -> {
			throw exception;
		}, () -> {
			throw e2;
		})));
		assertSame(e2, exception.getSuppressed()[0]);
	}

	@Test
	public void testNoErrorNoSuppress() throws Throwable {
		assertDoesNotThrow(() -> IuException.suppress(() -> {
		}));
	}

	@Test
	public void testStandardRuntimeExceptions() {
		assertStandardRuntimeException(IuBadRequestException.class);
		assertStandardRuntimeException(IuNotFoundException.class);
		assertStandardRuntimeException(IuAuthorizationFailedException.class);
		assertStandardRuntimeException(IuOutOfServiceException.class);
	}
	
	private void assertStandardRuntimeException(Class<? extends RuntimeException> exceptionClass) {
		Assertions.assertThrows(exceptionClass, () -> {
			throw exceptionClass.getConstructor().newInstance();
		});

		final var m = IdGenerator.generateId();
		assertEquals(m, Assertions.assertThrows(exceptionClass, () -> {
			throw exceptionClass.getConstructor(String.class).newInstance(m);
		}).getMessage());

		final var c = new Exception();
		assertEquals(c, Assertions.assertThrows(exceptionClass, () -> {
			throw exceptionClass.getConstructor(Throwable.class).newInstance(c);
		}).getCause());

		final var e = Assertions.assertThrows(exceptionClass, () -> {
			throw exceptionClass.getConstructor(String.class, Throwable.class).newInstance(m, c);
		});
		assertEquals(m, e.getMessage());
		assertEquals(c, e.getCause());
	}

}
