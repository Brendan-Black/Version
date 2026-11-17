// Version with no dependencies: com.github.Brendan-Black.Version:free:<tag>.
extra["publishedDependencies"] = listOf<String>()

sourceSets {
    main { java.setSrcDirs(listOf(rootProject.file("src"))) }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "free"
            from(components["java"])
            pom {
                name = "Version (free)"
                description = "Compares version strings: v1.0.0 > 0.1.0, 1.0.0-beta < 1.0.0. No dependencies."
                url = "https://github.com/Brendan-Black/Version"
                licenses { license { name = "MIT"; url = "https://opensource.org/licenses/MIT" } }
            }
        }
    }
}
