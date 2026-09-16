# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-09-16
- Gradle plugins `org.podval.conventions.settings`, `org.podval.conventions`, and `org.podval.conventions.publish` (`org.podval.conventions`; plugin markers on Maven Central, not the Gradle Plugin Portal).
- `org.podval.conventions.settings`: Foojay toolchain resolver; `dependencyResolutionManagement { repositories { mavenCentral() } }`. Does not apply `com.gradleup.nmcp.settings`.
- `org.podval.conventions`: applies `io.github.ben-manes.versions` and shared `rejectVersionIf` (non-stable + padded numeric `0.017`). Java 25 toolchain when `java` is present. Scala 3 flags when `scala` is present (`-release:25`, `-new-syntax`, `-feature`, `-language:strictEquality`, `-source:future`); `scala.scalaVersion` from the `scalaVersion` Gradle property when set. Does not apply `java` or `scala`; does not skip `compileJava`.
- `org.podval.conventions.publish`: `javadocJar` from ScalaDoc before `withJavadocJar()`; `withSourcesJar()`; jar `duplicatesStrategy = EXCLUDE` and Implementation-* manifest; Maven publication POM (SCM, issues, CI, Apache-2.0, developer) and in-memory signing from `signingKey` / `signingPassword`. Creates a `library` publication unless `java-gradle-plugin` is applied. Sets `gradlePlugin.website` / `vcsUrl` from `gitHubRepository`. `podvalPublish { }` extension for per-repo POM fields.
- Dogfood with `pluginManagement { includeBuild }`, not `mavenLocal()`.
