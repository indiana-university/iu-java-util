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
package iu.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@SuppressWarnings("javadoc")
public class BeanModelTest {

	public interface Base {
		@NotNull
		String getName();

		@Size(min = 1)
		String getShared();

		String getUnconstrained();
	}

	public interface Middle extends Base {
		@Override
		@Pattern(regexp = "[a-z]+")
		String getName();
	}

	public interface Left extends Base {
	}

	public interface Right extends Base {
	}

	public interface Diamond extends Left, Right {
	}

	public static class Parent {
		private String tag;

		public String getTag() {
			return tag;
		}

		public void setTag(String tag) {
			this.tag = tag;
		}
	}

	public static class Child extends Parent {
		@NotNull
		private String note;

		public String getNote() {
			return note;
		}

		public void setNote(String note) {
			this.note = note;
		}
	}

	public static class ConstrainedSetter {
		private String value;

		public String getValue() {
			return value;
		}

		@NotNull
		public void setValue(String value) {
			this.value = value;
		}
	}

	public static class WriteOnly {
		public void setThing(String thing) {
			// a property with no read method has nothing to validate
		}
	}

	public interface Triple<A, B, C> {
	}

	public interface Containers {
		@Valid
		List<@NotBlank String> getTags();

		Map<@NotBlank String, @NotNull String> getByCode();

		@NotNull
		Triple<@NotBlank String, String, String> getTriple();

		@NotEmpty
		String[] getWords();

		@Valid
		Base[] getAddresses();

		Optional<@NotBlank String> getMaybe();

		List<@Valid Base> getNested();

		Map<@Valid Base, @Valid Base> getBoth();

		Map<@NotBlank String, String> getKeyConstrainedOnly();

		Map<@Valid Base, String> getKeyCascadeOnly();
	}

	public interface WithCustomConstraint {
		@ConstraintsTest.Custom
		String getThing();
	}

	public interface WithIrrelevantAnnotation {
		@NotNull
		@ConstraintsTest.NotAConstraint
		String getBoth();
	}

	public interface WithGroupedConstraint {
		@NotNull(groups = ConstraintsTest.Group.class)
		String getGroupedOnly();
	}

	public interface WithRepeatedConstraint {
		@Pattern(regexp = "a+")
		@Pattern(regexp = "b+")
		String getRepeated();
	}

	private static Map<String, BeanProperty> properties(Class<?> type) {
		final Map<String, BeanProperty> byName = new LinkedHashMap<>();
		for (final var property : BeanModel.of(type).properties())
			byName.put(property.name(), property);
		return byName;
	}

	private static Set<Class<? extends Annotation>> types(List<ConstraintDeclaration> declarations) {
		final Set<Class<? extends Annotation>> annotationTypes = new LinkedHashSet<>();
		for (final var declaration : declarations)
			annotationTypes.add(declaration.annotation().annotationType());
		return annotationTypes;
	}

	@Test
	public void testModelsAreCached() {
		assertSame(BeanModel.of(Base.class), BeanModel.of(Base.class));
	}

	@Test
	public void testUnconstrainedPropertiesAreNotModeled() {
		final var properties = properties(Base.class);
		assertEquals(Set.of("name", "shared"), properties.keySet());
		assertFalse(properties.containsKey("unconstrained"),
				() -> "a property with no rule should never be read");
		assertFalse(properties.containsKey("class"), () -> "getClass is not a bean property to validate");
	}

	@Test
	public void testPropertiesAreOrderedByName() {
		assertEquals(List.of("name", "shared"), List.copyOf(properties(Base.class).keySet()));
	}

	@Test
	public void testConstraintsAreUnionedAcrossTheHierarchy() {
		// Base declares @NotNull and Middle overrides with @Pattern; both apply
		final var name = properties(Middle.class).get("name");
		assertEquals(Set.of(NotNull.class, Pattern.class), types(name.constraints()));
	}

	@Test
	public void testASuperInterfaceReachedTwiceIsProcessedOnce() {
		final var shared = properties(Diamond.class).get("shared");
		assertEquals(1, shared.constraints().size(),
				() -> "Base is reached through both Left and Right: " + shared.constraints());
	}

	@Test
	public void testSuperclassPropertiesAndFieldConstraints() {
		final var properties = properties(Child.class);
		assertEquals(Set.of("note"), properties.keySet());
		assertEquals(Set.of(NotNull.class), types(properties.get("note").constraints()),
				() -> "constraint is declared on the backing field");
	}

	@Test
	public void testSetterConstraints() {
		final var value = properties(ConstrainedSetter.class).get("value");
		assertNotNull(value);
		assertEquals(Set.of(NotNull.class), types(value.constraints()));
	}

	@Test
	public void testWriteOnlyPropertyIsSkipped() {
		assertEquals(Set.of(), properties(WriteOnly.class).keySet());
	}

	@Test
	public void testIrrelevantAnnotationsAreIgnored() {
		final var both = properties(WithIrrelevantAnnotation.class).get("both");
		assertEquals(Set.of(NotNull.class), types(both.constraints()));
	}

	@Test
	public void testConstraintOutsideTheDefaultGroupIsExcluded() {
		assertEquals(Set.of(), properties(WithGroupedConstraint.class).keySet());
	}

	@Test
	public void testEveryElementOfARepeatableContainerIsKept() {
		final var repeated = properties(WithRepeatedConstraint.class).get("repeated");
		assertEquals(2, repeated.constraints().size(), () -> "both @Pattern declarations must be kept");

		final Set<String> expressions = new LinkedHashSet<>();
		for (final var declaration : repeated.constraints())
			expressions.add(((Pattern) declaration.annotation()).regexp());
		assertEquals(Set.of("a+", "b+"), expressions);
	}

	@Test
	public void testACustomConstraintIsRefusedRatherThanIgnored() {
		final var e = assertThrows(UnsupportedOperationException.class,
				() -> BeanModel.of(WithCustomConstraint.class));
		assertTrue(e.getMessage().contains("Custom"), e::getMessage);
		assertTrue(e.getMessage().contains("getThing") || e.getMessage().contains("thing"), e::getMessage);
	}

	@Test
	public void testSingleTypeArgumentSuppliesElementConstraints() {
		final var tags = properties(Containers.class).get("tags");
		assertTrue(tags.cascade());
		assertEquals(Set.of(NotBlank.class), types(tags.elementConstraints()));
		assertEquals(Set.of(), types(tags.keyConstraints()));
	}

	@Test
	public void testTwoTypeArgumentsSupplyKeyAndValueConstraints() {
		final var byCode = properties(Containers.class).get("byCode");
		assertEquals(Set.of(NotBlank.class), types(byCode.keyConstraints()));
		assertEquals(Set.of(NotNull.class), types(byCode.elementConstraints()));
	}

	@Test
	public void testOtherTypeArgumentCountsAreIgnored() {
		final var triple = properties(Containers.class).get("triple");
		assertEquals(Set.of(NotNull.class), types(triple.constraints()));
		assertEquals(Set.of(), types(triple.elementConstraints()));
		assertEquals(Set.of(), types(triple.keyConstraints()));
	}

	@Test
	public void testAnArrayConstraintAppliesToTheArrayAndNotAlsoToItsElements() {
		// @NotEmpty String[] is, per the JLS, both a declaration annotation on the
		// accessor and a type annotation on the component. Reading the component too
		// would apply the constraint twice, so arrays contribute no element
		// constraints; List<@NotBlank String> is the unambiguous form.
		final var words = properties(Containers.class).get("words");
		assertEquals(Set.of(NotEmpty.class), types(words.constraints()));
		assertEquals(Set.of(), types(words.elementConstraints()));
	}

	@Test
	public void testValidStillCascadesToArrayElements() {
		final var addresses = properties(Containers.class).get("addresses");
		assertTrue(addresses.cascade() || addresses.cascadeElements());
	}

	@Test
	public void testOptionalSuppliesElementConstraints() {
		final var maybe = properties(Containers.class).get("maybe");
		assertEquals(Set.of(NotBlank.class), types(maybe.elementConstraints()));
	}

	@Test
	public void testValidOnATypeArgumentCascadesToElements() {
		final var nested = properties(Containers.class).get("nested");
		assertFalse(nested.cascade());
		assertTrue(nested.cascadeElements());
		assertFalse(nested.cascadeKeys());
	}

	@Test
	public void testValidOnBothMapTypeArguments() {
		final var both = properties(Containers.class).get("both");
		assertTrue(both.cascadeKeys());
		assertTrue(both.cascadeElements());
	}

	@Test
	public void testAPropertyConstrainedOnlyByItsMapKeyIsStillModeled() {
		final var keyOnly = properties(Containers.class).get("keyConstrainedOnly");
		assertNotNull(keyOnly, () -> "a key constraint alone is reason enough to read the property");
		assertEquals(Set.of(), types(keyOnly.constraints()));
		assertEquals(Set.of(), types(keyOnly.elementConstraints()));
		assertEquals(Set.of(NotBlank.class), types(keyOnly.keyConstraints()));
	}

	@Test
	public void testAPropertyCascadingOnlyThroughItsMapKeyIsStillModeled() {
		final var keyOnly = properties(Containers.class).get("keyCascadeOnly");
		assertNotNull(keyOnly, () -> "a key cascade alone is reason enough to read the property");
		assertFalse(keyOnly.cascade());
		assertFalse(keyOnly.cascadeElements());
		assertTrue(keyOnly.cascadeKeys());
	}

	@Test
	public void testNonGenericPropertyHasNoContainerConstraints() {
		final var name = properties(Base.class).get("name");
		assertEquals(Set.of(), types(name.elementConstraints()));
		assertEquals(Set.of(), types(name.keyConstraints()));
		assertFalse(name.cascade());
	}

	@Test
	public void testValueIsReadThroughTheAccessor() {
		final var name = properties(Base.class).get("name");
		assertEquals("Jane", name.value(Beans.of(Base.class, Map.of("getName", "Jane"))));
	}

}
