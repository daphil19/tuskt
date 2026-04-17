package dev.phillipslabs.tuskt.gradle

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

fun ProviderFactory.gradlePropertyOrEnvVar(name: String): Provider<String> =
    gradleProperty(name).orElse(environmentVariable(name))
