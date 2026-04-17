# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Note on versioning:** This project follows semantic versioning with an emphasis on ABI compatibility. Major version numbers will be bumped when changes break ABI compatibility, even if the API remains backward compatible.

## [Unreleased]

### Added

- Initial release of the core Ktor Tus server library for resumable uploads.
- Initial release of the Kotlin Multiplatform Tus client library for interacting with Tus-compatible servers.
- Shared Tus protocol headers and constants module used by the server and client libraries.
- Standalone runnable server fat jar for simple deployment and evaluation.
- Integration test coverage validating interoperability between the client and server implementations.
