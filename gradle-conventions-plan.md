# Podval Gradle conventions — remaining

Plugins and consumer docs are in `README.adoc`. This file is only what is not done.

- Dogfood: `includeBuild` from `xml` or `site-publisher`, strip the duplicated Groovy. Then Central, then pin versions in consumers (sequence is in the README).
- Do **not** add a fourth plugin for “versions only” or split Scala flags from the toolchain.
