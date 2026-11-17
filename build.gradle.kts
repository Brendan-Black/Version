import net.ltgt.gradle.errorprone.errorprone

// Two modules, which JitPack builds from each tag, both from the one source in src/:
//   free   com.github.Brendan-Black.Version:free:<tag>   no dependencies at all
//   Black  com.github.Brendan-Black.Version:Black:<tag>  the same class, implementing black.TypedString
// Both are black.version.Version, so switching from one to the other changes only the dependency line.
plugins {
    // Formatting and compiler warnings, checked by `check`: https://github.com/Brendan-Black/black-java
    id("black.java-standards") version "1.3.6" apply false
    // NullAway: whatever isn't @Nullable is never null, checked at compile time.
    id("net.ltgt.errorprone") version "5.2.0" apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "black.java-standards")
    apply(plugin = "net.ltgt.errorprone")

    // mavenCentral: the java-standards checks download their formatter and compiler from there. JitPack: TypedString.
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    group = "com.github.Brendan-Black.Version"
    version = providers.environmentVariable("VERSION").orElse("dev").get().removePrefix("v")

    configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
        withJavadocJar()
    }

    // Error Prone and NullAway are build-time only.
    dependencies {
        "errorprone"("com.google.errorprone:error_prone_core:2.52.0")
        "errorprone"("com.uber.nullaway:nullaway:0.14.3")
    }

    // Built with JDK 25, but runs on 17 and up, so it fits as many projects as possible.
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 17
        options.compilerArgs.add("-Xlint:all")
        options.errorprone {
            enabled.set(this@configureEach.name == "compileJava") // tests pass bad values on purpose
            disableAllWarnings = true // only the checks below, and Error Prone's own errors, count
            error("NullAway")
            option("NullAway:AnnotatedPackages", "black.version")
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }

    tasks.named<Jar>("jar") {
        manifest { attributes("Implementation-Version" to project.version) }
        from(rootProject.file("LICENSE"))
    }

    // The tests are a plain main() (no test framework), run against each module's own Version.
    val sourceSets = the<SourceSetContainer>()
    val launcher = the<JavaToolchainService>().launcherFor(the<JavaPluginExtension>().toolchain)
    sourceSets.named("test") { java.setSrcDirs(listOf(rootProject.file("test"))) }
    val runTests = tasks.register<JavaExec>("runTests") {
        description = "Runs test/black/version/VersionTest."
        group = "verification"
        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "black.version.VersionTest"
        javaLauncher = launcher
        // Which artifact this is, free or Black: the test checks Version implements what that one promises.
        systemProperty("version.flavour", project.the<PublishingExtension>().publications.getByName<MavenPublication>("maven").artifactId)
    }
    tasks.named("test") { enabled = false }

    // What users download must be exactly Version, LICENSE and the dependencies a module lists in
    // extra["publishedDependencies"]: in the POM (Maven) and in the module metadata (Gradle), each as group:artifact.
    val checkPackaging = tasks.register("checkPackaging") {
        description = "Fails if the published jar, POM or module metadata carries anything unexpected."
        group = "verification"
        val jar = tasks.named<Jar>("jar")
        val pom = tasks.named<GenerateMavenPom>("generatePomFileForMavenPublication")
        val metadata = tasks.named<GenerateModuleMetadata>("generateMetadataFileForMavenPublication")
        dependsOn(jar, pom, metadata)
        doLast {
            @Suppress("UNCHECKED_CAST")
            val expected = project.extra["publishedDependencies"] as List<String>

            val entries = java.util.zip.ZipFile(jar.get().archiveFile.get().asFile).use { zip -> zip.entries().asSequence().map { it.name }.sorted().toList() }
            val expectedEntries = listOf("LICENSE", "META-INF/", "META-INF/MANIFEST.MF", "black/", "black/version/", "black/version/Version.class")
            check(entries == expectedEntries) { "the jar holds $entries, not $expectedEntries" }

            // In the POM, every dependency must be compile scope: an implementer of TypedString needs it to compile.
            val pomText = pom.get().destination.readText()
            val pomDependencies = Regex("<dependency>\\s*<groupId>(.*?)</groupId>\\s*<artifactId>(.*?)</artifactId>.*?<scope>(.*?)</scope>", RegexOption.DOT_MATCHES_ALL)
                .findAll(pomText).map { "${it.groupValues[1]}:${it.groupValues[2]} (${it.groupValues[3]})" }.toList()
            check(pomDependencies == expected.map { "$it (compile)" }) { "the POM lists $pomDependencies, not $expected" }

            // In the module metadata, each dependency appears once for compiling against and once for running.
            val json = groovy.json.JsonSlurper().parse(metadata.get().outputFile.get().asFile) as Map<*, *>
            val metadataDependencies = (json["variants"] as List<*>).flatMap { variant ->
                ((variant as Map<*, *>)["dependencies"] as List<*>? ?: listOf<Any>()).map { (it as Map<*, *>).let { d -> "${d["group"]}:${d["module"]}" } }
            }.sorted()
            check(metadataDependencies == (expected + expected).sorted()) { "the module metadata lists $metadataDependencies, not $expected twice" }
        }
    }
    tasks.named("check") { dependsOn(runTests, checkPackaging) }
}
