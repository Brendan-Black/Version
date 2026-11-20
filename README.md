# Version

[![JitPack](https://jitpack.io/v/Brendan-Black/Version.svg)](https://jitpack.io/#Brendan-Black/Version)

Compares version strings in Java. One class, Java 17+, in two flavours: one with no dependencies at all, and one that
also implements [`black.TypedString`](https://github.com/Brendan-Black/TypedString).

```java
import black.version.Version;

new Version("v1.0.0").isGreaterThan(new Version("0.1.0"));   // true
new Version("1.10.0").isGreaterThan(new Version("1.9.0"));   // true: parts are numbers, not text
new Version("1.0.0-beta").isLessThan(new Version("1.0.0"));  // true: a pre-release comes before its release
new Version("1.3").isAtLeast(new Version("1.2"));            // true
new Version("1.2.0").isGreaterThan(new Version("1.2"));      // true: on a tie, the more precise version wins
```

Ordering follows [Semantic Versioning](https://semver.org/#spec-item-11), and also accepts a leading `v`, any number
of numeric parts, leading zeros (`01.2` is `1.2`), and numbers of any size. Build metadata (`+sha.5114f85`) is ignored.
Anything else, such as `1..2` or `" 1.0"`, throws `IllegalArgumentException`.

Versions with different numbers of parts compare like significant figures: the numbers decide first, with a missing
part counting as zero; when they tie, the more precise version is greater; and only then does the pre-release count.

```
1.2-beta  <  1.2  <  1.2.0-beta  <  1.2.0  <  1.2.1
```

So a project at `1.2` is not at least `1.2.0`: write a requirement with no more parts than the versions it checks.

`Version` is `Comparable`, and `equals` agrees with `compareTo` (`1.2` equals `v01.2`, but not `1.2.0`), so it sorts
and deduplicates safely. `toString()` gives back the text as written.

## Install

Via [JitPack](https://jitpack.io/#Brendan-Black/Version). Pick one:

| Artifact | Depends on | For |
|---|---|---|
| `com.github.Brendan-Black.Version:free:1.0.0` | nothing | any project |
| `com.github.Brendan-Black.Version:Black:1.0.0` | [TypedString](https://github.com/Brendan-Black/TypedString) | code that uses `black.TypedString` |

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.Brendan-Black.Version:free:1.0.0") }
```

Both are the same class, `black.version.Version`, built from the one source in `src/`, so switching between them
changes only the dependency line.

## Build

`./gradlew check` checks formatting, compiles with every warning as an error, and runs the tests (`test/`, a plain
`main()`) against both artifacts: specific orderings, and Comparable's contract over every pair and triple of a few
dozen versions. It also checks each jar holds only `Version`, and that neither lists a dependency it doesn't promise.

## License

MIT
