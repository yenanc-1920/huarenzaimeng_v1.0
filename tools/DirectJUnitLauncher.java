import java.io.PrintWriter;
import java.util.Arrays;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class DirectJUnitLauncher {
    private DirectJUnitLauncher() {}

    public static void main(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("TEST_CLASS_REQUIRED");
        var selectors = Arrays.stream(args).map(DiscoverySelectors::selectClass).toList();
        var request = LauncherDiscoveryRequestBuilder.request().selectors(selectors).build();
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        summary.printFailuresTo(new PrintWriter(System.out, true));
        System.out.println("DIRECT_JUNIT tests=" + summary.getTestsFoundCount()
                + " succeeded=" + summary.getTestsSucceededCount()
                + " failed=" + summary.getTestsFailedCount()
                + " skipped=" + summary.getTestsSkippedCount());
        if (summary.getTestsFailedCount() != 0 || summary.getTestsSucceededCount() == 0) System.exit(1);
    }
}
