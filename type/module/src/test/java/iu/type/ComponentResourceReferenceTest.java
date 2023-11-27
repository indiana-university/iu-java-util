package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuResource;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;

@SuppressWarnings("javadoc")
public class ComponentResourceReferenceTest extends IuTypeTestCase {

	@Test
	public void testUnbound() {
		class HasResourceRef {
			@Resource
			private Object resource;
		}
		final var ref = new ComponentResourceReference<>(TypeFactory.resolveRawClass(HasResourceRef.class).field("resource"));
		assertEquals("resource", ref.name());
		assertEquals(TypeFactory.resolveRawClass(Object.class), ref.type());
		assertEquals(HasResourceRef.class, ref.referrerType().erasedClass());
		assertEquals(
				"ComponentResourceReference [key=resource, attribute=HasResourceRef#resource:Object, boundResource=null]",
				ref.toString());
	}

	@Test
	public void testMustBeAnnotated() {
		class HasResourceRef {
			@SuppressWarnings("unused")
			private Object resource;
		}
		assertEquals("Missing @Resource: HasResourceRef#resource:Object",
				assertThrows(IllegalArgumentException.class,
						() -> new ComponentResourceReference<>(TypeFactory.resolveRawClass(HasResourceRef.class).field("resource")))
						.getMessage());
	}

	@Test
	public void testMustBeAssignable() {
		class HasResourceRef {
			@Resource(type = String.class)
			private Number resource;
		}
		assertEquals("attribute HasResourceRef#resource:Number is not assignable from IuType[String]",
				assertThrows(IllegalArgumentException.class,
						() -> new ComponentResourceReference<>(TypeFactory.resolveRawClass(HasResourceRef.class).field("resource")))
						.getMessage());
	}

	@Test
	public void testSetsNameAndType() {
		class HasResourceRef {
			@Resource(name = "foo", type = Number.class)
			private Object resource;
		}
		final var ref = new ComponentResourceReference<>(TypeFactory.resolveRawClass(HasResourceRef.class).field("resource"));
		assertEquals("foo", ref.name());
		assertEquals(TypeFactory.resolveRawClass(Number.class), ref.type());
		assertEquals(HasResourceRef.class, ref.referrerType().erasedClass());
		assertEquals(
				"ComponentResourceReference [key=foo!java.lang.Number, attribute=HasResourceRef#resource:Object, boundResource=null]",
				ref.toString());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBind() throws Exception {
		class HasResourceRef {
			@Resource
			private Object resource;
		}
		final var ref = new ComponentResourceReference<>(TypeFactory.resolveRawClass(HasResourceRef.class).field("resource"));
		final var value = new Object();
		final var refer1 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor(getClass()).exec(this);

		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("resource");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		when(resource.get()).thenReturn(value);
		ref.bind(resource);
		assertSame(value, refer1.resource);

		final var refer2 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor(getClass()).exec(this);
		assertSame(value, refer2.resource);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBindRequiresNameMatch() {
		class HasResourceRef {
			@Resource
			private Object resource;
		}
		final var ref = new ComponentResourceReference<>(TypeFactory.resolveRawClass(HasResourceRef.class).field("resource"));
		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("foo");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		assertEquals("Resource " + resource
				+ " does not apply to ComponentResourceReference [key=resource, attribute=HasResourceRef#resource:Object, boundResource=null]",
				assertThrows(IllegalArgumentException.class, () -> ref.bind(resource)).getMessage());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBindRequiresSuperType() {
		class HasResourceRef {
			@Resource
			private Number resource;
		}
		final var ref = new ComponentResourceReference<>(TypeFactory.resolveRawClass(HasResourceRef.class).field("resource"));
		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("resource");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(String.class));
		assertEquals("Resource " + resource
				+ " does not apply to ComponentResourceReference [key=resource!java.lang.Number, attribute=HasResourceRef#resource:Number, boundResource=null]",
				assertThrows(IllegalArgumentException.class, () -> ref.bind(resource)).getMessage());
	}

}
