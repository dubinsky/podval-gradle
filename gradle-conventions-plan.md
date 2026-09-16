# Podval Gradle conventions plan

Not implemented. New repo (`Podval/podval-gradle`, GitHub `dubinsky/podval-gradle` when
published). This is **not** the site-publisher plugin and **not**
`dub.podval.org`.

## Why this repo, not `dub.podval.org`

`dub.podval.org` is a published notes site (and a site-publisher consumer). Putting
convention plugins there would mean `includeBuild('../dub.podval.org')` from `xml`,
`pulumi`, OpenTorah, MathWorlds — a website composite for build logic.

This repo is only Gradle conventions. Local path next to the other Podval Gradle
projects so `includeBuild('../podval-gradle')` matches `../site-publisher` /
`../xml`.

## Three plugins

Java binary plugins (`java-gradle-plugin`), same shape as
`org.podval.tools.site-publisher`. Plugin id is registered in `gradlePlugin { }`.

Do **not** add a fourth plugin for “versions only” or split Scala flags from the
toolchain. Do **not** put `org.podval.tools.site-publisher` here.

| Plugin id | Applied in | Does |
|---|---|---|
| `org.podval.conventions.settings` | `settings.gradle` | Foojay toolchain resolver; `dependencyResolutionManagement { repositories { mavenCentral() } }`. Does **not** apply `com.gradleup.nmcp.settings` (publishing builds keep that line). |
| `org.podval.conventions` | `build.gradle` | Applies `io.github.ben-manes.versions` and the shared `rejectVersionIf` (non-stable + padded numeric `0.017`). If `java` / `java-library` / `scala` is present: Java 25 toolchain. If `scala` is present: `-release:25`, `-new-syntax`, `-feature`, `-language:strictEquality`, `-source:future`. Does **not** skip `compileJava` (that is a local quirk of Scala-only projects; `family-polymorphism` has Java). |
| `org.podval.conventions.publish` | `build.gradle` (libraries only) | `javadocJar` from ScalaDoc **before** `withJavadocJar()`; `withSourcesJar()`; jar `duplicatesStrategy = EXCLUDE` and Implementation-* manifest; Maven publication POM (SCM, issues, CI, Apache-2.0, developer) and in-memory signing from `signingKey` / `signingPassword`. |

`org.podval.conventions` does not apply `scala` or `java` — the consumer still does.
Markup-only sites (`www.podval.org`, MathWorlds) can apply it without `scala` and still
get versions + toolchain once they have a Java plugin, or skip it and only use
site-publisher + settings.

### Publish extension

POM fields that differ per repo are an extension, not hard-coded Podval:

```gradle
plugins {
  id 'java-library'
  id 'scala'
  id 'maven-publish'
  id 'signing'
  id 'org.podval.conventions'
  id 'org.podval.conventions.publish'
}
podvalPublish {
  gitHubRepository = 'dubinsky/xml'   // required
  inceptionYear = '2026'
  orgName = 'Podval Group'            // default
  orgUrl = 'https://www.podval.org'   // default
  developerEmail = 'dub@podval.org'   // default
}
```

OpenTorah can apply settings + `org.podval.conventions` and either use this publish
plugin with `orgName = 'Open Torah Project'` / `developerEmail = 'dub@opentorah.org'`,
or keep its own `configure.gradle` publishing. Do not special-case OpenTorah in the
plugin code.

nmcp (`com.gradleup.nmcp.settings`, `nmcpSettings { centralPortal { … } }`) stays in
the library’s `settings.gradle`. It is already one block and is not used by sites.

## Consumer shape

```gradle
// settings.gradle
pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
  final File conventionsDir = file(
    providers.gradleProperty('podvalGradleDir').getOrElse('../podval-gradle')
  )
  if (conventionsDir.isDirectory() && new File(conventionsDir, 'settings.gradle').isFile()) {
    includeBuild(conventionsDir)
  }
}
plugins {
  id 'org.podval.conventions.settings'
}

// build.gradle
plugins {
  id 'scala'
  id 'org.podval.conventions'
  // libraries:
  id 'org.podval.conventions.publish'
}
```

Pin versions on the convention ids once they are on Maven Central (CI has no
composite). Local checkout substitutes, same as site-publisher.

## Local iteration: composite build, not mavenLocal

Same rule as site-publisher README **Dogfooding**: `pluginManagement { includeBuild }`
when the checkout exists; Maven Central when it does not. Do **not** use
`publishToMavenLocal` / `mavenLocal()` to try changes.

## Publish

Maven Central only (plugin marker POMs + implementation). Not the Gradle Plugin
Portal — these are Podval conventions, not a public plugin people look up by name.
`pluginManagement { repositories { mavenCentral() } }` is already required for
`org.podval.tools.site-publisher` if that plugin is also Central-only.

Do not publish until at least one library (`xml` or `site-publisher`) dogfoods via
includeBuild. Then Central, then pin versions in consumers so CI works without a
second checkout.

## Layout

```
src/main/java/org/podval/conventions/ConventionsSettingsPlugin.java
src/main/java/org/podval/conventions/ConventionsPlugin.java
src/main/java/org/podval/conventions/ConventionsPublishPlugin.java
src/main/java/org/podval/conventions/PublishExtension.java
```

`java-gradle-plugin` on this project. TestKit for “applies and sets toolchain /
versions filter / POM coordinates from the extension”.
