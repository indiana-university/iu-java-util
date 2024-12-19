/*
 * Copyright © 2024 Indiana University
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
package iu.logging.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import iu.logging.IuLoggingTestCase;
import iu.logging.LogContext;

@SuppressWarnings("javadoc")
public class ProcessLoggerTest extends IuLoggingTestCase {

	@Test
	public void testSizeToString() {
		assertEquals("0B", ProcessLogger.sizeToString(0L));
		assertEquals("-1B", ProcessLogger.sizeToString(-1L));
		assertEquals("1KiB", ProcessLogger.sizeToString(1024L));
		assertEquals("1024TiB", ProcessLogger.sizeToString(1125899906842624L));
		assertEquals("4.767MiB", ProcessLogger.sizeToString(5000000L));
	}

	@Test
	public void testIntervalToString() {
		assertEquals("00:00.000", ProcessLogger.intervalToString(Duration.ZERO));
		assertEquals("02:00:00.000", ProcessLogger.intervalToString(Duration.ofHours(2L)));
		assertEquals("3 days, 02:00:00.000",
				ProcessLogger.intervalToString(Duration.ofDays(3L).plus(Duration.ofHours(2L))));
	}

	@Test
	public void testMemoryToString() {
		assertEquals("1B/2B/3B - 50% free", ProcessLogger.memoryToString(1L, 2L, 3L));
	}

	@Test
	public void testNotFollowing() {
		ProcessLogger.trace(IdGenerator::generateId); // no-op
		assertNull(ProcessLogger.getActiveContext());
		assertNull(ProcessLogger.export());
	}

	private static final String NUM_REGEX = "-?[\\d\\.]+";
	private static final String PCT_REGEX = NUM_REGEX + "%";
	private static final String SIZE_REGEX = "(?:0B|" + NUM_REGEX + "[KMG]iB)";
	private static final String MEM_REGEX = SIZE_REGEX + "/" + SIZE_REGEX + "/" + SIZE_REGEX + " - " + PCT_REGEX
			+ " free";
	private static final String INT_REGEX = "\\d{2}:\\d{2}.\\d{3}";
	private static final String TIME_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3,9}Z";

	private static final String msgRegex(String message) {
		StringBuilder sb = new StringBuilder(message);
		if (sb.length() >= 80)
			sb.setLength(80);
		final var l = sb.length();

		for (int i = 0; i < sb.length(); i++)
			if (sb.charAt(i) == '(' || sb.charAt(i) == ')')
				sb.insert(i++, '\\');
			else if (sb.charAt(i) < ' ')
				sb.setCharAt(i, ' ');
		if (l < 80)
			sb.append("\\.{").append(80 - l).append('}');

		return sb + " " + INT_REGEX + " " + INT_REGEX + " " + SIZE_REGEX + " " + SIZE_REGEX;
	}

	@Test
	public void testFollowAndExport() {
		final var header = IdGenerator.generateId();
		final var header2 = IdGenerator.generateId();
		final var header3 = IdGenerator.generateId();
		final var context = mock(LogContext.class);
		final var application = IdGenerator.generateId();
		final var context2 = mock(LogContext.class);
		when(context2.getApplication()).thenReturn(application);
		final var message = IdGenerator.generateId();
		final var message2 = IdGenerator.generateId();
		final var message3 = IdGenerator.generateId();
		final var message4 = IdGenerator.generateId() + "\n" + IdGenerator.generateId() + "\n"
				+ IdGenerator.generateId();
		final var message5 = IdGenerator.generateId();
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1.1: " + header2);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "end 1.1: " + header2);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1.2: " + header3);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "end 1.2: " + header3);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "complete 1: " + header + System.lineSeparator() //
				+ "init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
				+ msgRegex("begin 1: " + header) + System.lineSeparator() //
				+ msgRegex(message) + System.lineSeparator() //
				+ msgRegex(">1.1: " + header2) + System.lineSeparator() //
				+ msgRegex(" " + message2) + System.lineSeparator() //
				+ msgRegex("<1.1: " + header2) + System.lineSeparator() //
				+ msgRegex(message3) + System.lineSeparator() //
				+ msgRegex(">1.2 " + application + ": " + header3) + System.lineSeparator() //
				+ msgRegex(" " + message4) + System.lineSeparator() //
				+ msgRegex(" " + message5) + System.lineSeparator() //
				+ msgRegex("<1.2 " + application + ": " + header3) + System.lineSeparator() //
				+ msgRegex("end 1: " + header) + System.lineSeparator() //
				+ "final: " + TIME_REGEX + " " + SIZE_REGEX + " " + MEM_REGEX + System.lineSeparator() //
		);
		assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
			ProcessLogger.trace(() -> null);
			ProcessLogger.trace(() -> message);
			assertSame(context, ProcessLogger.getActiveContext());
			ProcessLogger.follow(context, header2, () -> {
				ProcessLogger.trace(() -> message2);
				assertSame(context, ProcessLogger.getActiveContext());
				return null;
			});
			ProcessLogger.trace(() -> message3);
			ProcessLogger.follow(context2, header3, () -> {
				assertSame(context2, ProcessLogger.getActiveContext());
				ProcessLogger.trace(() -> message4);

				final var exp = ProcessLogger.export();
				assertTrue(exp.matches("init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
						+ msgRegex(">1.2 " + application + ": " + header3) + System.lineSeparator() //
						+ msgRegex(" " + message4) + System.lineSeparator() //
				), exp::toString);

				ProcessLogger.trace(() -> message5);
				return null;
			});
			return null;
		}));
	}

}
