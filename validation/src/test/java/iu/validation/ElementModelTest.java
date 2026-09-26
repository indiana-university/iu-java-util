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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@SuppressWarnings("javadoc")
public class ElementModelTest {

	public static class Target {

		@NotNull
		@Pattern(regexp = "\\p{Digit}{10}")
		private String emplid;

		public String unconstrained;

		public Target() {
		}

		@NotBlank
		public String getEmplid() {
			return emplid;
		}

		@AssertTrue
		public boolean isActive() {
			return true;
		}

		@AssertTrue
		public Boolean isEnabled() {
			return Boolean.TRUE;
		}

		@NotNull
		public String isThing() {
			return "";
		}

		@NotNull
		public String get() {
			return "";
		}

		@NotNull
		public String is() {
			return "";
		}

		@NotNull
		public String compute() {
			return "";
		}

		@NotNull
		public String getX(String ignored) {
			return "";
		}

		public void accept(@NotNull String value) {
			// a parameter is the motivating standalone target
		}
	}

	private static ElementModel field(String name) throws Exception {
		return ElementModel.of(Target.class.getDeclaredField(name));
	}

	private static ElementModel method(String name, Class<?>... parameterTypes) throws Exception {
		return ElementModel.of(Target.class.getDeclaredMethod(name, parameterTypes));
	}

	private static Set<Class<? extends Annotation>> types(List<ConstraintDeclaration> declarations) {
		final Set<Class<? extends Annotation>> annotationTypes = new LinkedHashSet<>();
		for (final var declaration : declarations)
			annotationTypes.add(declaration.annotation().annotationType());
		return annotationTypes;
	}

	@Test
	public void testFieldNameAndConstraints() throws Exception {
		final var model = field("emplid");
		assertEquals("emplid", model.name());
		assertSame(Target.class, model.declaringType());
		assertEquals(Set.of(NotNull.class, Pattern.class), types(model.constraints().constraints()));
	}

	@Test
	public void testATargetContributesOnlyItsOwnConstraints() throws Exception {
		// the field declares @NotNull and @Pattern, the getter declares @NotBlank;
		// unlike a bean walk, validating one target does not union the other
		assertEquals(Set.of(NotNull.class, Pattern.class), types(field("emplid").constraints().constraints()));
		assertEquals(Set.of(NotBlank.class), types(method("getEmplid").constraints().constraints()));
	}

	@Test
	public void testAGetterReportsTheBeanPropertyItReads() throws Exception {
		assertEquals("emplid", method("getEmplid").name());
		assertEquals("active", method("isActive").name(), () -> "primitive boolean");
		assertEquals("enabled", method("isEnabled").name(), () -> "boxed Boolean");
	}

	@Test
	public void testAMethodThatIsNotAGetterReportsItsOwnName() throws Exception {
		assertEquals("compute", method("compute").name(), () -> "no accessor prefix");
		assertEquals("isThing", method("isThing").name(), () -> "is-prefixed but does not return a boolean");
		assertEquals("get", method("get").name(), () -> "the prefix alone is not a property name");
		assertEquals("is", method("is").name(), () -> "the prefix alone is not a property name");
		assertEquals("getX", method("getX", String.class).name(), () -> "a getter takes no arguments");
	}

	@Test
	public void testParameterNameAndDeclaringType() throws Exception {
		final var parameter = Target.class.getDeclaredMethod("accept", String.class).getParameters()[0];
		final var model = ElementModel.of(parameter);

		// asserted against reflection rather than a literal, because the name is arg0
		// unless the declaring code was compiled with -parameters
		assertEquals(parameter.getName(), model.name());
		assertSame(Target.class, model.declaringType(), () -> "resolved through the declaring executable");
		assertEquals(Set.of(NotNull.class), types(model.constraints().constraints()));
	}

	@Test
	public void testAnUnconstrainedTargetIsDescribedRatherThanRefused() throws Exception {
		final var model = field("unconstrained");
		assertEquals("unconstrained", model.name());
		assertTrue(model.constraints().constraints().isEmpty());
		assertTrue(model.constraints().elementConstraints().isEmpty());
		assertTrue(model.constraints().keyConstraints().isEmpty());
	}

	@Test
	public void testModelsAreCached() throws Exception {
		final var element = Target.class.getDeclaredField("emplid");
		assertSame(ElementModel.of(element), ElementModel.of(element));
	}

	@Test
	public void testUnsupportedTargetsAreRefused() throws Exception {
		final AnnotatedElement constructor = Target.class.getDeclaredConstructor();
		final AnnotatedElement annotatedType = Target.class.getDeclaredField("emplid").getAnnotatedType();

		for (final var unsupported : List.of(constructor, //
				Target.class.getPackage(), //
				Target.class.getModule(), //
				annotatedType, //
				Target.class)) {
			final var e = assertThrows(IllegalArgumentException.class, () -> ElementModel.of(unsupported),
					() -> "expected " + unsupported + " to be refused");
			assertTrue(e.getMessage().endsWith("is not a supported constraint target;"
					+ " expected a Field, Method, or Parameter, or a Class to validate as a bean"), e::getMessage);
		}
	}

}
