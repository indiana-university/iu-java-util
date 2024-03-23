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
package iu.auth.util;

@SuppressWarnings("javadoc")
public class JwksUtilsTest {
//
//	@Test
//	public void testInvalidECJWK() {
//		assertThrows(IllegalArgumentException.class,
//				() -> JwksUtils.toECPublicKey(Json.createObjectBuilder().add("kty", "").build()));
//	}
//
//	@Test
//	public void testInvalidRSAJWK() {
//		assertThrows(IllegalArgumentException.class,
//				() -> JwksUtils.toRSAPublicKey(Json.createObjectBuilder().add("kty", "").build()));
//	}
//
//	@Test
//	public void testECParameterSpec() throws NoSuchAlgorithmException, InvalidParameterSpecException {
//		assertECParameterSpec("P-256", "secp256r1");
//		assertECParameterSpec("P-384", "secp384r1");
//		assertECParameterSpec("P-521", "secp521r1");
//		assertThrows(IllegalArgumentException.class,
//				() -> JwksUtils.getECParameterSpec(Json.createObjectBuilder().add("crv", "").build()));
//	}
//
//	private void assertECParameterSpec(String crv, String stdName)
//			throws NoSuchAlgorithmException, InvalidParameterSpecException {
//		class Box {
//			ECGenParameterSpec spec;
//		}
//		final var box = new Box();
//		try (final var mockAlgorithmParameters = mockStatic(AlgorithmParameters.class); //
//				final var a = mockConstruction(ECGenParameterSpec.class, (spec, ctx) -> {
//					assertEquals(stdName, ctx.arguments().get(0));
//					box.spec = spec;
//				})) {
//			final var algParams = mock(AlgorithmParameters.class);
//			mockAlgorithmParameters.when(() -> AlgorithmParameters.getInstance("EC")).thenReturn(algParams);
//
//			JwksUtils.getECParameterSpec(Json.createObjectBuilder().add("crv", crv).build());
//			verify(algParams).init(box.spec);
//			verify(algParams).getParameterSpec(ECParameterSpec.class);
//		}
//	}

}
