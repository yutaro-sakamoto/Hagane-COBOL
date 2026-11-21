import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.cyclonedx.Version as CycloneDxVersion
import org.cyclonedx.model.Component

plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.diffplug.spotless") version "7.2.1"
    id("java")
    id("maven-publish")
    pmd
    id("com.github.spotbugs") version "6.5.13"
    jacoco
    id("org.cyclonedx.bom") version "3.4.1"
}

repositories {
    mavenCentral()
}

// The Javadoc of libcobj is published in two languages.
// The comments in the source code are written in Japanese, and `javadoc` builds
// the Japanese version from them as they are. `javadocEn` builds the English
// version from a copy of the sources whose comments are replaced with the
// translations in libcobj/javadoc-i18n/en/ (see JavadocTranslator).
val javadocI18n: SourceSet by sourceSets.creating
val javadocTranslationsEn = rootProject.layout.projectDirectory.dir("javadoc-i18n/en")
val javadocSourcesEn = layout.buildDirectory.dir("javadoc-src/en")

// Paths on the website where each version of the Javadoc is published.
val javadocUrlEn = "/javadoc/libcobj/index.html"
val javadocUrlJa = "/ja/javadoc/libcobj/index.html"

fun StandardJavadocDocletOptions.configureLibcobjJavadoc(lang: String, otherLanguageLink: String) {
    encoding = "UTF-8"
    docEncoding = "UTF-8"
    charSet = "UTF-8"
    locale = lang
    windowTitle = "libcobj API"
    docTitle = "libcobj API"
    bottom(otherLanguageLink)
    // It seems that the following line does not work.
    addStringOption("Xdoclint:missing")
}

tasks {
    javadoc {
        (options as StandardJavadocDocletOptions).configureLibcobjJavadoc(
            "ja_JP", "<a href=\"$javadocUrlEn\">English</a>")
    }

    val translateJavadocEn by registering(JavaExec::class) {
        description = "Copies the sources of libcobj with the Javadoc comments translated into English."
        group = "documentation"
        classpath = javadocI18n.runtimeClasspath
        mainClass.set("org.haganecobol.javadoci18n.JavadocTranslator")
        val srcDir = sourceSets.main.get().java.srcDirs.first()
        inputs.dir(srcDir)
        inputs.dir(javadocTranslationsEn).optional()
        outputs.dir(javadocSourcesEn)
        doFirst { delete(javadocSourcesEn) }
        args(
            "apply",
            "--source", srcDir.absolutePath,
            "--translations", javadocTranslationsEn.asFile.absolutePath,
            "--output", javadocSourcesEn.get().asFile.absolutePath)
    }

    register<Javadoc>("javadocEn") {
        description = "Generates the English Javadoc of libcobj into build/docs/javadoc-en."
        group = "documentation"
        dependsOn(translateJavadocEn)
        source(javadocSourcesEn)
        include("**/*.java")
        classpath = sourceSets.main.get().compileClasspath
        setDestinationDir(layout.buildDirectory.dir("docs/javadoc-en").get().asFile)
        (options as StandardJavadocDocletOptions).configureLibcobjJavadoc(
            "en_US", "<a href=\"$javadocUrlJa\">日本語</a>")
    }

    register<JavaExec>("syncJavadocTranslations") {
        description =
            "Updates libcobj/javadoc-i18n/en/ to match the Javadoc comments in the sources:" +
                " adds new Japanese texts with empty translations and removes unused ones."
        group = "documentation"
        classpath = javadocI18n.runtimeClasspath
        mainClass.set("org.haganecobol.javadoci18n.JavadocTranslator")
        args(
            "sync",
            "--source", sourceSets.main.get().java.srcDirs.first().absolutePath,
            "--translations", javadocTranslationsEn.asFile.absolutePath)
    }

    register<JavaExec>("checkJavadocTranslations") {
        description =
            "Lists the Javadoc texts without an English translation." +
                " Fails on untranslated texts with -PfailOnMissingTranslation."
        group = "verification"
        classpath = javadocI18n.runtimeClasspath
        mainClass.set("org.haganecobol.javadoci18n.JavadocTranslator")
        val checkArgs =
            mutableListOf(
                "check",
                "--source", sourceSets.main.get().java.srcDirs.first().absolutePath,
                "--translations", javadocTranslationsEn.asFile.absolutePath)
        if (project.hasProperty("failOnMissingTranslation")) {
            checkArgs.add("--fail-on-missing")
        }
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            checkArgs.add("--github-actions")
        }
        args(checkArgs)
    }
    compileJava {
        options.encoding = "UTF-8"
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }
}

dependencies {
    implementation("com.google.guava:guava:33.8.0-jre")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("commons-cli:commons-cli:1.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    implementation("org.json:json:20260814")
    spotbugs("com.github.spotbugs:spotbugs:4.10.5")

    implementation("org.slf4j:slf4j-api:2.0.20")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.20")
    testImplementation("com.github.valfirst:slf4j-test:3.0.3")
    implementation("org.postgresql:postgresql:42.7.14")

    // Only used by the tool that translates the Javadoc. Not part of libcobj.jar.
    "javadocI18nImplementation"("org.yaml:snakeyaml:2.5")
}

configurations {
    testRuntimeOnly {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

pmd {
    isConsoleOutput = true
    ruleSets = listOf()
    ruleSetFiles = files("${rootDir}/config/pmdRuleSet.xml")
}


spotbugs {
    excludeFilter.set(project.file("${rootDir}/config/spotbugsFilter.xml"))
}

spotless {
  java {
    googleJavaFormat("1.17.0").aosp().reflowLongStrings().skipJavadocFormatting()
  }
}

// SBOM of libcobj.jar, attached to every GitHub release.
// libcobj.jar is a shadow jar, so the third-party libraries it bundles are not
// visible from the jar itself. The SBOM lists them instead.
tasks.cyclonedxDirectBom {
    // Only the runtime classpath ends up in the shadow jar. Build-time and
    // test-only dependencies are not part of the released artifact.
    includeConfigs.set(listOf("runtimeClasspath"))
    projectType.set(Component.Type.LIBRARY)
    componentGroup.set("org.haganecobol")
    componentName.set("libcobj")
    componentVersion.set("2.0.0")
    schemaVersion.set(CycloneDxVersion.VERSION_16)
    // The plugin always writes both formats, and both are published as release
    // assets. Keep them next to each other instead of in the plugin's default
    // directory.
    jsonOutput.set(layout.buildDirectory.file("reports/libcobj-sbom.json"))
    xmlOutput.set(layout.buildDirectory.file("reports/libcobj-sbom.xml"))
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            // Publish to the repository the workflow runs in, so publishing
            // works on forks with the automatic GITHUB_TOKEN as well.
            url =
                uri(
                    "https://maven.pkg.github.com/" +
                        (System.getenv("GITHUB_REPOSITORY") ?: "opensourcecobol/opensourcecobol4j"))
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            groupId = "org.haganecobol"
            artifactId = "libcobj"
            version = "2.0.0"
            from(components["java"])
        }
    }
}

application {
    mainClass.set("")
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("libcobj")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.test {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
	}
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("org.haganecobol.libcobj.sql")
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

