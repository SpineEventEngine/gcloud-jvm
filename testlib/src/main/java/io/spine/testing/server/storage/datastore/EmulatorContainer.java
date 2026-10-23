package io.spine.testing.server.storage.datastore;

import com.google.cloud.NoCredentials;
import com.google.cloud.ServiceOptions;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.server.storage.datastore.ProjectId;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.gcloud.DatastoreEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

import static java.util.Objects.requireNonNull;

/**
 * A container running the Emulator image.
 *
 * <p>The container is started in its "full consistency" mode, keeping the data in memory only.
 *
 * <p>The output of the emulator is forwarded to the log of the tests, so that a failure
 * of the emulator itself is visible next to the tests it breaks.
 */
final class EmulatorContainer extends DatastoreEmulatorContainer {

    /**
     * A template of BASH command to execute upon Docker container start.
     */
    @SuppressWarnings("InlineFormatString" /* To make the parameters visible. */)
    private static final String TEMPLATE =
            "gcloud beta emulators datastore start " +
                    "--project %s " +
                    "--host-port 0.0.0.0:%d " +
                    "--consistency 1.0 " +
                    "--no-store-on-disk";

    /**
     * Options of the JVM running the emulator inside the container.
     *
     * <p>By default, a JVM keeps running after an {@code OutOfMemoryError}, which may leave
     * the emulator accepting connections without answering them. Every call to it then waits
     * until its deadline. Exiting on such an error stops the container instead, so that
     * the calls fail fast and the emulator output tells why.
     */
    private static final String JVM_OPTIONS = "-XX:+ExitOnOutOfMemoryError";

    private final ProjectId projectId;
    private final int port;

    /**
     * Datastore options of the running emulator.
     *
     * <p>Initialized upon the invocation of {@link #startAndServe() startAndServe()}.
     */
    private @MonotonicNonNull DatastoreOptions options;

    /**
     * Creates a new Emulator container.
     *
     * <p>Remember, the instance must be {@linkplain #startAndServe() started} after creation.
     *
     * @param name      name of the Docker image
     * @param projectId the GCP project ID, may NOT point to the real project in GCP
     * @param port      port to start the emulator on
     */
    EmulatorContainer(DockerImageName name, ProjectId projectId, int port) {
        super(name);
        this.projectId = projectId;
        this.port = port;
    }

    @Override
    @SuppressWarnings("resource" /* We don't care, as this is a test-only utility. */)
    protected void configure() {
        var command = String.format(TEMPLATE, projectId.value(), port);
        withCommand("/bin/sh", "-c", command);
        withEnv("JAVA_TOOL_OPTIONS", JVM_OPTIONS);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(EmulatorContainer.class)));
    }

    @Override
    public String getEmulatorEndpoint() {
        return getHost() + ':' + getMappedPort(port);
    }

    @Override
    public String getProjectId() {
        return projectId.value();
    }

    /**
     * Starts the container and returns the connection options for enclosed {@code Datastore}.
     */
    @CanIgnoreReturnValue
    DatastoreOptions startAndServe() {
        start();
        options = DatastoreOptions.newBuilder()
                .setHost(getEmulatorEndpoint())
                .setCredentials(NoCredentials.getInstance())
                .setRetrySettings(ServiceOptions.getNoRetrySettings())
                .setProjectId(getProjectId())
                .build();
        return options;
    }

    /**
     * Starts the container.
     *
     * @deprecated Use {@link #startAndServe() startAndServe()} instead.
     */
    @Override
    @Deprecated
    public void start() {
        super.start();
    }

    /**
     * Once the container is started, returns the connection options for {@code Datastore} emulator.
     */
    DatastoreOptions options() {
        requireNonNull(options,
                "`DatastoreOptions` are not available until the emulator is started.");
        return options;
    }
}
