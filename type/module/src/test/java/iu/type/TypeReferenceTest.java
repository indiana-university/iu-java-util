package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuExecutable;
import edu.iu.type.IuField;
import edu.iu.type.IuReferenceKind;

@SuppressWarnings("javadoc")
public class TypeReferenceTest {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNonArgumentKindIsStable() {
		var type = mock(TypeFacade.class);
		var ref = new TypeReference(IuReferenceKind.ERASURE, type, type);
		assertEquals(IuReferenceKind.ERASURE, ref.kind());
		assertSame(type, ref.referent());
		assertSame(type, ref.referrer());
		assertNull(ref.name());
		assertTrue(ref.index() < 0);
		assertEquals(ref, ref);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNonArgumentKindConstructorRequiresNonArgumentKind() {
		var type = mock(TypeFacade.class);
		var field = mock(IuField.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.FIELD, field, type));
		var exec = mock(IuExecutable.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.PARAMETER, exec, type));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNamedArgumentKindIsStable() {
		var type = mock(TypeFacade.class);
		var field = mock(IuField.class);
		var ref = new TypeReference(IuReferenceKind.FIELD, field, type, "");
		assertEquals(IuReferenceKind.FIELD, ref.kind());
		assertSame(type, ref.referent());
		assertSame(field, ref.referrer());
		assertEquals("", ref.name());
		assertTrue(ref.index() < 0);
		assertEquals(ref, ref);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNamedArgumentKindConstructorRequiresNamedArgumentKind() {
		var type = mock(TypeFacade.class);
		var exec = mock(IuExecutable.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.PARAMETER, exec, type, ""));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIndexedArgumentKindIsStable() {
		var type = mock(TypeFacade.class);
		var exec = mock(IuExecutable.class);
		var ref = new TypeReference(IuReferenceKind.PARAMETER, exec, type, 0);
		assertEquals(IuReferenceKind.PARAMETER, ref.kind());
		assertSame(type, ref.referent());
		assertSame(exec, ref.referrer());
		assertNull(ref.name());
		assertEquals(0, ref.index());
		assertEquals(ref, ref);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIndexedArgumentKindConstructorRequiresIndexedArgumentKind() {
		var type = mock(TypeFacade.class);
		var field = mock(IuField.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.FIELD, field, type, 0));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIndexedArgumentKindConstructorRequiresValidIndex() {
		var type = mock(TypeFacade.class);
		var exec = mock(IuExecutable.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.PARAMETER, exec, type, -1));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEquivalence() {
		var type1 = mock(TypeFacade.class);
		var type2 = mock(TypeFacade.class);
		var ref1 = new TypeReference(IuReferenceKind.ERASURE, type1, type2);
		assertEquals(ref1, new TypeReference(IuReferenceKind.ERASURE, type1, type2));
		var ref2 = new TypeReference(IuReferenceKind.ERASURE, type2, type1);
		var set = new HashSet<>(Set.of(ref1, ref2));
		assertTrue(set.contains(ref1));
		assertTrue(set.contains(ref2));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEqualsNullCheck() {
		var type = mock(TypeFacade.class);
		var ref = new TypeReference(IuReferenceKind.ERASURE, type, type);
		assertNotEquals(ref, null);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testReferrerUsesIdentityForEquals() {
		var type1 = TypeFacade.builder(Object.class).build();
		var type2 = TypeFacade.builder(Object.class).build();
		assertEquals(type1, type2);
		assertEquals(type2, type1);
		assertNotSame(type1, type2);
		var ref1 = new TypeReference(IuReferenceKind.ERASURE, type1, type1);
		var ref2 = new TypeReference(IuReferenceKind.ERASURE, type2, type1);
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentNamesNotEquals() {
		var type = mock(TypeFacade.class);
		var field = mock(IuField.class);
		var ref1 = new TypeReference(IuReferenceKind.FIELD, field, type, "a");
		var ref2 = new TypeReference(IuReferenceKind.FIELD, field, type, "b");
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentReferentNotEquals() {
		var type1 = mock(TypeFacade.class);
		var type2 = mock(TypeFacade.class);
		var field = mock(IuField.class);
		var ref1 = new TypeReference(IuReferenceKind.FIELD, field, type1, "a");
		var ref2 = new TypeReference(IuReferenceKind.FIELD, field, type2, "a");
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentIndexNotEquals() {
		var type = mock(TypeFacade.class);
		var exec = mock(IuExecutable.class);
		var ref1 = new TypeReference(IuReferenceKind.PARAMETER, exec, type, 1);
		var ref2 = new TypeReference(IuReferenceKind.PARAMETER, exec, type, 2);
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentKindsNotEquals() {
		var type = mock(TypeFacade.class);
		var ref1 = new TypeReference(IuReferenceKind.ERASURE, type, type);
		var ref2 = new TypeReference(IuReferenceKind.SUPER, type, type);
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

}