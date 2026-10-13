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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import edu.iu.validation.IuConstraintViolation;
import edu.iu.validation.IuValidationException;
import edu.iu.validation.IuValidationResult;

/**
 * Every violation from one validation pass, sorted, deduplicated, and
 * renderable as a log record.
 */
final class ValidationResultImpl implements IuValidationResult {

	/** Path first, then constraint, so that a report is stable across passes. */
	private static final Comparator<Violation> ORDER = Comparator //
			.comparing((Violation violation) -> violation.path()) //
			.thenComparing(violation -> violation.constraint().getName());

	private final Class<?> rootType;
	private final List<IuConstraintViolation> violations;

	/**
	 * Constructor.
	 *
	 * @param rootType   type the validation pass started from
	 * @param violations violations found, in discovery order
	 */
	ValidationResultImpl(Class<?> rootType, List<Violation> violations) {
		this.rootType = rootType;
		this.violations = order(violations);
	}

	/**
	 * Sorts violations and discards duplicates.
	 *
	 * <p>
	 * The same rule can be declared at more than one level of a type hierarchy. All
	 * of those declarations are evaluated, since a subtype may narrow a constraint,
	 * but identical ones failing at the same path describe one problem and are
	 * reported once.
	 * </p>
	 *
	 * @param violations violations found, in discovery order
	 * @return unmodifiable ordered violations
	 */
	private static List<IuConstraintViolation> order(List<Violation> violations) {
		final var sorted = new ArrayList<>(violations);
		sorted.sort(ORDER);

		final List<IuConstraintViolation> distinct = new ArrayList<>(sorted.size());
		Violation previous = null;
		for (final var violation : sorted) {
			if (previous != null //
					&& previous.path().equals(violation.path()) //
					&& previous.annotation().equals(violation.annotation()))
				continue;

			distinct.add(violation);
			previous = violation;
		}

		return Collections.unmodifiableList(distinct);
	}

	@Override
	public Iterator<IuConstraintViolation> iterator() {
		return violations.iterator();
	}

	@Override
	public boolean isValid() {
		return violations.isEmpty();
	}

	@Override
	public int count() {
		return violations.size();
	}

	@Override
	public Class<?> rootType() {
		return rootType;
	}

	@Override
	public String report(boolean includeValues) {
		if (violations.isEmpty())
			return "Valid " + rootType.getName();

		var pathWidth = 0;
		var constraintWidth = 0;
		for (final var violation : violations) {
			pathWidth = Math.max(pathWidth, violation.path().length());
			constraintWidth = Math.max(constraintWidth, violation.constraint().getSimpleName().length());
		}

		final var report = new StringBuilder("Invalid ").append(rootType.getName()).append(": ")
				.append(violations.size()).append(violations.size() == 1 //
						? " constraint violation"
						: " constraint violations");

		for (final var violation : violations) {
			report.append("\n  ").append(pad(violation.path(), pathWidth)) //
					.append("  @").append(pad(violation.constraint().getSimpleName(), constraintWidth)) //
					.append("  ").append(violation.message());

			if (includeValues)
				report.append(" (was ").append(display(violation.invalidValue())).append(')');
		}

		return report.toString();
	}

	@Override
	public void checkValid() throws IuValidationException {
		if (!violations.isEmpty())
			throw new IuValidationException(this);
	}

	/**
	 * Right-pads text to a column width.
	 *
	 * @param text  text to pad
	 * @param width column width
	 * @return padded text
	 */
	private static String pad(String text, int width) {
		final var padded = new StringBuilder(text);
		while (padded.length() < width)
			padded.append(' ');
		return padded.toString();
	}

	/**
	 * Renders an invalid value for a report that has opted into including values.
	 *
	 * @param value invalid value
	 * @return display text, with text values quoted so that surrounding whitespace
	 *         is visible
	 */
	private static String display(Object value) {
		if (value instanceof CharSequence)
			return '"' + value.toString() + '"';
		return String.valueOf(value);
	}

	@Override
	public String toString() {
		return report();
	}

}
