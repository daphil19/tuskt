# Tuskt

Tuskt is a kotlin implementation of the Tus protocol.

This repo aims to include a server and a client

## Releases

Releases are managed locally with `./gradlew --no-configuration-cache release`.

During release, Gradle will:

- convert `VERSION_NAME` from `-SNAPSHOT` to the release version
- move the `CHANGELOG.md` `Unreleased` notes into a dated version section
- run the configured verification tasks
- commit the release, create the `vX.Y.Z` tag, and bump to the next patch snapshot

After reviewing the generated commits and tag, push the branch and tags to trigger CI/CD publishing.
