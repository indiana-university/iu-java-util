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
/**
 * Provides uniform, optimized, access to runtime type introspection metadata.
 * 
 * <p>
 * <img alt="UML Class Diagram" src="doc-files/iu-java-type-api.svg" />
 * 
 * <p>
 * Includes support for:
 * </p>
 * 
 * <ul>
 * <li><a href=
 * "https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/package-summary.html">Java
 * Reflection</a></li>
 * <li><a href=
 * "https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/beans/package-summary.html">Java
 * Beans</a></li>
 * <li><a href= "https://jakarta.ee/specifications/interceptors/2.1/">Jakarta
 * Interceptors</a></li>
 * <li><a href="https://jakarta.ee/specifications/annotations/2.1/">Jakarta
 * Annotations</a></li>
 * </ul>
 *
 * <h2>Introspection scope</h2>
 * <p>
 * Type introspection supports container deployments, which inject resources
 * into and intercept non-public members. A type's fields, properties, and
 * methods are introspected only when its package is <strong>open</strong> to
 * the implementation module. In a named module, that requires {@code opens}:
 * </p>
 *
 * <pre>
 * module com.example.api {
 * 	exports com.example.api;
 * 	opens com.example.api;
 * }
 * </pre>
 *
 * <p>
 * Packages in the unnamed module and in automatic modules are open. A type in a
 * package that is exported but not open is <em>opaque</em>: it has a
 * {@link edu.iu.type.IuType#hierarchy() hierarchy} and annotations, but no
 * fields, properties, or methods, and contributes none to its subtypes. This
 * is intentional: shared library modules typically export their API without
 * opening it, and are compile-time dependencies rather than runtime injection
 * points, so they are left out. {@code opens} is the opt-in.
 * </p>
 *
 * <p>
 * Contract modules whose API types are meant to be introspected, for example
 * interfaces a deployed component implements, <em>must</em> open those
 * packages. Use an unqualified {@code opens}: the implementation module is
 * typically loaded in a child module layer, which a qualified
 * {@code opens ... to} cannot target. The implementation logs each type it
 * treats as opaque only because its package is exported but not open, at
 * {@link java.util.logging.Level#FINE FINE}.
 * </p>
 *
 * @see edu.iu.type.IuType
 */
package edu.iu.type;