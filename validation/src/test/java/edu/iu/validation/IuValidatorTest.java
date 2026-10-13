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
package edu.iu.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IuBadRequestException;
import iu.validation.Beans;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@SuppressWarnings("javadoc")
public class IuValidatorTest {

	public interface EnrlHeader {
		@NotNull
		@Pattern(regexp = "\\p{Digit}{10}")
		String getEmplid();

		@NotNull
		@Pattern(regexp = "\\p{Alpha}{4}")
		String getAcadCareer();
	}

	public interface Address {
		@NotBlank
		String getStreet();
	}

	public interface Order {
		@Valid
		Address getBillTo();

		@Valid
		Address getShipTo();
	}

	public interface Basket {
		@Valid
		List<Address> getCascadedContainer();

		List<@Valid Address> getCascadedElements();

		List<@NotBlank String> getLabels();

		@Valid
		Address[] getCascadedArray();

		Optional<@NotBlank String> getMaybe();

		@Valid
		Optional<Address> getMaybeAddress();

		Map<@NotBlank String, @Valid Address> getByCode();

		@Valid
		Map<String, Address> getLegacyMap();

		Map<@Valid Address, String> getCascadedKeys();
	}

	public interface PlainArray {
		@NotNull
		Address[] getArray();
	}

	public interface Sized {
		@NotEmpty
		Iterable<String> getNotEmptyIterable();
	}

	public interface Declared {
		@NotNull
		String getX();
	}

	public interface Redeclared extends Declared {
		@Override
		@NotNull
		String getX();
	}

	public interface TwoRules {
		@NotBlank
		@Size(min = 5)
		String getCode();
	}

	public interface Numbered {
		@Min(5)
		Integer getCount();
	}

	public interface BadlyConstrained {
		@Pattern(regexp = "[0-9]+")
		Integer getNumber();
	}

	public static class Node {
		@NotNull
		private String label;

		@Valid
		private Node next;

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public Node getNext() {
			return next;
		}

		public void setNext(Node next) {
			this.next = next;
		}
	}

	/** Standalone annotation targets, modeled on a JAX-RS resource method. */
	public static class Endpoint {

		@NotNull
		@Pattern(regexp = "\\p{Digit}{10}")
		private String emplid;

		public String unconstrained;

		@NotBlank
		public String getLabel() {
			return null;
		}

		public void echo(@Pattern(regexp = "[\\w\\-\\s]{1,100}") String message) {
			// the case EchoResource hand-rolls today
		}

		public void ship(@Valid Address destination) {
		}

		public void tag(List<@NotBlank String> tags) {
		}
	}

	private static Parameter parameterOf(String method, Class<?>... parameterTypes) throws Exception {
		return Endpoint.class.getDeclaredMethod(method, parameterTypes).getParameters()[0];
	}

	private static Set<String> described(IuValidationResult result) {
		final Set<String> descriptions = new java.util.LinkedHashSet<>();
		for (final var violation : result)
			descriptions.add(violation.describe());
		return descriptions;
	}

	private static Set<String> paths(IuValidationResult result) {
		final Set<String> found = new java.util.LinkedHashSet<>();
		for (final var violation : result)
			found.add(violation.path());
		return found;
	}

	private static List<IuValidationNode> nodes(IuConstraintViolation violation) {
		final List<IuValidationNode> path = new ArrayList<>();
		violation.nodes().forEach(path::add);
		return path;
	}

	private static Address address(String street) {
		final Map<String, Object> values = new HashMap<>();
		values.put("getStreet", street);
		return Beans.of(Address.class, values);
	}

	// ---- entry points -------------------------------------------------------

	@Test
	public void testAValidObjectReportsValid() {
		final var header = Beans.of(EnrlHeader.class,
				Map.of("getEmplid", "0123456789", "getAcadCareer", "UGRD"));

		final var result = IuValidator.validate(EnrlHeader.class, header);
		assertTrue(result.isValid());
		assertEquals(0, result.count());
		assertSame(EnrlHeader.class, result.rootType());
		assertEquals("Valid " + EnrlHeader.class.getName(), result.report());
		assertEquals(result.report(), result.toString());
		result.checkValid();
	}

	@Test
	public void testEveryFailureIsReportedInOnePass() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getAcadCareer", "ug"));

		final var result = IuValidator.validate(EnrlHeader.class, header);
		assertFalse(result.isValid());
		assertEquals(2, result.count());
		assertEquals(Set.of("acadCareer @Pattern must match \"\\p{Alpha}{4}\"", "emplid @NotNull must not be null"),
				described(result));
	}

	@Test
	public void testReportNamesEveryFailedPropertyAndItsRule() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getAcadCareer", "ug"));

		assertEquals("Invalid " + EnrlHeader.class.getName() + ": 2 constraint violations" //
				+ "\n  acadCareer  @Pattern  must match \"\\p{Alpha}{4}\"" //
				+ "\n  emplid      @NotNull  must not be null", //
				IuValidator.validate(EnrlHeader.class, header).report());
	}

	@Test
	public void testReportIsSingularForOneViolation() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getEmplid", "0123456789", "getAcadCareer", "ug"));
		final var report = IuValidator.validate(EnrlHeader.class, header).report();
		assertTrue(report.contains(": 1 constraint violation\n"), () -> report);
	}

	@Test
	public void testInvalidValuesAreRedactedUnlessRequested() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getEmplid", "0123456789", "getAcadCareer", "ug"));
		final var result = IuValidator.validate(EnrlHeader.class, header);

		assertFalse(result.report().contains("ug"), result::report);
		assertTrue(result.report(true).endsWith("(was \"ug\")"), () -> result.report(true));
	}

	@Test
	public void testANonTextInvalidValueRendersUnquoted() {
		final var result = IuValidator.validate(Numbered.class, Beans.of(Numbered.class, Map.of("getCount", 4)));
		assertTrue(result.report(true).endsWith("(was 4)"), () -> result.report(true));
	}

	@Test
	public void testANullInvalidValueRenders() {
		final var result = IuValidator.validate(EnrlHeader.class, Beans.of(EnrlHeader.class));
		assertTrue(result.report(true).contains("(was null)"), () -> result.report(true));
	}

	@Test
	public void testRequireThrowsWithTheReportAsItsMessage() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getAcadCareer", "ug"));

		final var e = assertThrows(IuValidationException.class, () -> IuValidator.require(EnrlHeader.class, header));
		assertEquals(IuValidator.validate(EnrlHeader.class, header).report(), e.getMessage());
		assertEquals(2, e.getResult().count());
	}

	@Test
	public void testRequirePassesAValidObject() {
		IuValidator.require(EnrlHeader.class,
				Beans.of(EnrlHeader.class, Map.of("getEmplid", "0123456789", "getAcadCareer", "UGRD")));
	}

	@Test
	public void testRuntimeClassEntryPoints() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getAcadCareer", "ug"));

		assertEquals(2, IuValidator.validate((Object) header).count());
		assertThrows(IuValidationException.class, () -> IuValidator.require((Object) header));
	}

	@Test
	public void testNullIsValidAndReportsTheRequestedRootType() {
		final var typed = IuValidator.validate(EnrlHeader.class, null);
		assertTrue(typed.isValid());
		assertSame(EnrlHeader.class, typed.rootType());

		final var untyped = IuValidator.validate((Object) null);
		assertTrue(untyped.isValid());
		assertSame(Object.class, untyped.rootType());

		IuValidator.require(EnrlHeader.class, null);
		IuValidator.require((Object) null);
	}

	@Test
	public void testRootTypeIsRequired() {
		assertThrows(NullPointerException.class, () -> IuValidator.validate(null, "x"));
	}

	// ---- cascading ----------------------------------------------------------

	@Test
	public void testCascadeIntoANestedBean() {
		final var order = Beans.of(Order.class, Map.of("getBillTo", address(""), "getShipTo", address("1 Main St")));

		assertEquals(Set.of("billTo.street"), paths(IuValidator.validate(Order.class, order)));
	}

	@Test
	public void testASharedInstanceIsReportedUnderTheFirstPathReached() {
		final var shared = address("");
		final var order = Beans.of(Order.class, Map.of("getBillTo", shared, "getShipTo", shared));

		// properties are walked in name order, so billTo is reached first
		assertEquals(Set.of("billTo.street"), paths(IuValidator.validate(Order.class, order)));
	}

	@Test
	public void testACycleTerminates() {
		final var a = new Node();
		final var b = new Node();
		a.setNext(b);
		b.setNext(a);

		assertEquals(Set.of("label", "next.label"), paths(IuValidator.validate(Node.class, a)));
	}

	@Test
	public void testCascadeSkipsANullValue() {
		assertTrue(IuValidator.validate(Order.class, Beans.of(Order.class)).isValid());
	}

	// ---- containers ---------------------------------------------------------

	@Test
	public void testValidOnAContainerCascadesToItsElements() {
		final var basket = Beans.of(Basket.class,
				Map.of("getCascadedContainer", List.of(address("ok"), address(""))));

		assertEquals(Set.of("cascadedContainer[1].street"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testANullElementIsSkippedRatherThanCascadedInto() {
		final List<Address> withNull = new ArrayList<>();
		withNull.add(null);
		withNull.add(address(""));
		final var basket = Beans.of(Basket.class, Map.of("getCascadedContainer", withNull));

		assertEquals(Set.of("cascadedContainer[1].street"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testValidOnAnElementTypeArgumentCascades() {
		final var basket = Beans.of(Basket.class, Map.of("getCascadedElements", List.of(address(""))));

		assertEquals(Set.of("cascadedElements[0].street"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testElementConstraintsApplyPerElement() {
		final var basket = Beans.of(Basket.class,
				Map.of("getLabels", new ArrayList<>(List.of("ok", "", "  "))));

		assertEquals(Set.of("labels[1]", "labels[2]"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testAnArrayIsWalkedByIndex() {
		final var basket = Beans.of(Basket.class,
				Map.of("getCascadedArray", new Address[] { address("ok"), address("") }));

		assertEquals(Set.of("cascadedArray[1].street"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testAnArrayWithoutCascadeIsNotDescendedIntoItsElements() {
		// getArray is constrained (@NotNull) so it is read, but carries no cascade, so
		// the invalid Address inside it is never reached
		final var bean = Beans.of(PlainArray.class, Map.of("getArray", new Address[] { address("") }));

		assertTrue(IuValidator.validate(PlainArray.class, bean).isValid());
	}

	@Test
	public void testAnIterableThatIsNotACollectionIsSized() {
		final Iterable<String> empty = () -> List.<String>of().iterator();
		assertEquals(Set.of("notEmptyIterable"),
				paths(IuValidator.validate(Sized.class, Beans.of(Sized.class, Map.of("getNotEmptyIterable", empty)))));

		final Iterable<String> populated = () -> List.of("a").iterator();
		assertTrue(IuValidator
				.validate(Sized.class, Beans.of(Sized.class, Map.of("getNotEmptyIterable", populated))).isValid());
	}

	@Test
	public void testAnOptionalIsTransparentToThePath() {
		final var present = Beans.of(Basket.class, Map.of("getMaybe", Optional.of("")));
		assertEquals(Set.of("maybe"), paths(IuValidator.validate(Basket.class, present)));

		final var absent = Beans.of(Basket.class, Map.of("getMaybe", Optional.empty()));
		assertTrue(IuValidator.validate(Basket.class, absent).isValid());
	}

	@Test
	public void testAnOptionalCascadesToItsContents() {
		final var basket = Beans.of(Basket.class, Map.of("getMaybeAddress", Optional.of(address(""))));
		assertEquals(Set.of("maybeAddress.street"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testMapKeysAndValuesAreDistinguished() {
		final Map<String, Address> byCode = new LinkedHashMap<>();
		byCode.put("", address("ok"));
		byCode.put("GOOD", address(""));
		final var basket = Beans.of(Basket.class, Map.of("getByCode", byCode));

		assertEquals(Set.of("byCode[]<key>", "byCode[GOOD].street"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testValidOnAMapCascadesToItsValuesOnly() {
		final Map<String, Address> legacy = new LinkedHashMap<>();
		legacy.put("k", address(""));
		final var basket = Beans.of(Basket.class, Map.of("getLegacyMap", legacy));

		assertEquals(Set.of("legacyMap[k].street"), paths(IuValidator.validate(Basket.class, basket)));
	}

	@Test
	public void testValidOnAMapKeyTypeArgumentCascadesToTheKeys() {
		final Map<Address, String> byAddress = new LinkedHashMap<>();
		byAddress.put(address(""), "x");
		final var basket = Beans.of(Basket.class, Map.of("getCascadedKeys", byAddress));

		final var result = IuValidator.validate(Basket.class, basket);
		assertEquals(1, result.count());

		final var path = result.iterator().next().path();
		assertTrue(path.startsWith("cascadedKeys[") && path.endsWith("]<key>.street"), () -> path);
	}

	@Test
	public void testNullKeysAndValuesInAMapAreTolerated() {
		final Map<String, Address> byCode = new LinkedHashMap<>();
		byCode.put(null, null);
		final var basket = Beans.of(Basket.class, Map.of("getByCode", byCode));

		assertEquals(Set.of("byCode[null]<key>"), paths(IuValidator.validate(Basket.class, basket)));
	}

	// ---- result assembly ----------------------------------------------------

	@Test
	public void testTheSameRuleDeclaredTwiceIsReportedOnce() {
		assertEquals(1, IuValidator.validate(Redeclared.class, Beans.of(Redeclared.class)).count());
	}

	@Test
	public void testTwoRulesFailingAtOnePathAreBothReported() {
		final var result = IuValidator.validate(TwoRules.class, Beans.of(TwoRules.class, Map.of("getCode", "")));

		assertEquals(2, result.count());
		assertEquals(Set.of("code"), paths(result));
		assertEquals(Set.of("code @NotBlank must not be blank", "code @Size size must be between 5 and 2147483647"),
				described(result));
	}

	@Test
	public void testViolationsAreUnmodifiable() {
		final var result = IuValidator.validate(EnrlHeader.class, Beans.of(EnrlHeader.class));
		final var iterator = result.iterator();
		assertTrue(iterator.hasNext());
		iterator.next();
		assertThrows(UnsupportedOperationException.class, iterator::remove);
	}

	// ---- violation detail ---------------------------------------------------

	@Test
	public void testViolationDetail() {
		final var result = IuValidator.validate(EnrlHeader.class,
				Beans.of(EnrlHeader.class, Map.of("getEmplid", "0123456789", "getAcadCareer", "ug")));

		final var violation = result.iterator().next();
		assertEquals("acadCareer", violation.path());
		assertSame(Pattern.class, violation.constraint());
		assertEquals("\\p{Alpha}{4}", violation.attributes().get("regexp"));
		assertEquals("{jakarta.validation.constraints.Pattern.message}", violation.messageTemplate());
		assertEquals("must match \"\\p{Alpha}{4}\"", violation.message());
		assertEquals("ug", violation.invalidValue());
		assertSame(EnrlHeader.class, violation.rootType());
		assertSame(EnrlHeader.class, violation.declaringType());
		assertEquals(violation.describe(), violation.toString());

		final var nodes = nodes(violation);
		assertEquals(2, nodes.size());
		assertEquals(IuValidationNodeKind.BEAN, nodes.get(0).kind());
		assertEquals(IuValidationNodeKind.PROPERTY, nodes.get(1).kind());
		assertEquals("acadCareer", nodes.get(1).name());
	}

	@Test
	public void testAttributesAreUnmodifiable() {
		final var violation = IuValidator.validate(EnrlHeader.class, Beans.of(EnrlHeader.class)).iterator().next();
		assertThrows(UnsupportedOperationException.class, () -> violation.attributes().clear());
	}

	@Test
	public void testDeclaringTypeNamesWhereTheRuleCameFrom() {
		final var violation = IuValidator.validate(Order.class,
				Beans.of(Order.class, Map.of("getBillTo", address("")))).iterator().next();

		assertEquals("billTo.street", violation.path());
		assertSame(Address.class, violation.declaringType());
		assertSame(Order.class, violation.rootType());
	}

	// ---- annotation targets -------------------------------------------------

	@Test
	public void testAFieldReportsEveryRuleItDeclares() throws Exception {
		final var field = Endpoint.class.getDeclaredField("emplid");
		final var result = IuValidator.validate(field, "nope");

		assertSame(Endpoint.class, result.rootType());
		assertEquals(Set.of("emplid @Pattern must match \"\\p{Digit}{10}\""), described(result));
	}

	@Test
	public void testAGetterReportsAtItsBeanPropertyName() throws Exception {
		final var getter = Endpoint.class.getDeclaredMethod("getLabel");
		assertEquals(Set.of("label"), paths(IuValidator.validate(getter, "  ")));
	}

	@Test
	public void testAParameterIsValidatedAgainstItsOwnConstraints() throws Exception {
		final var parameter = parameterOf("echo", String.class);

		final var invalid = IuValidator.validate(parameter, "not a valid message!");
		assertEquals(1, invalid.count());
		assertSame(Pattern.class, invalid.iterator().next().constraint());

		assertTrue(IuValidator.validate(parameter, "a valid message").isValid());
	}

	@Test
	public void testAnExplicitNameRootsThePath() throws Exception {
		final var parameter = parameterOf("echo", String.class);

		// the useful name lives in the JAX-RS annotation, not in the bytecode
		assertEquals(Set.of("message"), paths(IuValidator.validate(parameter, "message", "bad!")));
	}

	@Test
	public void testAParameterCascades() throws Exception {
		final var parameter = parameterOf("ship", Address.class);

		assertEquals(Set.of("destination.street"),
				paths(IuValidator.validate(parameter, "destination", address(""))));
	}

	@Test
	public void testAParameterAppliesContainerElementConstraints() throws Exception {
		final var parameter = parameterOf("tag", List.class);
		final var tags = new ArrayList<>(List.of("ok", "", "  "));

		assertEquals(Set.of("tags[1]", "tags[2]"), paths(IuValidator.validate(parameter, "tags", tags)));
	}

	@Test
	public void testAnUnconstrainedTargetIsValid() throws Exception {
		final var field = Endpoint.class.getDeclaredField("unconstrained");
		assertTrue(IuValidator.validate(field, "anything").isValid());
		assertTrue(IuValidator.validate(field, "name", "anything").isValid());
	}

	@Test
	public void testANullValueIsValidForATarget() throws Exception {
		final var parameter = parameterOf("echo", String.class);
		assertTrue(IuValidator.validate(parameter, null).isValid(), () -> "@Pattern admits null");
	}

	@Test
	public void testAClassTargetResolvesToTheBeanWalk() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getAcadCareer", "ug"));
		final AnnotatedElement element = EnrlHeader.class;

		assertEquals(IuValidator.validate(EnrlHeader.class, header).report(),
				IuValidator.validate(element, header).report());
	}

	@Test
	public void testANamedClassTargetRootsEveryPath() {
		final var header = Beans.of(EnrlHeader.class, Map.of("getAcadCareer", "ug"));
		final AnnotatedElement element = EnrlHeader.class;

		assertEquals(Set.of("body.acadCareer", "body.emplid"),
				paths(IuValidator.validate(element, "body", header)));
	}

	@Test
	public void testRequireForATarget() throws Exception {
		final var parameter = parameterOf("echo", String.class);

		final var e = assertThrows(IuValidationException.class, () -> IuValidator.require(parameter, "bad!"));
		assertTrue(e.getMessage().contains("@Pattern"), e::getMessage);

		assertThrows(IuValidationException.class, () -> IuValidator.require(parameter, "message", "bad!"));
		IuValidator.require(parameter, "fine");
		IuValidator.require(parameter, "message", "fine");
	}

	@Test
	public void testATargetIsRequired() throws Exception {
		assertThrows(NullPointerException.class, () -> IuValidator.validate((AnnotatedElement) null, "x"));
		assertThrows(NullPointerException.class, () -> IuValidator.validate((AnnotatedElement) null, "n", "x"));
		assertThrows(NullPointerException.class,
				() -> IuValidator.validate(Endpoint.class.getDeclaredField("emplid"), null, "x"));
	}

	@Test
	public void testAnUnsupportedTargetIsRefused() throws Exception {
		final AnnotatedElement constructor = Endpoint.class.getDeclaredConstructor();
		assertThrows(IllegalArgumentException.class, () -> IuValidator.validate(constructor, "x"));
		assertThrows(IllegalArgumentException.class, () -> IuValidator.validate(constructor, "n", "x"));
	}

	// ---- misuse -------------------------------------------------------------

	@Test
	public void testAConstraintOnAnIncompatibleTypeNamesThePath() {
		final var bean = Beans.of(BadlyConstrained.class, Map.of("getNumber", 42));

		final var e = assertThrows(UnsupportedOperationException.class,
				() -> IuValidator.validate(BadlyConstrained.class, bean));
		assertEquals("@Pattern cannot be applied to " + BadlyConstrained.class.getName()
				+ ".number: java.lang.Integer is not text", e.getMessage());
	}

	@Test
	public void testValidationExceptionRefusesAValidResult() {
		final var valid = IuValidator.validate(EnrlHeader.class,
				Beans.of(EnrlHeader.class, Map.of("getEmplid", "0123456789", "getAcadCareer", "UGRD")));

		final var e = assertThrows(IllegalArgumentException.class, () -> new IuValidationException(valid));
		assertEquals("expected at least one violation", e.getMessage());
	}

	@Test
	public void testValidationExceptionIsABadRequest() {
		final var result = IuValidator.validate(EnrlHeader.class, Beans.of(EnrlHeader.class));
		final IuBadRequestException e = new IuValidationException(result);

		// a web boundary already configured for IuBadRequestException answers 400
		// without needing to know this module exists
		assertEquals(result.report(), e.getMessage());
		assertSame(result, ((IuValidationException) e).getResult());
	}

}
