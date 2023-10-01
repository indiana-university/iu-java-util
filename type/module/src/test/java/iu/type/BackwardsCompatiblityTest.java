package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.type.DefaultInterceptor;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;

@SuppressWarnings("javadoc")
@ExtendWith(LegacyContextSupport.class)
public class BackwardsCompatiblityTest {

	@Test
	public void testNonLegacyReturnsSameType() throws ClassNotFoundException {
		assertSame(Resource.class, BackwardsCompatibility.getNonLegacyClass(Resource.class));
	}

	@Test
	public void testLegacyReturnsSameType() throws ClassNotFoundException {
		assertSame(Resource.class, BackwardsCompatibility.getLegacyClass(Resource.class));
	}

	@Test
	public void testConvertsJavaxToJakarta() throws ClassNotFoundException {
		assertSame(Resource.class, BackwardsCompatibility
				.getNonLegacyClass(LegacyContextSupport.get().loadClass("javax.annotation.Resource")));
	}

	@Test
	public void testConvertsJakartaToJavax() throws Exception {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			assertSame(LegacyContextSupport.get().loadClass("javax.annotation.Resource"),
					BackwardsCompatibility.getLegacyClass(Resource.class));
			return null;
		});
	}

	@Test
	public void testConvertsDefaultInterceptorFromIuJee6() throws ClassNotFoundException {
		assertSame(DefaultInterceptor.class, BackwardsCompatibility
				.getNonLegacyClass(LegacyContextSupport.get().loadClass("edu.iu.spi.DefaultInterceptor")));
	}

	@Test
	public void testConvertsDefaultInterceptorToIuJee6() throws Exception {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			assertSame(LegacyContextSupport.get().loadClass("edu.iu.spi.DefaultInterceptor"),
					BackwardsCompatibility.getLegacyClass(DefaultInterceptor.class));
			return null;
		});
	}

	@Test
	public void testTriesJakartaAndJavax() throws Exception {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			var classNotFound = assertThrows(ClassNotFoundException.class,
					() -> BackwardsCompatibility.getLegacyClass(Jsonb.class));
			assertEquals("javax" + Jsonb.class.getName().substring(7), classNotFound.getMessage());
			assertEquals(Jsonb.class.getName(), classNotFound.getSuppressed()[0].getMessage());
			return null;
		});
	}

	@Test
	public void testTriesIuType() throws Exception {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			var classNotFound = assertThrows(ClassNotFoundException.class,
					() -> BackwardsCompatibility.getLegacyClass(IuType.class));
			assertEquals(IuType.class.getName(), classNotFound.getMessage());
			return null;
		});
	}

}
