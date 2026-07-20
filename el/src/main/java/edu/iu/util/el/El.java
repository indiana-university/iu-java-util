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
package edu.iu.util.el;

import static edu.iu.util.el.ElUtils.ANY;
import static edu.iu.util.el.ElUtils.EMPTY;
import static edu.iu.util.el.ElUtils.ESC_TOKEN;
import static edu.iu.util.el.ElUtils.getCloseBracket;
import static edu.iu.util.el.ElUtils.getIndexFrom;
import static edu.iu.util.el.ElUtils.select;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import edu.iu.client.IuJson;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Evaluates a compact expression and template language over Jakarta JSON
 * values. It is intended for thin views whose templates are stored as module or
 * class-path resources.
 *
 * <p>
 * Expressions start with a context symbol and may be followed by path elements
 * and postfix operations. Supported expression features include:
 * </p>
 * <ul>
 * <li>{@code $} selects the current JSON context, {@code root} selects the
 * original context, {@code _} selects the previous expression result, and
 * {@code p.} evaluates a path against the parent template context.</li>
 * <li>{@code i}, {@code head}, and {@code tail} expose an array item's index,
 * whether it is the first item, and whether it is not the first item while a
 * template is iterating an array or, when introspecting an object with
 * {@code &}, a property's name in place of a numeric index.</li>
 * <li>Dot-separated path elements select object members or array indexes. A
 * path element beginning with {@code /} is evaluated as a JSON Pointer; for
 * example, {@code $./items/0/name}.</li>
 * <li>{@code '} quotes the rest of an expression as text, {@code *} starts a
 * comment, and {@code @} returns raw text. Atomic results are HTML-escaped by
 * default.</li>
 * <li>{@code ?} evaluates its following expression when the current value is
 * truthy, and {@code !} evaluates its following expression when the value is
 * falsey. The two may be combined as {@code condition?ifTrue!ifFalse}. Missing
 * values, JSON null, false, and numbers whose integer value is zero are
 * falsey.</li>
 * <li>{@code =} compares the current value with the following expression.</li>
 * <li>{@code #} formats numbers with a {@link DecimalFormat} pattern and ISO
 * instant strings with a {@link SimpleDateFormat} pattern.</li>
 * <li>{@code &} marks the current value, which must be a JSON object, so that
 * the template applied by the expression that follows (see {@code <} below)
 * is applied once per property instead of once for the whole object.
 * {@code &} applied to a value that is not a JSON object throws
 * {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * <p>
 * {@code <} applies a template whose resource path is produced by the following
 * expression. Resource paths may be absolute or relative to the containing
 * template. A template contains expressions in braces, such as
 * {@code {$.name}}; prefixing an opening brace with {@code \} leaves it as
 * literal text. Applying a template to an array renders it once per item, and
 * applying a template to an object marked with {@code &} renders it once per
 * property; both make the iteration symbols above available. An inline
 * template is delimited by backticks after {@code <}, for example
 * {@code <`Hello {$.name}`}. Nested resource and inline templates are
 * supported.
 * </p>
 */
public final class El {

	private static final ThreadLocal<DecimalFormat> DECIMAL_FMT = new ThreadLocal<DecimalFormat>() {
		@Override
		protected DecimalFormat initialValue() {
			return new DecimalFormat();
		}
	};

	private static final ThreadLocal<SimpleDateFormat> DATE_FMT = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat();
		}
	};

	private static final ThreadLocal<DateTimeFormatter> DATE_TIME_FMT = new ThreadLocal<DateTimeFormatter>() {
		@Override
		protected DateTimeFormatter initialValue() {
			return DateTimeFormatter.ISO_INSTANT;
		}
	};

	private El() {
	}

	/**
	 * Evaluate an expression with no context.
	 * 
	 * @param expr input expression
	 * @return {@link JsonValue} representation of the input expression
	 */
	public static JsonValue eval(String expr) {
		return eval(null, expr);
	}

	/**
	 * Evaluate an expression within a given context.
	 * 
	 * @param context context within which to evaluate the expression
	 * @param expr    the expression to evaluate
	 * @return {@link JsonValue} representation of the input expression within the
	 *         given context
	 */
	public static JsonValue eval(JsonValue context, String expr) {
		return eval(context, expr, null);
	}

	private static boolean isTruthy(JsonValue value) {
		return value != null //
				&& !((value instanceof JsonNumber n) //
						&& n.intValue() == 0) //
				&& !JsonValue.FALSE.equals(value) //
				&& !JsonValue.NULL.equals(value);
	}

	/**
	 * Evaluate an expression within a given context.
	 * 
	 * @param context      context within which to evaluate the expression
	 * @param expr         the expression to evaluate
	 * @param readResource resource evaluation function; SHOULD map strictly to a
	 *                     least-privilege set of preloaded template resources. Note
	 *                     that this utility performs no verification of resource
	 *                     names, e.g., to prevent path traversal. The caller is
	 *                     responsible for sanitizing template names before using to
	 *                     locate a file or hosted resource.
	 * @return {@link JsonValue} representation of the input expression within the
	 *         given context
	 */
	public static JsonValue eval(JsonValue context, String expr, Function<String, String> readResource) {
		final Map<String, ElTemplate> templateCache = new HashMap<>();
		final Map<String, ElTemplate> inlineTemplateCache = new HashMap<>();
		final var rootContext = new ElContext(context, expr);
		final Deque<ElContext> evalStack = new ArrayDeque<>();
		evalStack.push(rootContext);

		while (!evalStack.isEmpty()) {
			final var evalContext = evalStack.pop();
			if (evalContext.isEmpty()) {
				evalContext.complete();
				continue;
			} else
				evalStack.push(evalContext);

			final var position = evalContext.getPosition();
			final var expression = evalContext.getExpression();
			switch (expression.charAt(0)) {

			case '*': // comment
				if (position == 0)
					evalContext.setResult(EMPTY);
				evalContext.setPositionAtEnd();
				break;

			case '\'': { // quote
				if (position != 0)
					throw new IllegalArgumentException("unexpected ' " + evalContext);

				var endPos = getIndexFrom(expression, '*', 1);
				while (endPos > 0) {
					if (expression.charAt(endPos - 1) != ESC_TOKEN)
						break;

					endPos = getIndexFrom(expression, '*', endPos + 1);
				}

				final String quoted;
				if (endPos == -1)
					quoted = expression.substring(1);
				else
					quoted = expression.substring(1, endPos);

				evalContext.setResult(IuJson.string(quoted.replace("\\*", "*")));
				evalContext.setPositionAtEnd();
				break;
			}

			case '@': // raw
				if (position != 0)
					throw new IllegalArgumentException("unexpected @ " + evalContext);

				evalContext.markAsRaw();
				evalContext.trim(1);
				break;

			case '&': { // introspect
				final var result = evalContext.getResult();
				if (!(result instanceof JsonObject))
					throw new IllegalArgumentException("unexpected & " + evalContext);
				
				evalContext.markAsIntrospect();
				evalContext.trim(1);
				break;
			}

			case '<': { // template
				final var result = evalContext.getResult();
				final var templatePathExpr = expression.substring(1);
				evalContext.setPositionAtEnd();

				final var inline = templatePathExpr.startsWith("`");

				if (inline) {
					final var elen = templatePathExpr.length();
					if (elen <= 1 //
							|| templatePathExpr.charAt(elen - 1) != '`')
						throw new IllegalArgumentException("inline template doesn't end with '`'");

					ElTemplate template = inlineTemplateCache.get(templatePathExpr);
					if (template == null)
						inlineTemplateCache.put(templatePathExpr, template = new ElTemplate(
								templatePathExpr.substring(1, templatePathExpr.length() - 1)));
					template.apply(result, evalContext, evalStack);
				} else {
					final var templatePathContext = new ElContext(evalContext, result, templatePathExpr,
							templateName -> {
								final var path = ((JsonString) templateName).getString();

								ElTemplate template = templateCache.get(path);
								if (template == null)
									templateCache.put(path,
											template = new ElTemplate(Objects.requireNonNull(
													Objects.requireNonNull(readResource,
															"missing readResource function").apply(path),
													"missing resource content " + path)));

								template.apply(result, evalContext, evalStack);
							});
					templatePathContext.markAsRaw();

					evalStack.push(templatePathContext);
				}
				break;
			}

			case '[': {
				final var closeBracket = getCloseBracket(expression, 1);
				if (closeBracket == -1)
					throw new IllegalArgumentException("missing close bracket ']'");

				evalContext.advancePosition(closeBracket + 1);

				final var result = evalContext.getResult();
				ElContext subContext = new ElContext(evalContext, false, null, evalContext.getContext(),
						expression.substring(1, closeBracket), name -> evalContext.setResult(select(result, name)));
				evalStack.push(subContext);
				break;
			}

			case '.': {
				final var endOfReference = getIndexFrom(expression, ANY, 1);
				final JsonValue result;
				if (endOfReference == -1) {
					result = select(evalContext.getResult(), expression.substring(1));
					evalContext.setPositionAtEnd();
				} else {
					result = select(evalContext.getResult(), expression.substring(1, endOfReference));
					evalContext.advancePosition(endOfReference);
				}
				evalContext.setResult(result);
				break;
			}

			case '?': { // if conditional
				final var result = evalContext.getResult();
				final var truthy = isTruthy(result);

				final var unlessPos = getIndexFrom(expression, '!', 1);
				if (truthy) {
					final var truthyExpression = unlessPos == -1 //
							? expression.substring(1)
							: expression.substring(1, unlessPos);

					evalStack.push(new ElContext(evalContext, evalContext.getContext(), truthyExpression,
							evalContext::setResult));

					evalContext.setPositionAtEnd();
				}

				else if (unlessPos == -1)
					evalContext.setPositionAtEnd();
				else
					evalContext.advancePosition(unlessPos);

				break;
			}

			case '!': { // unless conditional
				final var result = evalContext.getResult();
				if (!isTruthy(result))
					evalStack.push(new ElContext(evalContext, evalContext.getContext(), expression.substring(1),
							evalContext::setResult));

				evalContext.setPositionAtEnd();
				break;
			}

			case '=': { // equals match
				evalStack.push(new ElContext(evalContext, evalContext.getContext(), expression.substring(1),
						evalContext::setMatchResult));
				evalContext.setPositionAtEnd();
				break;
			}

			case '#': { // format
				final var cval = evalContext.getResult();
				if (cval instanceof JsonNumber) {
					DecimalFormat df = DECIMAL_FMT.get();
					df.applyPattern(expression.substring(1));
					evalContext.setResult(Json.createValue(df.format(((JsonNumber) cval).numberValue())));
				}
				// Expect the value is formatted as ISO 8601, treat it as a date and apply the
				// format pattern
				if (cval instanceof JsonString) {
					try {
						DateTimeFormatter dtf = DATE_TIME_FMT.get();
						final var instant = dtf.parse(((JsonString) cval).getString(), Instant::from);
						SimpleDateFormat df = DATE_FMT.get();
						df.applyPattern(expression.substring(1));
						evalContext.setResult(Json.createValue(df.format(new Date(instant.toEpochMilli()))));
					} catch (DateTimeParseException e) {
						// ignore
						// will return unformatted value
					}
				}
				evalContext.setPositionAtEnd();
				break;
			}

			default: {
				final var nextControlChar = getIndexFrom(expression, ANY, 0);
				final String symbol;
				if (nextControlChar == -1) {
					evalContext.setPositionAtEnd();
					symbol = expression;
				} else {
					evalContext.advancePosition(nextControlChar);
					symbol = expression.substring(0, nextControlChar);
				}

				switch (symbol) {
				case "p": {
					final var parentContext = Objects.requireNonNull(evalContext.getParent(), "missing parent context");
					evalStack.push(new ElContext(parentContext, parentContext.getContext(), expression.substring(1),
							evalContext::setResult));
					evalContext.setPositionAtEnd();
					continue;
				}

				case "_": {
					final var parentContext = Objects.requireNonNull(evalContext.getParent(), "missing parent context");
					evalStack.push(new ElContext(parentContext, parentContext.getThis(), expression.substring(1),
							evalContext::setResult));
					evalContext.setPositionAtEnd();
					continue;
				}

				case "root": {
					evalContext.setResult(evalContext.getRoot());
					continue;
				}

				case "$": {
					continue;
				}

				case "head": {
					evalContext.setResult(evalContext.isHead() ? JsonValue.TRUE : JsonValue.FALSE);
					continue;
				}

				case "tail": {
					evalContext.setResult(evalContext.isHead() ? JsonValue.FALSE : JsonValue.TRUE);
					continue;
				}

				case "i": {
					evalContext.setResult(evalContext.getIndex());
					continue;
				}

				default: {
					evalContext.setResult(select(evalContext.getResult(), symbol));
					continue;
				}
				}
			}
			}
		}

		return rootContext.getResult();
	}

}
