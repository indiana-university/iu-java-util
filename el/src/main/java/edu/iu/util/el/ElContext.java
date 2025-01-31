package edu.iu.util.el;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.text.StringEscapeUtils;

import edu.iu.IuStream;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * 
 */
public class ElContext {

	private static final Logger LOG = Logger.getLogger(ElContext.class.getName());

	private static char START_TOKEN = '{';
	private static char TEMPLATE_TOKEN = '<';
	private static char INLINE_TOKEN = '`';
	private static char END_TOKEN = '}';
	private static String START = Character.toString(START_TOKEN);

	private static class TemplateExpression {
		private final int insertPoint;
		private final String expression;

		private TemplateExpression(int insertPoint, String expression) {
			this.insertPoint = insertPoint;
			this.expression = expression;
		}
	}

	private static class Template {
		private final String content;
		private final List<TemplateExpression> expressions;

		private Template(String content) {
			if (content == null) {
				this.content = "";
				this.expressions = Collections.emptyList();
				return;
			}

			int iot = content.indexOf(START_TOKEN);
			if (iot == -1) {
				this.content = content;
				this.expressions = Collections.emptyList();
				return;
			}

			List<TemplateExpression> el = new ArrayList<>();
			StringBuilder contentBuffer = new StringBuilder(content);
			while (iot != -1) {

				if (iot > 0 && contentBuffer.charAt(iot - 1) == El.ESC_TOKEN) {
					contentBuffer.deleteCharAt(iot - 1);
					iot = contentBuffer.indexOf(START, iot);
					continue;
				}

				if (iot > 0 && iot < contentBuffer.length() //
						&& contentBuffer.charAt(iot - 1) == '$' //
						&& contentBuffer.charAt(iot) == '{') {
					iot = contentBuffer.indexOf(START, iot + 1);
					continue;
				}

				int length = contentBuffer.length();
				int depth = 0;
				int sdepth = 1;
				int ioe;
				for (ioe = iot + 1; ioe < length; ioe++) {
					char c = contentBuffer.charAt(ioe);
					if (c == INLINE_TOKEN) {
						char p = contentBuffer.charAt(ioe - 1);
						if (p == TEMPLATE_TOKEN)
							depth++;
						else if (depth > 0 && ioe + 1 < length && contentBuffer.charAt(ioe + 1) == END_TOKEN)
							depth--;
					} else if (c == START_TOKEN)
						sdepth++;
					else if (c == END_TOKEN) {
						sdepth--;
						if (depth == 0 && sdepth == 0)
							break;
					}
				}
				if (ioe == length)
					throw new IllegalStateException("Missing end token '`}': " + content.substring(iot));

				el.add(new TemplateExpression(iot, contentBuffer.substring(iot + 1, ioe)));
				contentBuffer.delete(iot, ioe + 1);

				iot = contentBuffer.indexOf(START, iot);
			}
			this.content = contentBuffer.toString();
			this.expressions = el;
		}

		private void apply(boolean first, JsonValue key, JsonValue value, ElContext context,
				Deque<ElContext> evalStack) {
			int offset = context.templateBuffer.length();
			context.templateBuffer.append(content);
			for (TemplateExpression expr : expressions) {
				ElContext elc = new ElContext(context, first, key, value, expr.expression);
				elc.insertPoint = offset + expr.insertPoint;

				// push template expressions in order found, to process the last
				// expression first and avoid the need to adjust insert points
				evalStack.push(elc);
			}
		}
	}

	private final JsonValue root;
	private final ElContext parent;
	private final boolean head;
	private final JsonValue index;
	private final JsonValue context;

	private final String expression;
	private final Map<String, Template> templateCache;

	private boolean raw;
	private int position;
	private JsonValue result;
	private JsonValue matchResult;
	private JsonValue lastResult;

	private boolean template;
	private String templatePath;
	private StringBuilder templateBuffer;
	private int insertPoint = -1;

	ElContext(ElContext parent, boolean head, JsonValue index, JsonValue context, String expression) {
		this.parent = parent;
		this.head = head;
		this.index = index;
		this.context = context;
		this.expression = expression;

		if (parent == null) {
			this.root = context;
			this.templateCache = new HashMap<>();
		} else {
			this.root = parent.root;
			this.templateCache = parent.templateCache;
		}

		setResult(context);
	}

	ElContext(ElContext replace, String expression) {
		this.parent = replace.parent;
		this.head = replace.head;
		this.index = replace.index;
		this.context = replace.context;
		this.root = replace.root;
		this.templateCache = replace.templateCache;
		this.insertPoint = replace.insertPoint;

		this.expression = expression;
		this.lastResult = replace.getResult();
		setResult(context);
	}

	boolean isEmpty() {
		return expression == null || position == -1 || position >= expression.length();
	}

	int getPosition() {
		return position;
	}

	void advancePosition(int position) {
		this.position += position;
	}

	void setPositionAtEnd() {
		this.position = expression.length();
	}

	boolean isRaw() {
		return raw;
	}

	void markAsRaw() {
		this.raw = true;
	}

	String getExpression() {
		return isEmpty() ? "" : expression.substring(position);
	}

	JsonValue getResult() {
		if (matchResult == null)
			return result;

		if (result == null)
			return JsonValue.FALSE;

		return matchResult.equals(result) ? JsonValue.TRUE : JsonValue.FALSE;
	}

	void setResult(JsonValue result) {
		this.result = result;
	}

	void setMatchResult(JsonValue matchResult) {
		this.matchResult = matchResult;
	}

	void postProcessResult(Deque<ElContext> evalStack) {
		if (template)
			result = Json.createValue(templateBuffer.toString());

		if (raw && parent == null)
			return;

		String resultText;
		if (result == null || result.equals(JsonValue.NULL))
			return;
		else if (result instanceof JsonString)
			resultText = ((JsonString) result).getString();
		else if (result.equals(JsonValue.TRUE) || result.equals(JsonValue.FALSE) || (result instanceof JsonNumber))
			resultText = result.toString();
		// try adding this to skip to the next context if the parent is a template
		else if (parent !=null && parent.isTemplate())
			return;
		else
			throw new IllegalStateException("Non-atmoic result");

		if (!raw)
			result = Json.createValue(resultText = StringEscapeUtils.escapeHtml4(resultText));

		if (parent != null && parent.template)
			if (insertPoint == -1) // template name expression
				parent.setupTemplate(resultText, evalStack);
			else
				parent.templateBuffer.insert(insertPoint, resultText);
	}

	boolean isTemplate() {
		return template;
	}

	void markAsTemplate() {
		this.template = true;
		this.raw = true;
	}

	private void setupTemplate(String path, Deque<ElContext> evalStack) {
		assert template && templatePath == null && templateBuffer == null;

		String resourcePath = path == null ? "" : path;
		boolean inline = resourcePath.length() > 1 && resourcePath.charAt(0) == '`'
				&& resourcePath.charAt(resourcePath.length() - 1) == '`';
		if (!inline) {
			int ioc = resourcePath.indexOf(':');
			if (ioc == -1 && (resourcePath.isEmpty() || resourcePath.charAt(0) != '/')) {
				String parentDir;
				String parentPath = parent == null ? null : parent.templatePath;
				if (parentPath == null)
					parentDir = "";
				else {
					int lioc = parentPath.indexOf(':') + 1;
					int lios = parentPath.lastIndexOf('/');
					parentDir = parentPath.substring(0, lios < lioc ? lioc : lios);
				}

				if (resourcePath.isEmpty())
					resourcePath = parentDir;
				else if (parentDir != null && !parentDir.isEmpty())
					resourcePath = (parentDir + '/' + resourcePath).intern();
			}

			// Strip leading slash from resource path
			if (resourcePath.isEmpty() || (resourcePath.length() == 1 && resourcePath.charAt(0) == '/'))
				resourcePath = "";
			else if (resourcePath.charAt(0) == '/')
				resourcePath = resourcePath.substring(1);
		}

		templatePath = resourcePath.intern();

		Template template = templateCache.get(templatePath);
		if (template == null)
			if (inline)
				template = new Template(resourcePath.substring(1, resourcePath.length() - 1));
			else {
				URL rurl = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
				if (rurl == null) {
					LOG.fine(() -> "Resource " + templatePath + " (" + path + ") not found");
					throw new IllegalArgumentException(resourcePath);
				}

				try (InputStream is = rurl.openStream(); Reader r = new InputStreamReader(is)) {
					templateCache.put(templatePath, template = new Template(IuStream.read(r)));
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}

		templateBuffer = new StringBuilder();

		if (result instanceof JsonArray) {
			var array = result.asJsonArray();
			for (int i = 0; i < array.size(); i++)
				template.apply(i == 0, Json.createValue(i), array.get(i), this, evalStack);
		} else
			template.apply(false, null, result, this, evalStack);
	}

	/**
	 * Returns the root JSON value of the context.
	 *
	 * @return the root JSON value
	 */
	public JsonValue getRoot() {
		return root;
	}

	/**
	 * Checks if this context is the head context.
	 *
	 * @return true if this context is the head, false otherwise
	 */
	public boolean isHead() {
		return head;
	}

	/**
	 * Checks if this context is the tail context.
	 *
	 * @return true if this context is the tail, false otherwise
	 */
	public boolean isTail() {
		return !head;
	}

	/**
	 * Returns the index JSON value of the context.
	 *
	 * @return the index JSON value
	 */
	public JsonValue getI() {
		return index;
	}

	/**
	 * Returns the JSON value representing the context.
	 *
	 * @return the JSON value
	 */
	public JsonValue get$() {
		return context;
	}

	/**
	 * Returns the last result JSON value of the context.
	 *
	 * @return the last result JSON value
	 */
	public JsonValue get_() {
		return lastResult;
	}

	/**
	 * Returns the JSON value of the parent context.
	 *
	 * @return the parent context JSON value
	 */
	public ElContext getP() {
		return parent;
	}

	/**
	 * Returns a string representation of the context.
	 *
	 * @return a string representation of the context
	 */
	@Override
	public String toString() {
		return "EL" + (expression == null ? ""
				: " expression \"" + expression + "\" at " + position + ": \""
						+ expression.substring(Math.max(0, position - 5), position) + "["
						+ (position >= 0 && position < expression.length() ? expression.charAt(position) : "") + "]"
						+ expression.substring(Math.min(expression.length(), position + 1),
								Math.min(expression.length(), position + 6))
						+ "\"" + (insertPoint >= 0 ? " insert at " + insertPoint : "")
						+ (template && templatePath != null
								? "\n   in " + templatePath
										+ (templateBuffer == null ? "" : " = \"" + templateBuffer + "\"")
								: "")
						+ (parent == null ? "" : "\nParent " + parent));
	}

}
