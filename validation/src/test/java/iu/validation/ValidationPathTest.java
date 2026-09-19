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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.validation.IuValidationNode;
import edu.iu.validation.IuValidationNodeKind;

@SuppressWarnings("javadoc")
public class ValidationPathTest {

	@Test
	public void testRootRendersEmpty() {
		assertEquals("", ValidationPath.ROOT.toString());
	}

	@Test
	public void testPropertyOmitsTheLeadingSeparator() {
		assertEquals("emplid", ValidationPath.ROOT.property("emplid").toString());
	}

	@Test
	public void testNestedPropertiesAreDotted() {
		assertEquals("header.detail.strm",
				ValidationPath.ROOT.property("header").property("detail").property("strm").toString());
	}

	@Test
	public void testElementIsIndexed() {
		assertEquals("details[2]", ValidationPath.ROOT.property("details").element(2).toString());
		assertEquals("details[2].strm", ValidationPath.ROOT.property("details").element(2).property("strm").toString());
	}

	@Test
	public void testMapValueIsKeyed() {
		assertEquals("byCode[ABCD]", ValidationPath.ROOT.property("byCode").mapValue("ABCD").toString());
	}

	@Test
	public void testMapKeyIsMarkedApartFromItsValue() {
		assertEquals("byCode[ABCD]<key>", ValidationPath.ROOT.property("byCode").mapKey("ABCD").toString());
		assertEquals("byCode[null]<key>", ValidationPath.ROOT.property("byCode").mapKey(null).toString());
	}

	@Test
	public void testNodesAreOrderedFromTheRoot() {
		final var path = ValidationPath.ROOT.property("details").element(2).property("codes").mapKey("X");
		final var nodes = List.copyOf(path.nodes());

		assertEquals(5, nodes.size());

		assertEquals(IuValidationNodeKind.BEAN, nodes.get(0).kind());
		assertNull(nodes.get(0).name());

		assertEquals(IuValidationNodeKind.PROPERTY, nodes.get(1).kind());
		assertEquals("details", nodes.get(1).name());
		assertNull(nodes.get(1).index());

		assertEquals(IuValidationNodeKind.ITERABLE_ELEMENT, nodes.get(2).kind());
		assertEquals(2, nodes.get(2).index());

		assertEquals(IuValidationNodeKind.PROPERTY, nodes.get(3).kind());
		assertEquals("codes", nodes.get(3).name());

		assertEquals(IuValidationNodeKind.MAP_KEY, nodes.get(4).kind());
		assertEquals("X", nodes.get(4).key());
	}

	@Test
	public void testMapValueNodeCarriesItsKey() {
		final var nodes = List.copyOf(ValidationPath.ROOT.property("byCode").mapValue("K").nodes());
		final var node = nodes.get(nodes.size() - 1);
		assertEquals(IuValidationNodeKind.MAP_VALUE, node.kind());
		assertEquals("K", node.key());
		assertNull(node.index());
		assertNull(node.name());
	}

	@Test
	public void testNodesAreUnmodifiable() {
		final List<IuValidationNode> nodes = ValidationPath.ROOT.property("a").nodes();
		assertThrows(UnsupportedOperationException.class, () -> nodes.clear());
	}

}
