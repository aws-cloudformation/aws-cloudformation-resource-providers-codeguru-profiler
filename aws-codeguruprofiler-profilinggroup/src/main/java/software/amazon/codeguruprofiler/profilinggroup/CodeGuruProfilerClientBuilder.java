package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;

import java.time.Duration;

public class CodeGuruProfilerClientBuilder {
    // Following what we do at https://tiny.amazon.com/c1ma38jp/codeamazpackSkySblobmainsrc

    /**
     * We use an equal-jitter exponential backoff strategy, with a base delay of 100 ms. And, the max backoff time is
     * set to 1 s. So, the retry pattern would be like this: 50 - 100 ms, 100 - 200 ms, 200 - 400 ms, 400 - 800 ms, 500
     * - 1000 ms, 500 - 1000 ms ... until the request succeeds or until the overall execution timeout or until we run
     * out of retries.
     *
     * The default in the SDK is a full-jitter backoff strategy in which the minimum backoff delay is 0ms (allows instant retries),
     * and uses a max backoff of 20 seconds.
     */
    private static final Duration BASE_DELAY_MS = Duration.ofMillis(100); // Default: 100ms
    private static final Duration MAX_BACKOFF_MS = Duration.ofMillis(1000); // Default: 20 seconds

    /**
     * Setting this to the max retries supported by the SDK. Note that we would timeout well before we do these many
     * retries since we are bound by the overall request timeout.
     */
    private static final int MAX_ERROR_RETRY = 30; // Default: 3 (for most services)

    /**
     * See https://tiny.amazon.com/vlswgwgb/codeamazpackSkySbloba96fsrc
     * for more details on the individual call timeouts.
     */
    private static final Duration OVERALL_TIMEOUT = Duration.ofMillis(10000); // We can handle more here compared to our API.
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofMillis(500);

    /**
     * Maximum amount of time that the client waits for the underlying HTTP client to establish a TCP connection.
     * We want connection issues to time out quickly so that they can be retried like other failures. We can rely
     * on fast network since our calls are intra-region.
     */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofMillis(500); // Default: 10 seconds

    /**
     * The maximum amount of time that the HTTP client waits to read data from an already-established TCP connection.
     * This is the time between when an HTTP POST ends and the entire response of the request is received, and it
     * includes the service and network round-trip times.
     *
     * The general recommendation is to set this value a little higher than the ATTEMPT_TIMEOUT setting if they are used together.
     */
    private static final Duration SOCKET_TIMEOUT = Duration.ofMillis(600); // Default: 30s


    private static RetryPolicy getRetryPolicy() {
        BackoffStrategy failureBackoffStrategy = EqualJitterBackoffStrategy.builder()
                .baseDelay(BASE_DELAY_MS)
                .maxBackoffTime(MAX_BACKOFF_MS)
                .build();
        BackoffStrategy throttlingBackoffStrategy = EqualJitterBackoffStrategy.builder()
                .baseDelay(SdkDefaultRetrySetting.THROTTLED_BASE_DELAY) // 500ms
                .maxBackoffTime(MAX_BACKOFF_MS)
                .build();

        return RetryPolicy.defaultRetryPolicy().toBuilder()
                .backoffStrategy(failureBackoffStrategy)
                .throttlingBackoffStrategy(throttlingBackoffStrategy)
                .numRetries(MAX_ERROR_RETRY) // We can be a bit slower in CloudFormation for the sake of not failing the deployment!
                .build();
    }

    private static SdkHttpClient getHttpClient() {
        return ApacheHttpClient.builder()
                .connectionTimeout(CONNECTION_TIMEOUT)
                .socketTimeout(SOCKET_TIMEOUT)
                .build();
    }

    public static CodeGuruProfilerClient create() {
        return CodeGuruProfilerClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(getRetryPolicy())
                        .apiCallTimeout(OVERALL_TIMEOUT)
                        .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
                        .build())
                .httpClient(getHttpClient())
                .build();
    }
}
