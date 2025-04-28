/*
 * Copyright © 2025 Indiana University
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
package iu.auth.saml;

import java.io.InputStream;
import java.io.Reader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import edu.iu.IuException;
import net.shibboleth.shared.xml.ParserPool;
import net.shibboleth.shared.xml.XMLParserException;

/**
 * Implemented custom parser pool to support SAML Parser pool
 *
 */
@SuppressWarnings("exports") // requires static only
public class SamlParserPool implements ParserPool {

	private final DocumentBuilderFactory builderFactory;

	/**
	 * Initialize the pool.
	 * 
	 * @throws ParserConfigurationException when DocumentBuilderFactory or the
	 *                                      DocumentBuilders it creates cannot
	 *                                      support this feature.
	 */
	public SamlParserPool() throws ParserConfigurationException {
		DocumentBuilderFactory newFactory = DocumentBuilderFactory.newInstance();
		newFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		newFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

		newFactory.setCoalescing(true);
		newFactory.setExpandEntityReferences(false);
		newFactory.setIgnoringComments(true);
		newFactory.setIgnoringElementContentWhitespace(true);
		newFactory.setNamespaceAware(true);
		newFactory.setSchema(null);
		newFactory.setValidating(false);
		newFactory.setXIncludeAware(false);

		builderFactory = newFactory;

	}

	@Override
	public DocumentBuilder getBuilder() {
		return IuException.unchecked(() -> builderFactory.newDocumentBuilder());
	}

	@Override
	public void returnBuilder(DocumentBuilder builder) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Document newDocument() throws XMLParserException {
		return getBuilder().newDocument();
	}

	@Override
	public Document parse(InputStream input) {
		return IuException.unchecked(() -> getBuilder().parse(input));
	}

	@Override
	public Document parse(Reader input) {
		return IuException.unchecked(() -> getBuilder().parse(new InputSource(input)));
	}

}
