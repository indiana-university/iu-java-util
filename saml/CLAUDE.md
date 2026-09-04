# CLAUDE.md — saml

`iu-java-saml-api`, `iu-java-saml-impl` / modules `iu.util.saml`, `iu.util.saml.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

SAML 2.0 Service Provider — metadata publication, AuthnRequest generation, and Response validation. Authentication state is carried across the redirect round-trip in a `session` detail.

`api` compiles at the inherited level; `impl` overrides `release` to **17**.

## OpenSAML is an optional runtime dependency

This is the module's defining constraint. `module-info.java` declares every OpenSAML and Shibboleth module `requires static`, and `pom.xml` scopes them `provided`:

```java
requires static org.opensaml.core;
requires static org.opensaml.saml;
requires static org.opensaml.saml.impl;
requires static org.opensaml.security;
requires static org.opensaml.security.impl;
requires static org.opensaml.xmlsec;
requires static org.opensaml.xmlsec.impl;
requires static net.shibboleth.shared.support;
```

Consequences when editing:

- OpenSAML types must not appear in any exported signature. They are confined to the internals of `iu.saml`; the public API speaks `edu.iu.saml` types plus `IuStatefulRedirect` and `IuRequestAttributes` from `base`.
- The deploying application supplies OpenSAML on the runtime module path. A missing provider surfaces as `NoClassDefFoundError` at first use, not at startup.
- OpenSAML artifacts resolve from the **Shibboleth Nexus** repository declared in this POM, and BouncyCastle is excluded in favor of the JDK providers used by `crypt`.

## API surface (`edu.iu.saml`)

```java
String metadata();                                                  // SP metadata XML
IuStatefulRedirect initRequest(URI postUri, URI returnUri);         // begin authentication
IuStatefulRedirect verifyResponse(IuRequestAttributes attrs, String samlResponse, String relayState);
IuSamlPrincipal getPrincipalIdentity(IuRequestAttributes attrs);
```

`IuSamlAssertion` models a verified assertion; `IuSamlPrincipal` is the resulting identity.

## Implementation notes (`iu.saml`)

`SamlServiceProvider` is the entry point. Validation is split across `SamlResponseValidator` and `IuSubjectConfirmationValidator`; `SamlParserPool` manages XML parsers (pooled deliberately — parser construction dominates response processing cost). `XmlDomUtil` holds the DOM helpers, and `SamlPreAuthentication` / `SamlPostAuthentication` are the session details carried across the redirect. Configuration is `iu.saml.config.IuSamlServiceProviderMetadata`, bound through `IuConfig`.

Response validation is the security boundary: every rejection path needs a negative test.
