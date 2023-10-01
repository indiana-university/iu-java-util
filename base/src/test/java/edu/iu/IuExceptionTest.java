package edu.iu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

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

	@Test
	public void testUnsafeUnchecked() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(RuntimeException.class, (o, a) -> IuException.unchecked(o, a));
			mockIuException.verify(() -> IuException.unchecked(any(Executable.class), any()), times(3));
			mockIuException.verify(() -> IuException.unchecked(any(Throwable.class)), times(3));
		}
	}

	@Test
	public void testUnsafeChecked() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(Exception.class, (o, a) -> IuException.checked(o, a));
			mockIuException.verify(() -> IuException.checked(any(Executable.class), any()), times(3));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class)), times(3));
		}
	}

	@Test
	public void testUnsafeChecked1() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(CheckedException1.class, (o, a) -> IuException.checked(CheckedException1.class, o, a));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), any(Executable.class), any()),
					times(3));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class), eq(CheckedException1.class)),
					times(3));
		}
	}

	@Test
	public void testUnsafeChecked2() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(CheckedException1.class,
					(o, a) -> IuException.checked(CheckedException1.class, CheckedException2.class, o, a));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					any(Executable.class), any()), times(3));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class), eq(CheckedException1.class),
					eq(CheckedException2.class)), times(3));
		}
	}

	@Test
	public void testUnsafeChecked3() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertUnsafe(CheckedException1.class, (o, a) -> IuException.checked(CheckedException1.class,
					CheckedException2.class, CheckedException3.class, o, a));
			mockIuException.verify(() -> IuException.checked(eq(CheckedException1.class), eq(CheckedException2.class),
					eq(CheckedException3.class), any(Executable.class), any()), times(3));
			mockIuException.verify(() -> IuException.checked(any(Throwable.class), eq(CheckedException1.class),
					eq(CheckedException2.class), eq(CheckedException3.class)), times(3));
		}
	}

	@Test
	public void testUncheckedUnsafeRunnable() throws Throwable {
		try (var mockIuException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			assertRunnable(RuntimeException.class, r -> IuException.unchecked(r));
			mockIuException.verify(() -> IuException.unchecked(any(UnsafeRunnable.class)), times(2));
			mockIuException.verify(() -> IuException.unchecked(any(RuntimeException.class)), times(1));
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

}
