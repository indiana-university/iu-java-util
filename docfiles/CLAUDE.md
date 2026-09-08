# CLAUDE.md — docfiles

`iu-java-docfiles`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Javadoc branding assets — two files, `src/main/resources/iu-javadoc.css` and `rivet.min.css` (the IU Rivet design system stylesheet). No Java source, no `module-info.java`, no tests.

It is built **first** in the root POM's module list because every other module's Javadoc run depends on it.

## How it is consumed

The root `pom.xml` wires it into `maven-javadoc-plugin` for the whole reactor:

```xml
<resourcesArtifacts>
  <resourcesArtifact>
    <groupId>edu.iu.util</groupId>
    <artifactId>iu-java-docfiles</artifactId>
    <version>${iu-java-util.version}</version>
  </resourcesArtifact>
</resourcesArtifacts>
<additionalOptions>
  <additionalOption>--main-stylesheet</additionalOption>
  <additionalOption>iu-javadoc.css</additionalOption>
  <additionalOption>--add-stylesheet</additionalOption>
  <additionalOption>rivet.min.css</additionalOption>
</additionalOptions>
```

The matching Rivet header and footer markup lives in the root POM's `<top>` and `<bottom>` Javadoc configuration, not here. A visual change to the generated documentation usually means editing both this module's CSS and that markup.

Because `docfiles` is resolved as a released/installed artifact rather than a reactor path, a change here only affects Javadoc output after the module is installed — run `mvn -pl docfiles install` before rebuilding documentation elsewhere.
