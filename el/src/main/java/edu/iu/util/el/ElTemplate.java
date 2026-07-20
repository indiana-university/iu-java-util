package edu.iu.util.el;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import edu.iu.client.IuJson;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * Parsed representation of an EL template.
 *
 * <p>
 * Template expressions are delimited by &#123; and &#125;. An opening delimiter
 * preceded by {@code \} is treated as literal text and the escape character is
 * removed. Braces within an expression are balanced, and braces within nested
 * inline templates ({@code <`...`}) do not end the containing expression.
 * </p>
 *
 * <p>
 * Parsing removes expressions from the retained content and records their
 * insertion points. A parsed template may then be applied repeatedly to JSON
 * values. Expression contexts are pushed onto an evaluation stack so that they
 * complete in reverse order, preserving the recorded insertion points as
 * results are inserted into the output buffer.
 * </p>
 */
class ElTemplate {

	private static char START_TOKEN = '{';
	private static char TEMPLATE_TOKEN = '<';
	private static char INLINE_TOKEN = '`';
	private static char END_TOKEN = '}';
	private static String START = Character.toString(START_TOKEN);

	/**
	 * A parsed expression and its insertion point in expression-free template
	 * content.
	 *
	 * @param insertPoint position at which to insert the evaluated result
	 * @param expression  EL expression to evaluate
	 */
	record Expression(int insertPoint, String expression) {
	}

	private final String content;
	private final Iterable<Expression> expressions;

	/**
	 * Parses template content.
	 *
	 * @param content template content; {@code null} is treated as an empty template
	 * @throws IllegalStateException if an expression has no matching end delimiter
	 */
	ElTemplate(String content) {
		if (content == null)
			content = "";

		var indexOfStartToken = content.indexOf(START_TOKEN);
		if (indexOfStartToken == -1) {
			this.content = content;
			this.expressions = Collections.emptyList();
			return;
		}

		final List<Expression> expressionList = new ArrayList<>();
		final var contentBuffer = new StringBuilder(content);
		while (indexOfStartToken != -1) {

			if (indexOfStartToken > 0 // \{ -> prune escape char and skip start token
					&& contentBuffer.charAt(indexOfStartToken - 1) == ElUtils.ESC_TOKEN) {
				contentBuffer.deleteCharAt(indexOfStartToken - 1); // moves iot to next token
				indexOfStartToken = contentBuffer.indexOf(START, indexOfStartToken);
				continue;
			}

			final var length = contentBuffer.length();
			var depth = 0;
			var sdepth = 1;
			int indexOfEndToken;
			for (indexOfEndToken = indexOfStartToken + 1; //
					indexOfEndToken < length; //
					indexOfEndToken++) {

				char c = contentBuffer.charAt(indexOfEndToken);
				if (c == INLINE_TOKEN) {
					char p = contentBuffer.charAt(indexOfEndToken - 1);
					if (p == TEMPLATE_TOKEN)
						depth++; // start of inline template
					else if (depth > 0 //
							&& indexOfEndToken + 1 < length //
							&& contentBuffer.charAt(indexOfEndToken + 1) == END_TOKEN)
						depth--; // end of inline template
				}

				else if (c == START_TOKEN)
					sdepth++;
				else if (c == END_TOKEN) {
					sdepth--;
					if (depth == 0 //
							&& sdepth == 0)
						break; // found
				}
			}

			if (indexOfEndToken == length)
				throw new IllegalStateException("Missing end token '`}': " + content.substring(indexOfStartToken));

			expressionList.add(
					new Expression(indexOfStartToken, contentBuffer.substring(indexOfStartToken + 1, indexOfEndToken)));
			contentBuffer.delete(indexOfStartToken, indexOfEndToken + 1);

			indexOfStartToken = contentBuffer.indexOf(START, indexOfStartToken);
		}
		this.content = contentBuffer.toString();
		this.expressions = expressionList::iterator;
	}

	/**
	 * Applies this template to an evaluation result and schedules completion of the
	 * rendered output.
	 *
	 * <p>
	 * When {@code result} is an array, the template is applied once to each element
	 * with its numeric index and first-element state available to the expression
	 * contexts. All other values are rendered once. A completion context is placed
	 * beneath the template expression contexts on {@code evalStack}; after those
	 * expressions complete, it stores the rendered buffer as a JSON string in
	 * {@code evalContext}.
	 * </p>
	 *
	 * @param result      JSON value to which the template is applied
	 * @param evalContext evaluation context that receives the rendered result
	 * @param evalStack   stack on which completion and expression contexts are
	 *                    scheduled
	 */
	void apply(JsonValue result, ElContext evalContext, Deque<ElContext> evalStack) {
		final var buffer = new StringBuilder();
		evalStack.push(
				new ElContext(evalContext, null, null, a -> evalContext.setResult(IuJson.string(buffer.toString()))));

		if (result instanceof JsonArray) {
			var array = result.asJsonArray();
			for (int i = 0; i < array.size(); i++)
				apply(buffer, i == 0, IuJson.number(i), array.get(i), evalContext, evalStack);
		} else // TODO: introspect if & is seen
			apply(buffer, false, null, result, evalContext, evalStack);
	}

	/**
	 * Appends this template's static content and schedules its expressions for
	 * evaluation.
	 *
	 * <p>
	 * String and number results are inserted into {@code buffer}. Null and boolean
	 * results do not add text. JSON objects and arrays are rejected because a
	 * template expression must produce a single value.
	 * </p>
	 *
	 * @param buffer    output buffer receiving static content and expression
	 *                  results
	 * @param first     whether {@code value} is the first item in an iteration
	 * @param key       current iteration key or index
	 * @param value     JSON value used as the expression context
	 * @param context   parent evaluation context
	 * @param evalStack stack on which expression contexts are scheduled
	 * @throws IllegalArgumentException when an expression produces a JSON object or
	 *                                  array
	 */
	void apply(StringBuilder buffer, boolean first, JsonValue key, JsonValue value, ElContext context,
			Deque<ElContext> evalStack) {
		int offset = buffer.length();
		buffer.append(content);
		for (Expression expr : expressions) {
			ElContext elc = new ElContext(context, first, key, value, expr.expression, result -> {
				String resultText;
				if (result == null)
					return;
				else if (result instanceof JsonString)
					resultText = ((JsonString) result).getString();
				else if (result instanceof JsonNumber)
					resultText = result.toString();
				else if (result instanceof JsonStructure)
					throw new IllegalArgumentException(
							"invalid result " + result + ", expected a single value from " + expr.expression);
				else
					return;

				buffer.insert(offset + expr.insertPoint, resultText);
			});

			// push template expressions in order found, to process the last
			// expression first and avoid the need to adjust insert points
			evalStack.push(elc);
		}
	}

}
