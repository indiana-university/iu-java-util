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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.validation.groups.Default;

@SuppressWarnings("javadoc")
public class MessagesTest {

	private static String interpolate(String template, Object... attributePairs) {
		final Map<String, Object> attributes = new HashMap<>();
		attributes.put("message", template);
		for (var i = 0; i < attributePairs.length; i += 2)
			attributes.put((String) attributePairs[i], attributePairs[i + 1]);
		return Messages.interpolate(attributes);
	}

	@Test
	public void testTemplateWithNoTokenIsReturnedVerbatim() {
		assertEquals("plain text", interpolate("plain text"));
		assertEquals("", interpolate(""));
	}

	@Test
	public void testAttributeTokenIsSubstituted() {
		assertEquals("at least 5", interpolate("at least {value}", "value", 5));
		assertEquals("between 2 and 4", interpolate("between {min} and {max}", "min", 2, "max", 4));
	}

	@Test
	public void testCatalogKeyResolvesToItsDefaultTemplate() {
		assertEquals("must not be null", interpolate("{jakarta.validation.constraints.NotNull.message}"));
		assertEquals("must not be blank", interpolate("{jakarta.validation.constraints.NotBlank.message}"));
	}

	@Test
	public void testCatalogTemplateIsThenSubstitutedFromAttributes() {
		assertEquals("size must be between 2 and 4",
				interpolate("{jakarta.validation.constraints.Size.message}", "min", 2, "max", 4));
		assertEquals("must match \"[a-z]+\"",
				interpolate("{jakarta.validation.constraints.Pattern.message}", "regexp", "[a-z]+"));
	}

	@Test
	public void testACatalogTemplateCannotItselfResolveACatalogKey() {
		// with min and max absent, the nested expansion has no attribute to substitute
		// and must not fall back to resolving them as catalog keys
		assertEquals("size must be between {min} and {max}",
				interpolate("{jakarta.validation.constraints.Size.message}"));
	}

	@Test
	public void testAnUnresolvableTokenIsLeftInPlace() {
		assertEquals("keep {nope} intact", interpolate("keep {nope} intact"));
		assertEquals("keep {some.other.key} intact", interpolate("keep {some.other.key} intact"));
	}

	@Test
	public void testAnUnterminatedTokenIsLeftInPlace() {
		assertEquals("trailing {open", interpolate("trailing {open"));
		assertEquals("5 then {open", interpolate("{value} then {open", "value", 5));
	}

	@Test
	public void testExclusiveBoundsResolveTheExclusiveTemplate() {
		assertEquals("must be greater than 5.5", interpolate("{jakarta.validation.constraints.DecimalMin.message}",
				"value", "5.5", "inclusive", false));
		assertEquals("must be less than 5.5", interpolate("{jakarta.validation.constraints.DecimalMax.message}",
				"value", "5.5", "inclusive", false));
	}

	@Test
	public void testInclusiveBoundsResolveTheOrdinaryTemplate() {
		assertEquals("must be greater than or equal to 5.5",
				interpolate("{jakarta.validation.constraints.DecimalMin.message}", "value", "5.5", "inclusive", true));
	}

	@Test
	public void testAnExclusiveConstraintWithNoExclusiveTemplateFallsBack() {
		// Min declares no inclusive() attribute, so this pairing cannot arise from a
		// real annotation; it isolates the fallback when the variant key is absent
		assertEquals("must be greater than or equal to 5",
				interpolate("{jakarta.validation.constraints.Min.message}", "value", 5, "inclusive", false));
	}

	@Test
	public void testArrayAttributesRender() {
		assertEquals("groups [interface jakarta.validation.groups.Default]",
				interpolate("groups {groups}", "groups", new Class<?>[] { Default.class }));
	}

	@Test
	public void testNullAttributeRenders() {
		final Map<String, Object> attributes = new HashMap<>();
		attributes.put("message", "value {value}");
		attributes.put("value", null);
		assertEquals("value null", Messages.interpolate(attributes));
	}

}
