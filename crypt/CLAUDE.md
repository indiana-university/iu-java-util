# CLAUDE.md — crypt

`iu-java-crypt*` / modules `iu.util.crypt`, `iu.util.crypt.impl`, `iu.util.crypt.cli`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

JOSE — JSON Web Key, JSON Web Signature, and JSON Web Encryption — implemented directly on the JDK
crypto providers. This is the security foundation for `jwt`, `session`, `oidc`, `saml`, `pki`,
`config`, and `logging/impl`.

## Layout and build order

`crypt/pom.xml` builds `api`, then `impl`, then `cli`.

| Module | Java | Notes |
|---|---|---|
| `api` | 11 | `exports edu.iu.crypt`; `exports iu.crypt.spi to iu.util.crypt.impl`; `uses IuCryptSpi` |
| `impl` | 11 | `exports iu.crypt`; `provides IuCryptSpi with iu.crypt.CryptSpi` |
| `cli` | **21** | assembly-packaged `tar.gz` distribution |

## SPI and module-layer initialization

`edu.iu.crypt.Init` holds the `ServiceLoader`-resolved `IuCryptSpi` in a static initializer.

When both `iu.util.crypt` and `iu.util.crypt.impl` are loaded by the system class loader, resolution
is automatic. When they are loaded into a **non-system `ModuleLayer`** — which is how `type/loader`
and `logging` load components — the bootstrap module must call `Init.init()` explicitly while the
implementation module's `ClassLoader` is the thread context class loader. Getting this wrong produces
a `NoSuchElementException` from `ServiceLoader.findFirst().get()` at first use, far from the cause.

## API surface (`edu.iu.crypt`)

- `WebKey` and `WebKeyReference` — JWK, including generation and PEM/JWKS parsing.
- `WebSignature`, `WebSignedPayload` — JWS.
- `WebEncryption`, `WebEncryptionRecipient` — JWE, including multi-recipient.
- `WebCryptoHeader` — shared JOSE header handling across JWS and JWE.
- `WebCertificateReference`, `X509CertificateAuthority`, `X500Utils` — X.509 interop.
- `PemEncoded` — PEM parsing and generation.
- `EphemeralKeys` — throwaway key material.

Everything in this package is a builder-driven interface; the concrete classes are in `iu.crypt`
(`Jwk`, `Jws`, `Jwe`, `Jose`, and their `*Builder` counterparts). Add new capability by extending the
interface in `api` and the corresponding builder in `impl` together — the SPI split means an
interface method with no implementation compiles cleanly and fails at runtime.

## CLI

`crypt/cli` (`iu.crypt.cli.WebKeyCli`) is packaged by `maven-assembly-plugin` from
`src/assembly/bin.xml` into `iu-java-crypt-cli.tar.gz`, with launcher scripts from `src/bin/` and all
runtime dependencies under `lib/`. It is the only module in the repository compiled at Java 21.
Exercise it through `edu.iu.test.CliTestSupport`.

## Testing notes

`src/test/resources/META-INF/iu-test.properties` sets `iu.util.test.platformLoggers=edu.iu.crypt`,
exempting this package's own log output from `IuTestLogger`'s strict matching. Downstream modules
that use crypt (`session/impl`, `logging/impl`) copy that same line.
