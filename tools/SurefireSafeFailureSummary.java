import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.helpers.DefaultHandler;

/** Emits a bounded structural summary; captured test output and raw reports are never emitted. */
public final class SurefireSafeFailureSummary {
    private static final long MAX_XML_BYTES = 5L * 1024 * 1024;
    private static final int MAX_REPORTS = 128;
    private static final int MAX_FAILURE_ITEMS = 32;
    private static final int MAX_FRAMES_PER_FAILURE = 8;
    private static final int MAX_OUTPUT_LINES = 320;
    private static final int MAX_OUTPUT_BYTES = 32 * 1024;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_$.-]{1,240}");
    private static final Pattern SAFE_FRAME = Pattern.compile(
            "\\s*at [A-Za-z0-9_.$]+\\([A-Za-z0-9_$.-]+\\.java:[0-9]{1,7}\\)\\s*");

    public static void main(String[] args) {
        try {
            Summary summary = summarize(args);
            System.out.print(summary.rendered());
        } catch (SafeFailure failure) {
            System.out.println("SAFE_SUREFIRE_SUMMARY_ERROR " + failure.code);
            System.exit(2);
        } catch (Throwable failure) {
            System.out.println("SAFE_SUREFIRE_SUMMARY_ERROR INTERNAL_FAILURE");
            System.exit(2);
        }
    }

    private static Summary summarize(String[] args) {
        List<Path> reports = discover(args);
        DocumentBuilderFactory factory = secureFactory();
        BoundedOutput output = new BoundedOutput();
        int suites = 0, tests = 0, failures = 0, errors = 0, skipped = 0, failureItems = 0;
        output.add("SAFE_SUREFIRE_FAILURE_SUMMARY_BEGIN");
        for (Path report : reports) {
            Element suite = parse(factory, report);
            suites++;
            tests = checkedAdd(tests, safeInt(suite.getAttribute("tests")));
            failures = checkedAdd(failures, safeInt(suite.getAttribute("failures")));
            errors = checkedAdd(errors, safeInt(suite.getAttribute("errors")));
            skipped = checkedAdd(skipped, safeInt(suite.getAttribute("skipped")));
            var cases = suite.getElementsByTagName("testcase");
            for (int index = 0; index < cases.getLength(); index++) {
                Element testCase = (Element) cases.item(index);
                for (Node child = testCase.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element detail
                            && ("failure".equals(detail.getTagName()) || "error".equals(detail.getTagName()))) {
                        if (++failureItems > MAX_FAILURE_ITEMS) throw fail("FAILURE_COUNT_LIMIT");
                        addFailure(output, testCase, detail);
                    }
                }
            }
        }
        if (failureItems != checkedAdd(failures, errors)) throw fail("FAILURE_COUNT_MISMATCH");
        output.add("SUMMARY suites=" + suites + " tests=" + tests + " failures=" + failures
                + " errors=" + errors + " skipped=" + skipped);
        output.add("SAFE_SUREFIRE_FAILURE_SUMMARY_END");
        return new Summary(output.render());
    }

    private static List<Path> discover(String[] args) {
        if (args == null || args.length == 0) throw fail("REPORTS_MISSING");
        List<Path> reports = new ArrayList<>();
        for (String argument : args) {
            Path directory;
            try { directory = Path.of(argument).toAbsolutePath().normalize(); }
            catch (RuntimeException invalid) { throw fail("REPORT_DIRECTORY_INVALID"); }
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(directory)) {
                throw fail("REPORT_DIRECTORY_UNREADABLE");
            }
            try (var files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().startsWith("TEST-")
                                && path.getFileName().toString().endsWith(".xml"))
                        .sorted(Comparator.comparing(Path::toString)).forEach(path -> {
                            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
                                throw fail("REPORT_UNREADABLE");
                            }
                            long size;
                            try { size = Files.size(path); }
                            catch (Exception unavailable) { throw fail("REPORT_UNREADABLE"); }
                            if (size > MAX_XML_BYTES) throw fail("REPORT_SIZE_LIMIT");
                            if (reports.size() == MAX_REPORTS) throw fail("REPORT_COUNT_LIMIT");
                            reports.add(path);
                        });
            } catch (SafeFailure failure) {
                throw failure;
            } catch (Exception unavailable) {
                throw fail("REPORT_DIRECTORY_UNREADABLE");
            }
        }
        if (reports.isEmpty()) throw fail("REPORTS_MISSING");
        reports.sort(Comparator.comparing(Path::toString));
        return reports;
    }

    private static Element parse(DocumentBuilderFactory factory, Path report) {
        try (InputStream input = Files.newInputStream(report, LinkOption.NOFOLLOW_LINKS)) {
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element suite = builder.parse(input).getDocumentElement();
            if (!"testsuite".equals(suite.getTagName())) throw fail("REPORT_ROOT_INVALID");
            return suite;
        } catch (SafeFailure failure) {
            throw failure;
        } catch (Exception malformed) {
            throw fail("REPORT_MALFORMED");
        }
    }

    private static void addFailure(BoundedOutput output, Element testCase, Element detail) {
        output.add("FAILED class=" + safeId(testCase.getAttribute("classname"))
                + " method=" + safeId(testCase.getAttribute("name"))
                + " kind=" + detail.getTagName().toUpperCase()
                + " type=" + safeId(detail.getAttribute("type")));
        int frames = 0;
        for (String line : detail.getTextContent().split("\\R")) {
            if (frames == MAX_FRAMES_PER_FAILURE) break;
            if (SAFE_FRAME.matcher(line).matches()) {
                output.add("FRAME " + line.strip());
                frames++;
            }
        }
    }

    private static DocumentBuilderFactory secureFactory() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory;
        } catch (Exception unavailable) {
            throw fail("XML_SECURITY_UNAVAILABLE");
        }
    }

    private static String safeId(String value) {
        return SAFE_ID.matcher(value).matches() ? value : "UNSAFE_IDENTIFIER_REDACTED";
    }

    private static int safeInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw fail("REPORT_COUNT_INVALID");
            return parsed;
        } catch (SafeFailure failure) {
            throw failure;
        } catch (RuntimeException invalid) {
            throw fail("REPORT_COUNT_INVALID");
        }
    }

    private static int checkedAdd(int left, int right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException overflow) { throw fail("REPORT_COUNT_INVALID"); }
    }

    private static SafeFailure fail(String code) { return new SafeFailure(code); }

    private record Summary(String rendered) { }

    private static final class BoundedOutput {
        private final List<String> lines = new ArrayList<>();
        private int bytes;
        void add(String line) {
            int added = line.getBytes(StandardCharsets.UTF_8).length + 1;
            if (lines.size() == MAX_OUTPUT_LINES || bytes + added > MAX_OUTPUT_BYTES) {
                throw fail("OUTPUT_LIMIT");
            }
            lines.add(line);
            bytes += added;
        }
        String render() { return String.join("\n", lines) + "\n"; }
    }

    private static final class SafeFailure extends RuntimeException {
        private final String code;
        private SafeFailure(String code) { super(null, null, false, false); this.code = code; }
    }
}
