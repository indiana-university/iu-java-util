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
package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;

import net.shibboleth.shared.xml.XMLParserException;

@SuppressWarnings("javadoc")
public class SamlParserPoolTest {

	@Test
	public void testParserPool() throws ParserConfigurationException {
		SamlParserPool parserPool = new SamlParserPool();
		DocumentBuilder builder = parserPool.getBuilder();
		assertNotNull(builder);
	}

	@Test
	public void testParserConfigurationException()
			throws XMLParserException, ParserConfigurationException, FileNotFoundException, URISyntaxException {
		DocumentBuilderFactory mockDbf = mock(DocumentBuilderFactory.class);
		doThrow(ParserConfigurationException.class).when(mockDbf).newDocumentBuilder();

		SamlParserPool parserPool = new SamlParserPool();
		DocumentBuilder builder = parserPool.getBuilder();
		assertNotNull(parserPool.newDocument());
		assertNotNull(builder);
		assertThrows(IllegalStateException.class, () -> parserPool.parse(new InputStream() {

			@Override
			public int read() throws IOException {
				return 0;
			}
		}));

		String initialString = "<foo><bar>baz</bar></foo>";
		InputStream targetStream = new ByteArrayInputStream(initialString.getBytes());
		assertNotNull(parserPool.parse(targetStream));
		assertNotNull(parserPool.parse(new StringReader(initialString)));

	}

	@Test
	public void testReturnBuilder() throws ParserConfigurationException {
		SamlParserPool parserPool = new SamlParserPool();
		assertThrows(UnsupportedOperationException.class, () -> parserPool.returnBuilder(null));
	}

}
