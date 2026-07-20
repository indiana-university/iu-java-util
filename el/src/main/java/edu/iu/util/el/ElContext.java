package edu.iu.util.el;

import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.text.StringEscapeUtils;

import edu.iu.client.IuJson;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Mutable state for evaluating one EL expression.
 *
 * <p>
 * A context retains the JSON value being evaluated, the root and parent
 * contexts, the unconsumed portion of the expression, and its current result.
 * Child contexts inherit the root value and may provide iteration metadata and
 * a completion action. Completion actions allow nested expressions and template
 * expressions to pass their results back to the evaluation that created them.
 * </p>
 *
 * <p>
 * Results are HTML escaped by default when evaluation completes. Calling
 * {@link #markAsRaw()} disables escaping for expressions that explicitly
 * request an unescaped value.
 * </p>
 */
class ElContext {

	private final JsonValue root;
	private final ElContext parent;
	private final Consumer<JsonValue> then;
	private final boolean head;
	private final JsonValue index;
	private final JsonValue context;

	private String expression;

	private boolean raw;
	private int position;
	private JsonValue result;
	private Optional<JsonValue> matchResult;

	/**
	 * Creates a root evaluation context.
	 *
	 * @param context    JSON value used as both the root and current context
	 * @param expression expression to evaluate, or {@code null} for no expression
	 */
	ElContext(JsonValue context, String expression) {
		this(null, false, null, context, expression, null);
	}

	/**
	 * Creates a child evaluation context.
	 *
	 * @param parent     parent evaluation context, or {@code null}
	 * @param context    JSON value used as the child context
	 * @param expression expression to evaluate, or {@code null} for no expression
	 * @param then       action invoked with the completed result, or {@code null}
	 */
	ElContext(ElContext parent, JsonValue context, String expression, Consumer<JsonValue> then) {
		this(parent, false, null, context, expression, then);
	}

	/**
	 * Creates a child evaluation context for a value, optionally within an
	 * iteration.
	 *
	 * @param parent     parent evaluation context, or {@code null} for a root
	 *                   context
	 * @param head       whether the value is the first iteration item
	 * @param index      iteration key or index, or {@code null}
	 * @param context    JSON value used as the child context
	 * @param expression expression to evaluate, or {@code null} for no expression
	 * @param then       action invoked with the completed result, or {@code null}
	 */
	ElContext(ElContext parent, boolean head, JsonValue index, JsonValue context, String expression,
			Consumer<JsonValue> then) {
		if (parent == null)
			this.root = context;
		else
			this.root = parent.root;

		this.parent = parent;
		this.head = head;
		this.index = index;
		this.expression = expression;

		this.context = context;
		this.result = context;
		this.then = then;
	}

	/**
	 * Determines whether the expression has any unconsumed characters.
	 *
	 * @return {@code true} when the expression is absent or fully consumed
	 */
	boolean isEmpty() {
		return expression == null //
				|| position >= expression.length();
	}

	/**
	 * Completes evaluation of this context.
	 *
	 * <p>
	 * The completion action, when present, receives {@link #getResult()} before
	 * default HTML escaping is applied to this context's string result. Escaping is
	 * skipped when this context has been marked raw.
	 * </p>
	 */
	void complete() {
		if (then != null)
			then.accept(getResult());

		if (!raw //
				&& (result instanceof JsonString s))
			result = IuJson.string(StringEscapeUtils.escapeHtml4(s.getString()));
	}

	/**
	 * Returns the offset of the next unconsumed expression character.
	 *
	 * @return current expression offset
	 */
	int getPosition() {
		return position;
	}

	/**
	 * Advances the current expression position.
	 *
	 * @param position number of characters to advance
	 */
	void advancePosition(int position) {
		this.position += position;
	}

	/**
	 * Removes characters beginning at the current expression position.
	 *
	 * @param n number of characters to remove
	 */
	void trim(int n) {
		if (position == 0)
			expression = expression.substring(n);
		else {
			final var expression = new StringBuilder(this.expression);
			expression.delete(position, position + n);
			this.expression = expression.toString();
		}
	}

	/**
	 * Moves the current position to the end of the expression.
	 */
	void setPositionAtEnd() {
		this.position = expression.length();
	}

	/**
	 * Determines whether default result escaping is disabled.
	 *
	 * @return {@code true} when this context is raw
	 */
	boolean isRaw() {
		return raw;
	}

	/**
	 * Disables default HTML escaping for this context's result.
	 */
	void markAsRaw() {
		this.raw = true;
	}

	/**
	 * Returns the unconsumed portion of the expression.
	 *
	 * @return remaining expression, or an empty string when fully consumed or
	 *         absent
	 */
	String getExpression() {
		return isEmpty() ? "" : expression.substring(position);
	}

	/**
	 * Returns the effective result of this context.
	 *
	 * <p>
	 * Normally this is the current result. After {@link #setMatchResult(JsonValue)}
	 * is called, this method instead reports whether the current result equals the
	 * expected match value. A missing result or a {@code null} expected value does
	 * not match.
	 * </p>
	 *
	 * @return current result, or {@link JsonValue#TRUE} or {@link JsonValue#FALSE}
	 *         while evaluating a match
	 */
	JsonValue getResult() {
		if (matchResult == null)
			return result;

		if (result == null //
				|| matchResult.isEmpty())
			return JsonValue.FALSE;

		return result.equals(matchResult.get()) ? JsonValue.TRUE : JsonValue.FALSE;
	}

	/**
	 * Sets the current result without changing the current JSON context.
	 *
	 * @param result result to set, or {@code null}
	 */
	void setResult(JsonValue result) {
		this.result = result;
	}

	/**
	 * Enables match evaluation using the supplied expected value.
	 *
	 * @param matchResult expected value, or {@code null} to represent a missing
	 *                    match operand
	 */
	void setMatchResult(JsonValue matchResult) {
		this.matchResult = Optional.ofNullable(matchResult);
	}

	/**
	 * Returns the parent context.
	 *
	 * @return parent context, or {@code null} for a root context
	 */
	ElContext getParent() {
		return parent;
	}

	/**
	 * Returns the root JSON value of the context.
	 *
	 * @return the root JSON value
	 */
	JsonValue getRoot() {
		return root;
	}

	/**
	 * Determines whether this context represents the first item in an iteration.
	 *
	 * @return {@code true} for the first iteration item
	 */
	boolean isHead() {
		return head;
	}

	/**
	 * Returns the index JSON value of the context.
	 *
	 * @return iteration key or index, or {@code null}
	 */
	JsonValue getIndex() {
		return index;
	}

	/**
	 * Returns the JSON value against which this expression is evaluated.
	 *
	 * @return the JSON value
	 */
	JsonValue getContext() {
		return context;
	}

	/**
	 * Returns the current result without applying match evaluation.
	 *
	 * @return value initialized from the current context or subsequently supplied
	 *         to {@link #setResult(JsonValue)}
	 */
	JsonValue getThis() {
		return result;
	}

	/**
	 * Returns a diagnostic representation showing the current expression position
	 * and parent chain.
	 *
	 * @return a string representation of the context
	 */
	@Override
	public String toString() {
		if (expression == null)
			return "EL";

		String exprPriorToPos = expression.substring(Math.max(0, position - 5), position);
		String exprCharAtPos = position < expression.length() //
				? "" + expression.charAt(position) //
				: "";
		String exprAfterPos = expression.substring(Math.min(expression.length(), position + 1),
				Math.min(expression.length(), position + 6));
		String parentStr = parent == null //
				? "" //
				: "\nParent " + parent;

		return "EL" + " expression \"" + expression + "\" at " + position + ": \"" + exprPriorToPos + "["
				+ exprCharAtPos + "]" + exprAfterPos + "\"" // + insertPointStr + templatePathStr
				+ parentStr;
	}

}
