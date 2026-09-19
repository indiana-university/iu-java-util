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
import java.util.List;

import edu.iu.validation.IuValidationNode;
import edu.iu.validation.IuValidationNodeKind;

/**
 * Immutable path from a validated root object to one value in its object graph.
 *
 * <p>
 * Modeled as a singly linked list toward the root, so that extending a path is
 * constant time and shared prefixes are shared in memory. A breadth-first walk
 * holds many paths at once, most of them siblings.
 * </p>
 *
 * <p>
 * Each segment renders itself as the path is extended rather than by dispatching
 * on {@link IuValidationNodeKind} afterward, which keeps the rendering rule next
 * to the factory that knows it.
 * </p>
 */
final class ValidationPath {

	/** One segment. Which fields are populated depends on {@code kind}. */
	private record PathNode(IuValidationNodeKind kind, String name, Integer index, Object key)
			implements IuValidationNode {
	}

	/** The empty path, describing the root object itself. */
	static final ValidationPath ROOT = new ValidationPath(null,
			new PathNode(IuValidationNodeKind.BEAN, null, null, null), "");

	private final ValidationPath parent;
	private final IuValidationNode node;
	private final String rendered;

	/**
	 * Constructor.
	 *
	 * @param parent   path to the container of {@code node}; null for the root
	 * @param node     this segment
	 * @param rendered the whole path from the root through {@code node}
	 */
	private ValidationPath(ValidationPath parent, IuValidationNode node, String rendered) {
		this.parent = parent;
		this.node = node;
		this.rendered = rendered;
	}

	/**
	 * Extends this path with a bean property.
	 *
	 * @param name property name
	 * @return extended path, rendered {@code container.name}
	 */
	ValidationPath property(String name) {
		return new ValidationPath(this, new PathNode(IuValidationNodeKind.PROPERTY, name, null, null),
				rendered.isEmpty() ? name : rendered + '.' + name);
	}

	/**
	 * Extends this path with one element of an {@link Iterable} or array.
	 *
	 * @param index position in iteration order
	 * @return extended path, rendered {@code container[index]}
	 */
	ValidationPath element(int index) {
		return new ValidationPath(this, new PathNode(IuValidationNodeKind.ITERABLE_ELEMENT, null, index, null),
				rendered + '[' + index + ']');
	}

	/**
	 * Extends this path with one value of a {@link java.util.Map}.
	 *
	 * @param key map key addressing the value
	 * @return extended path, rendered {@code container[key]}
	 */
	ValidationPath mapValue(Object key) {
		return new ValidationPath(this, new PathNode(IuValidationNodeKind.MAP_VALUE, null, null, key),
				rendered + '[' + key + ']');
	}

	/**
	 * Extends this path with one key of a {@link java.util.Map}.
	 *
	 * @param key map key
	 * @return extended path, rendered <code>container[key]&lt;key&gt;</code>, the
	 *         trailing marker distinguishing a failure in the key itself from one
	 *         in the value stored under it
	 */
	ValidationPath mapKey(Object key) {
		return new ValidationPath(this, new PathNode(IuValidationNodeKind.MAP_KEY, null, null, key),
				rendered + '[' + key + "]<key>");
	}

	/**
	 * Gets the segments of this path, root first.
	 *
	 * @return unmodifiable node list; never empty, since the root node is always
	 *         present
	 */
	List<IuValidationNode> nodes() {
		final List<IuValidationNode> nodes = new ArrayList<>();

		for (var segment = this; segment != null; segment = segment.parent)
			nodes.add(segment.node);

		Collections.reverse(nodes);
		return Collections.unmodifiableList(nodes);
	}

	@Override
	public String toString() {
		return rendered;
	}

}
