package black.version;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version number, such as {@code 1.0.0}, {@code v2.3} or {@code 1.0.0-beta.2}, ordered the way Semantic Versioning
 * orders them, plus a rule for versions with different numbers of parts (below):
 *
 * <pre>{@code
 * new Version("v1.0.0").isGreaterThan(new Version("0.1.0")); // true
 * new Version("1.10.0").isGreaterThan(new Version("1.9.0")); // true: parts are numbers, not text
 * new Version("1.0.0-beta").isLessThan(new Version("1.0.0")); // true: a pre-release comes before its release
 * new Version("1.2.0").isGreaterThan(new Version("1.2")); // true: on a tie, the more precise version wins
 * }</pre>
 *
 * <p>What's accepted, beyond strict Semantic Versioning:
 * <ul>
 * <li>a leading {@code v} or {@code V}, which is ignored</li>
 * <li>any number of numeric parts, one or more</li>
 * <li>leading zeros, which are ignored: {@code 01.2} is {@code 1.2}</li>
 * <li>numbers of any size</li>
 * </ul>
 * A pre-release ({@code -alpha.1}) orders by Semantic Versioning's rules, and build metadata ({@code +sha.5114f85}) is
 * ignored: {@code 1.0.0+a} and {@code 1.0.0+b} are equal.
 *
 * <p>Versions with different numbers of parts compare like significant figures. The numbers decide first, with a missing
 * part counting as zero; when they tie, the more precise version is greater; and only then does the pre-release count.
 * So {@code 1.2-beta < 1.2 < 1.2.0-beta < 1.2.0 < 1.2.1}, and a project at {@code 1.2} is not at least {@code 1.2.0}.
 *
 * <p>{@link #equals} agrees with {@link #compareTo}, so versions sort and deduplicate safely. {@link #toString} gives
 * back the text as written.
 */
public final class Version implements Comparable<Version> {
	private static final String IDENTIFIER = "[0-9A-Za-z-]+";
	private static final String IDENTIFIERS = IDENTIFIER + "(?:\\." + IDENTIFIER + ")*";
	private static final Pattern SHAPE = Pattern.compile("[vV]?(\\d+(?:\\.\\d+)*)(?:-(" + IDENTIFIERS + "))?(?:\\+" + IDENTIFIERS + ")?");
	private static final String[] NONE = {};

	private final String value;
	/** The numeric parts, without leading zeros: {@code 1.02.0} is {@code [1, 2, 0]}. */
	private final String[] release;
	/** The pre-release identifiers, numeric ones without leading zeros; empty for a release. */
	private final String[] preRelease;

	/**
	 * @param value the version as written, such as {@code 1.0.0} or {@code v2.1-rc.1}
	 * @throws IllegalArgumentException if {@code value} isn't a version
	 */
	public Version(String value) {
		Matcher m = SHAPE.matcher(value);
		if (!m.matches()) {
			throw new IllegalArgumentException("not a version: \"" + value + "\"; expected numbers separated by dots, such as 1.0.0");
		}
		this.value = value;
		this.release = normalize(m.group(1).split("\\."));
		String pre = m.group(2);
		this.preRelease = pre == null ? NONE : normalize(pre.split("\\."));
	}

	/** Whether this version comes after {@code other}: {@code 1.0.0} is greater than {@code 0.1.0}. */
	public boolean isGreaterThan(Version other) {
		return compareTo(other) > 0;
	}

	/** Whether this version comes before {@code other}: {@code 1.0.0-beta} is less than {@code 1.0.0}. */
	public boolean isLessThan(Version other) {
		return compareTo(other) < 0;
	}

	/** Whether this version is {@code other} or comes after it: the usual "requires 1.2 or newer" check. */
	public boolean isAtLeast(Version other) {
		return compareTo(other) >= 0;
	}

	@Override
	public int compareTo(Version other) {
		int c = compareRelease(release, other.release);
		if (c == 0) {
			c = Integer.compare(release.length, other.release.length);
		}
		return c != 0 ? c : comparePreRelease(preRelease, other.preRelease);
	}

	/** Equal when neither comes before the other: {@code 1.2} equals {@code v01.2}, but not {@code 1.2.0}. */
	@Override
	public boolean equals(Object o) {
		return o instanceof Version other && Arrays.equals(release, other.release) && Arrays.equals(preRelease, other.preRelease);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(release) + Arrays.hashCode(preRelease);
	}

	/** The version as it was written. */
	@Override
	public String toString() {
		return value;
	}

	/** The numbers alone, a missing part counting as zero: {@code 1.2} and {@code 1.2.0} tie here. */
	private static int compareRelease(String[] a, String[] b) {
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int c = compareNumbers(i < a.length ? a[i] : "0", i < b.length ? b[i] : "0");
			if (c != 0) {
				return c;
			}
		}
		return 0;
	}

	/** A release beats any of its pre-releases; otherwise identifiers compare in turn, and a longer list wins a tie. */
	private static int comparePreRelease(String[] a, String[] b) {
		if (a.length == 0 || b.length == 0) {
			return Integer.compare(b.length, a.length);
		}
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			int c = compareIdentifiers(a[i], b[i]);
			if (c != 0) {
				return c;
			}
		}
		return Integer.compare(a.length, b.length);
	}

	/** Numbers by value, words by ASCII, and a number before any word. */
	private static int compareIdentifiers(String a, String b) {
		boolean aNumber = isNumber(a);
		boolean bNumber = isNumber(b);
		if (aNumber && bNumber) {
			return compareNumbers(a, b);
		}
		if (aNumber || bNumber) {
			return aNumber ? -1 : 1;
		}
		return Integer.signum(a.compareTo(b));
	}

	/** Two numbers without leading zeros, of any size: the longer is bigger, and equal lengths compare digit by digit. */
	private static int compareNumbers(String a, String b) {
		int c = Integer.compare(a.length(), b.length());
		return c != 0 ? c : Integer.signum(a.compareTo(b));
	}

	private static String[] normalize(String[] identifiers) {
		for (int i = 0; i < identifiers.length; i++) {
			if (isNumber(identifiers[i])) {
				identifiers[i] = stripLeadingZeros(identifiers[i]);
			}
		}
		return identifiers;
	}

	private static boolean isNumber(String identifier) {
		return identifier.chars().allMatch(ch -> ch >= '0' && ch <= '9');
	}

	private static String stripLeadingZeros(String number) {
		int start = 0;
		while (start < number.length() - 1 && number.charAt(start) == '0') {
			start++;
		}
		return number.substring(start);
	}
}
