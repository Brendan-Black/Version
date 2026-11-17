package black.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Checks Version's ordering and parsing; exits non-zero if any check failed. */
public final class VersionTest {
	private static int failures;
	private static int checks;

	private VersionTest() {}

	public static void main(String[] args) {
		greater("v1.0.0", "0.1.0");
		greater("1.10.0", "1.9.0");
		greater("2", "1.99.99");
		greater("1.0.1", "1.0");
		greater("1.0.0.1", "1.0.0");
		greater("100000000000000000000.0", "99999999999999999999.0");

		equal("1.2", "v1.2");
		equal("01.002", "1.2");
		equal("1.0.0+build.1", "1.0.0+build.2");
		equal("V3.0.0-rc.01", "3.0.0-rc.1");

		// Like significant figures: the numbers first, then the more precise wins, then the pre-release.
		ascending("1.2-beta", "1.2", "1.2.0-beta", "1.2.0", "1.2.0.0", "1.2.1-alpha", "1.2.1");
		ascending("1", "1.0", "1.0.0", "1.0.1");
		ascending("0.9.9.9", "1.0-rc", "1.0", "1.0.0-0");

		// Semantic Versioning's own example, section 11.4.4.
		ascending("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11",
			"1.0.0-rc.1", "1.0.0");
		ascending("0.9.9", "1.0.0-0", "1.0.0-a", "1.0.0", "1.0.1-alpha", "1.0.1");

		check(new Version("1.2.0").isAtLeast(new Version("1.2")), "1.2.0 is at least 1.2");
		check(!new Version("1.2").isAtLeast(new Version("1.2.0")), "1.2 is not at least 1.2.0");
		check(new Version("1.3").isAtLeast(new Version("1.2")), "1.3 is at least 1.2");
		check(!new Version("1.2-rc").isAtLeast(new Version("1.2")), "1.2-rc is not at least 1.2");
		check(new Version("v1.2.0").toString().equals("v1.2.0"), "toString gives back the text as written");

		List<Version> sorted = new ArrayList<>(List.of(new Version("1.10"), new Version("1.2"), new Version("1.9")));
		Collections.sort(sorted);
		check(sorted.toString().equals("[1.2, 1.9, 1.10]"), "sorts by number, not text: " + sorted);

		for (String bad : List.of("", "v", "1.", ".1", "1..2", "1.0.0-", "1.0.0+", "1.0.0-beta..1", "1.a", "x1.0", " 1.0", "1.0 ",
			"1.0.0-é", "vv1")) {
			refused(bad);
		}
		try {
			String nothing = null;
			new Version(nothing);
			check(false, "null is refused");
		} catch (NullPointerException expected) {
			check(true, "null is refused");
		}

		// The contract Comparable asks for, over every pair and triple: what TreeSet, TreeMap and sort rely on.
		List<Version> corpus = new ArrayList<>();
		for (String v : CORPUS) {
			corpus.add(new Version(v));
		}
		laws(corpus);

		// Any starting order sorts into the one order.
		List<Version> ascending = new ArrayList<>();
		for (String v : ASCENDING) {
			ascending.add(new Version(v));
		}
		Random random = new Random(1);
		for (int round = 0; round < 50; round++) {
			List<Version> shuffled = new ArrayList<>(ascending);
			Collections.shuffle(shuffled, random);
			Collections.sort(shuffled);
			check(shuffled.equals(ascending), "a shuffle sorts back into order: " + shuffled);
		}

		flavour();

		System.out.println(checks - failures + "/" + checks + " checks passed");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void greater(String a, String b) {
		Version va = new Version(a);
		Version vb = new Version(b);
		check(va.isGreaterThan(vb), a + " > " + b);
		check(vb.isLessThan(va), b + " < " + a);
		check(!vb.isGreaterThan(va), "not " + b + " > " + a);
		check(!va.equals(vb), a + " != " + b);
	}

	private static void equal(String a, String b) {
		Version va = new Version(a);
		Version vb = new Version(b);
		check(va.compareTo(vb) == 0 && vb.compareTo(va) == 0, a + " compares equal to " + b);
		check(va.equals(vb) && va.hashCode() == vb.hashCode(), a + " equals " + b);
		check(!va.isGreaterThan(vb) && !va.isLessThan(vb), a + " is neither greater nor less than " + b);
	}

	private static void ascending(String... versions) {
		for (int i = 1; i < versions.length; i++) {
			greater(versions[i], versions[i - 1]);
		}
	}

	/** Every version in strictly ascending order, across every rule: numbers, precision, pre-releases. */
	private static final List<String> ASCENDING = List.of("0", "0.0.1", "0.1.0", "0.9.9.9", "1-alpha", "1", "1.0-rc", "1.0",
		"1.0.0-0", "1.0.0-1", "1.0.0-9", "1.0.0-10", "1.0.0-a", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-b",
		"1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.0.0", "1.0.1-alpha", "1.0.1", "1.2-beta", "1.2",
		"1.2.0-beta", "1.2.0", "1.2.1", "1.9", "1.10", "2", "10", "99999999999999999999", "100000000000000000000");

	/** ASCENDING, plus spellings that equal one of them, so the laws see ties as well. */
	private static final List<String> CORPUS = concat(ASCENDING,
		List.of("v1", "V1.0", "01.0", "1.0.0+build", "v1.0.0-beta.02", "0.00.1", "1.2+x", "01.10", "v2"));

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	/**
	 * Comparable's contract: compareTo is antisymmetric and transitive, ties are interchangeable, and equals and hashCode
	 * agree with it.
	 */
	private static void laws(List<Version> versions) {
		for (Version a : versions) {
			check(a.compareTo(a) == 0 && a.equals(a), a + " equals itself");
			check(!a.equals(null), a + " doesn't equal null");
			for (Version b : versions) {
				int ab = Integer.signum(a.compareTo(b));
				check(ab == -Integer.signum(b.compareTo(a)), "antisymmetric: " + a + ", " + b);
				check((ab == 0) == a.equals(b), "equals agrees with compareTo: " + a + ", " + b);
				if (a.equals(b)) {
					check(a.hashCode() == b.hashCode(), "equal versions hash the same: " + a + ", " + b);
				}
				for (Version c : versions) {
					int bc = Integer.signum(b.compareTo(c));
					if (ab == bc && ab != 0) {
						check(Integer.signum(a.compareTo(c)) == ab, "transitive: " + a + ", " + b + ", " + c);
					}
					if (ab == 0) {
						check(Integer.signum(a.compareTo(c)) == bc, "ties are interchangeable: " + a + ", " + b + ", " + c);
					}
				}
			}
		}
	}

	/**
	 * runTests says which artifact it's testing: free must implement nothing but Comparable, and Black black.TypedString as
	 * well. By name, so this compiles without TypedString.
	 */
	private static void flavour() {
		String flavour = System.getProperty("version.flavour", "unset");
		List<String> interfaces = new ArrayList<>();
		for (Class<?> i : Version.class.getInterfaces()) {
			interfaces.add(i.getName());
		}
		List<String> expected = switch (flavour) {
			case "free" -> List.of("java.lang.Comparable");
			case "Black" -> List.of("black.TypedString", "java.lang.Comparable");
			default -> List.of("version.flavour is free or Black, not " + flavour);
		};
		check(interfaces.equals(expected), flavour + " implements " + expected + ", not " + interfaces);
	}

	private static void refused(String text) {
		try {
			new Version(text);
			check(false, "\"" + text + "\" is refused");
		} catch (IllegalArgumentException expected) {
			String message = String.valueOf(expected.getMessage());
			check(message.startsWith("not a version: \"" + text + "\""), "the message names what was refused: " + message);
		}
	}


	private static void check(boolean passed, String what) {
		checks++;
		if (!passed) {
			failures++;
			System.out.println("FAIL: " + what);
		}
	}
}
