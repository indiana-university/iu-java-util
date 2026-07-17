package edu.iu.util.el;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;

import edu.iu.client.IuJson;
import jakarta.json.Json;
import jakarta.json.JsonArray;
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
 * template is iterating an array.</li>
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
 * </ul>
 *
 * <p>
 * {@code <} applies a template whose resource path is produced by the following
 * expression. Resource paths may be absolute or relative to the containing
 * template. A template contains expressions in braces, such as
 * {@code {$.name}}; prefixing an opening brace with {@code \} leaves it as
 * literal text. Applying a template to an array renders it once per item and
 * makes the iteration symbols above available. An inline template is delimited
 * by backticks after {@code <}, for example {@code <`Hello {$.name}`}. Nested
 * resource and inline templates are supported.
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

	/**
	 * Character to indicate any control character
	 */
	static char ANY = '\0';
	/**
	 * Escape character
	 */
	static char ESC_TOKEN = '\\';
	/**
	 * Control characters
	 */
	static char[] CONTROL_CHARS = new char[] { '\'', '@', '<', '`', '=', '?', '!', '&', '#', '*' };
	/**
	 * Empty JsonString
	 */
	static JsonString EMPTY = Json.createValue("");

	static {
		Arrays.sort(CONTROL_CHARS);
	}

	private El() {
	}

	/**
	 * Finds the index of a character in a string, starting from a given position,
	 * while skipping over inline template blocks ({@code <`...`}}).
	 *
	 * <p>
	 * Inline template blocks delimited by {@code <`} and {@code `}} (or a terminal
	 * {@code `}) are skipped; nesting is tracked so that only characters at depth 0
	 * are considered when searching.
	 * </p>
	 *
	 * @param s    the string to search
	 * @param c    the character to search for, or {@link #ANY} ({@code '\0'}) to
	 *             match the first occurrence of any {@link #CONTROL_CHARS control
	 *             character}
	 * @param from the index to start searching from (inclusive)
	 * @return the index of the first matching character at depth 0, or {@code -1}
	 *         if not found
	 */
	static int getIndexFrom(String s, char c, int from) {
		boolean any = c == ANY;
		int l = s.length();
		int depth = 0;
		for (int i = from; i < l; i++) {
			char n = s.charAt(i);
			if (depth > 0) {
				if (n == '`') {
					char p = s.charAt(i - 1);
					if (p == '<')
						depth++;
					else if (i + 1 == l //
							|| s.charAt(i + 1) == '}')
						depth--;
				}
			} else if (any) {
				if (Arrays.binarySearch(CONTROL_CHARS, n) >= 0)
					return i;
			} else if (n == c)
				return i;
			else if (n == '`' //
					&& i > 0 //
					&& s.charAt(i - 1) == '<')
				depth++;
		}
		return -1;
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
		ElContext evalContext = new ElContext(null, false, null, context, expr);
		Deque<ElContext> evalStack = new LinkedList<>();
		evalStack.push(evalContext);

		while (!evalStack.isEmpty()) {
			evalContext = evalStack.pop();
			if (evalContext.isEmpty()) {
				evalContext.postProcessResult(evalStack);
				continue;
			}

			String etail = evalContext.getExpression();
			int pos = evalContext.getPosition();
			switch (etail.charAt(0)) {

			case '*': // comment
				evalContext.setResult(EMPTY);
				evalContext.setPositionAtEnd();
				break;

			case '\'': // quote
				int commentPos = getIndexFrom(etail, '*', 1);
				if (commentPos == -1)
					evalContext.setResult(Json.createValue(etail.substring(1)));
				else if (etail.charAt(commentPos - 1) == ESC_TOKEN)
					evalContext.setResult(
							Json.createValue(etail.substring(1, commentPos - 1) + etail.substring(commentPos)));
				else
					evalContext.setResult(Json.createValue(etail.substring(1, commentPos)));
				evalContext.setPositionAtEnd();
				break;

			case '@': // raw
				evalContext.markAsRaw();
				evalContext.advancePosition(1);
				break;

			case '<': // template
				String templatePathExpr = etail.substring(1);
				JsonValue result = evalContext.getResult();

				ElContext templatePathContext = new ElContext(evalContext, false, null, result, templatePathExpr);
				templatePathContext.markAsRaw();

				evalContext.setPositionAtEnd();
				evalContext.markAsTemplate();
				evalStack.push(evalContext);
				evalStack.push(templatePathContext);
				continue;

			case '`': // inline template
				int elen = etail.length();
				final var onlyOneBackTick = elen <= 1;
				final var lastCharNotBackTick = etail.charAt(elen - 1) != '`';
				if (onlyOneBackTick || lastCharNotBackTick)
					throw new IllegalArgumentException("inline template doesn't end with '`'");
				evalContext.setResult(Json.createValue(etail));
				evalContext.setPositionAtEnd();
				break;

			case '?': { // if conditional
				final var cval = evalContext.getResult();
				final var unlessPos = getIndexFrom(etail, '!', 1);
				final var cond = cval != null //
						&& !((cval instanceof JsonNumber n) //
								&& n.intValue() == 0) //
						&& !JsonValue.FALSE.equals(cval) //
						&& !JsonValue.NULL.equals(cval);
				if (cond) {
					final var trueCondExprToEval = unlessPos == -1 ? etail.substring(1) : etail.substring(1, unlessPos);
					evalStack.push(new ElContext(evalContext, trueCondExprToEval));
				}
				if (cond || unlessPos == -1) {
					evalContext.setPositionAtEnd();
					continue;
				}
				evalContext.advancePosition(unlessPos);
				break;
			}

			case '!': { // unless conditional
				final var cval = evalContext.getResult();
				if (cval == null //
						|| ((cval instanceof JsonNumber n) //
								&& n.intValue() == 0) //
						|| JsonValue.FALSE.equals(cval) //
						|| JsonValue.NULL.equals(cval))
					evalStack.push(new ElContext(evalContext, etail.substring(1)));
				evalContext.setPositionAtEnd();
				continue;
			}

			case '=': { // equals match
				ElContext melc = new ElContext(evalContext, etail.substring(1));
				melc.setMatchResult(evalContext.getResult());
				evalStack.push(melc);
				evalContext.setPositionAtEnd();
				continue;
			}

			case '#': { // format
				final var cval = evalContext.getResult();
				if (cval instanceof JsonNumber) {
					DecimalFormat df = DECIMAL_FMT.get();
					df.applyPattern(etail.substring(1));
					evalContext.setResult(Json.createValue(df.format(((JsonNumber) cval).numberValue())));
				}
				// Expect the value is formatted as ISO 8601, treat it as a date and apply the
				// format pattern
				if (cval instanceof JsonString) {
					try {
						DateTimeFormatter dtf = DATE_TIME_FMT.get();
						final var instant = dtf.parse(((JsonString) cval).getString(), Instant::from);
						SimpleDateFormat df = DATE_FMT.get();
						df.applyPattern(etail.substring(1));
						evalContext.setResult(Json.createValue(df.format(new Date(instant.toEpochMilli()))));
					} catch (DateTimeParseException e) {
						// ignore
						// will return unformatted value
					}
				}
				evalContext.setPositionAtEnd();
				break;
			}

			case 'p': {
				if (etail.length() < 2 //
						|| etail.charAt(1) != '.') {
					throw new IllegalArgumentException("expected '.' after 'p'");
				}
				String parentPathExpr = etail.substring(2);
				ElContext parentContext = evalContext.getP();
				if (parentContext == null)
					throw new IllegalArgumentException("no parent context");
				ElContext parentPathContext = new ElContext(parentContext, false, null, parentContext.get$(),
						parentPathExpr);

				parentPathContext.markAsRaw();
				evalContext.setPositionAtEnd();

				evalStack.push(evalContext);
				evalStack.push(parentPathContext);
				continue;
			}

			default:
				int npos = getIndexFrom(etail, ANY, pos);
				if (npos == -1)
					npos = etail.length();

				JsonValue selected = null;
				var path = etail.substring(0, npos);
				var dot = etail.indexOf('.');

				String firstSymbol;
				if (dot == -1)
					firstSymbol = path;
				else
					firstSymbol = path.substring(0, dot);
				switch (firstSymbol) {
				case "$":
					selected = evalContext.get$();
					break;

				case "_":
					selected = evalContext.get_();
					break;

				case "root":
					selected = evalContext.getRoot();
					break;

				case "head":
					selected = evalContext.isHead() ? JsonValue.TRUE : JsonValue.FALSE;
					break;

				case "tail":
					selected = evalContext.isTail() ? JsonValue.TRUE : JsonValue.FALSE;
					break;

				case "i":
					selected = evalContext.getI();
					break;

				default:
					throw new IllegalArgumentException("unexpected " + firstSymbol);
				}

				while (dot != -1) {
					var nextDot = path.indexOf('.', dot + 1);
					String pathElement;
					if (nextDot == -1)
						pathElement = path.substring(dot + 1);
					else
						pathElement = path.substring(dot + 1, nextDot);

					if (pathElement.startsWith("/")) {
						final var pointer = IuJson.PROVIDER.createPointer(pathElement);
						if (selected instanceof JsonObject)
							selected = pointer.getValue(selected.asJsonObject());
						else if (selected instanceof JsonArray)
							selected = pointer.getValue(selected.asJsonArray());
						else
							throw new IllegalArgumentException("expected object or array for " + selected);
					} else {
						if (selected instanceof JsonArray)
							selected = selected.asJsonArray().get(Integer.parseInt(pathElement));
						else if (selected instanceof JsonObject)
							selected = selected.asJsonObject().get(pathElement);
						else
							throw new IllegalArgumentException("expected object or array for " + selected);
					}

					dot = nextDot;
				}

				evalContext.setResult(selected);

				evalContext.advancePosition(npos);
				break;
			}

			evalStack.push(evalContext);
		}

		return evalContext.getResult();
	}

}
