import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.math.BigDecimal

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "com.reservation"
    version = "0.0.1-SNAPSHOT"
    description = "fcfs-reservation"
    extra["kotlin.version"] = rootProject.libs.versions.kotlin.lang.get()
}

subprojects {
    apply(plugin = rootProject.libs.plugins.kotlin.jvm.get().pluginId)
    apply(plugin = rootProject.libs.plugins.kotlin.spring.get().pluginId)
    apply(plugin = rootProject.libs.plugins.spring.dependency.management.get().pluginId)
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.core.get())
    }

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.spring.boot.get()}")
        }
    }

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        "implementation"(rootProject.libs.kotlin.reflect)
        "implementation"(rootProject.libs.kotlin.logging)
        "testImplementation"(rootProject.libs.kotest.assertions.core)
        "testImplementation"(rootProject.libs.kotest.runner.junit5)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }
}

configure(listOf(project(":apps:domain"), project(":apps:application"))) {
    apply(plugin = "jacoco")

    configure<JacocoPluginExtension> {
        toolVersion = rootProject.libs.versions.jacoco.get()
    }

    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    val packagePrefix =
        when (path) {
            ":apps:domain" -> "package com.reservation.domain."
            ":apps:application" -> "package com.reservation.application."
            else -> ""
        }
    val coreCoverageIncludes =
        if (!sourceRoot.exists()) {
            emptyList()
        } else {
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { sourceFile ->
                    val sourceText = sourceFile.readText()
                    sourceText.contains(packagePrefix) &&
                        sourceText.contains(Regex("""\bfun\s+\w+""")) &&
                        !sourceText.contains(Regex("""(?m)^\s*interface\s+""")) &&
                        !sourceText.contains(Regex("""(?m)^\s*enum\s+class\s+"""))
                }.flatMap { sourceFile ->
                    val relativePath = sourceFile.relativeTo(sourceRoot).invariantSeparatorsPath
                    val packagePath = relativePath.substringBeforeLast("/")
                    val className = sourceFile.nameWithoutExtension
                    sequenceOf(
                        "$packagePath/$className.class",
                        $$"$$packagePath/$$className$*.class",
                    )
                }.toList()
        }
    val coreClassDirectories =
        files(
            fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
                include(coreCoverageIncludes)
            },
        )

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named<Test>("test"))
        classDirectories.setFrom(coreClassDirectories)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named<Test>("test"))
        classDirectories.setFrom(coreClassDirectories)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.80")
                }
            }
        }
    }
}
