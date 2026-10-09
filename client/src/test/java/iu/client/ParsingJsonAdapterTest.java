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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class ParsingJsonAdapterTest {

	public static final class Parsed {
		private final String value;

		private Parsed(String value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return "parsed:" + value;
		}
	}

	public static final class Printed {
		private final String value;

		private Printed(String value) {
			this.value = value;
		}
	}

	@Test
	public void testOneInstancePerType() {
		final var adapter = ParsingJsonAdapter.of(Parsed.class, Parsed::new);
		assertSame(adapter, ParsingJsonAdapter.of(Parsed.class, Parsed::new));

		// the first parser supplied wins, as it did with computeIfAbsent
		final Function<String, Parsed> other = v -> new Parsed("other");
		assertSame(adapter, ParsingJsonAdapter.of(Parsed.class, other));
		assertEquals("a", adapter.fromJson(IuJson.string("a")).value);
		assertEquals(IuJson.string("parsed:b"), adapter.toJson(new Parsed("b")));
	}

	@Test
	public void testPrintedIsSeparateFromToString() {
		final var printed = ParsingJsonAdapter.of(Printed.class, Printed::new, p -> "printed:" + p.value);
		assertSame(printed, ParsingJsonAdapter.of(Printed.class, Printed::new, p -> "ignored"));
		assertEquals(IuJson.string("printed:c"), printed.toJson(new Printed("c")));

		// a type may have both kinds, each its own instance
		assertNotSame(printed, ParsingJsonAdapter.of(Printed.class, Printed::new));
	}

	@Test
	public void testSingletonsCreateOnlyWhileAbsent() {
		final var singletons = new ParsingJsonAdapter.Singletons();
		final var created = new AtomicInteger();
		final var first = singletons.get(Parsed.class, () -> {
			created.incrementAndGet();
			return new ParsingJsonAdapter<>(Parsed::new, Parsed::toString);
		});
		assertSame(first, singletons.get(Parsed.class, () -> {
			created.incrementAndGet();
			return new ParsingJsonAdapter<>(Parsed::new, Parsed::toString);
		}));
		assertEquals(1, created.get());

		// held per class
		assertNotSame(first, singletons.get(Printed.class,
				() -> new ParsingJsonAdapter<>(Printed::new, p -> p.value)));
	}

}
