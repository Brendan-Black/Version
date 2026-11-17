// Version as a black.TypedString: com.github.Brendan-Black.Version:Black:<tag>. Its source is src/'s, with the interface
// added, so the two modules can't drift apart.
dependencies {
    "api"("com.github.Brendan-Black:TypedString:1.0.0")
}
extra["publishedDependencies"] = listOf("com.github.Brendan-Black:TypedString")

val typedSource = tasks.register<Sync>("typedSource") {
    description = "Copies src/ with Version implementing black.TypedString."
    from(rootProject.file("src"))
    into(layout.buildDirectory.dir("generated/sources/typed"))
    filter { line ->
        when (line) {
            "import java.util.regex.Pattern;" -> "$line\n\nimport black.TypedString;"
            else -> line.replace("implements Comparable<Version>", "implements TypedString, Comparable<Version>")
        }
    }
    // If src/ changes so that either line above no longer matches, fail rather than publish a Version without the interface.
    doLast {
        val text = destinationDir.resolve("black/version/Version.java").readText()
        check("import black.TypedString;" in text && "implements TypedString," in text) { "src/ no longer matches the typedSource filter" }
    }
}

sourceSets {
    main { java.setSrcDirs(listOf(typedSource)) }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "Black"
            from(components["java"])
            pom {
                name = "Version (Black)"
                description = "Compares version strings: v1.0.0 > 0.1.0, 1.0.0-beta < 1.0.0. Implements black.TypedString."
                url = "https://github.com/Brendan-Black/Version"
                licenses { license { name = "MIT"; url = "https://opensource.org/licenses/MIT" } }
            }
        }
    }
}
