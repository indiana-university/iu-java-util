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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import edu.iu.client.IuJsonAdapter;

/**
 * Adapts arrays of a primitive component type, such as {@code int[]}, whose
 * items box to and from their wrapper type.
 */
class PrimitiveArrayAdapter extends JsonArrayAdapter<Object, Object> {

	private final Class<?> component;

	/**
	 * Constructor
	 *
	 * @param itemAdapter adapts the boxed component type; reads a JSON null as
	 *                    the primitive default
	 * @param component   primitive component type
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	PrimitiveArrayAdapter(IuJsonAdapter itemAdapter, Class<?> component) {
		super(itemAdapter);
		this.component = component;
	}

	@Override
	public Iterator<Object> iterator(Object value) {
		return IntStream.range(0, Array.getLength(value)).mapToObj(i -> Array.get(value, i)).iterator();
	}

	@Override
	public Object collect(Iterable<Object> items) {
		final List<Object> list = new ArrayList<>();
		items.forEach(list::add);

		final var size = list.size();
		final var array = Array.newInstance(component, size);
		for (var i = 0; i < size; i++)
			Array.set(array, i, list.get(i));
		return array;
	}

}
