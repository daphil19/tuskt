import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

plugins {
    id("tuskt.jvm-module")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(projects.tusktServer)
    implementation(ktorLibs.server.netty)
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "dev.phillipslabs.tuskt.standalone.MainKt"
    }
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
