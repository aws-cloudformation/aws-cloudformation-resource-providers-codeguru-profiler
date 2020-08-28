package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.conditions.OrRetryCondition;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;

import java.time.Duration;

public class CodeGuruProfilerClientBuilder {
    // We can be a bit slower in CloudFormation for the sake of not failing the deployment.
    private static final Duration OVERALL_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(30);
    // Note that we would timeout well before we do these many retries since we are bound by the overall request timeout.
    private static final int MAX_ERROR_RETRY = 30;

    private static RetryPolicy getRetryPolicy() {
        return RetryPolicy.defaultRetryPolicy().toBuilder()
                .numRetries(MAX_ERROR_RETRY)
                .retryCondition(getRetryCondition())
                .build();
    }

    private static RetryCondition getRetryCondition() {
        return OrRetryCondition.create(
                RetryCondition.defaultRetryCondition(), // Pull in SDK defaults
                shouldRetryAbortedException() // https://github.com/aws/aws-sdk-java-v2/issues/1684
        );
    }

    private static RetryCondition shouldRetryAbortedException() {
        return (RetryPolicyContext c) ->
                   c.exception().getCause() != null &&
                       c.exception().getCause().getClass().equals(AbortedException.class);
    }

    static ClientOverrideConfiguration getClientConfiguration() {
        return ClientOverrideConfiguration.builder()
                   .retryPolicy(getRetryPolicy())
                   .apiCallTimeout(OVERALL_TIMEOUT)
                   .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
                   .build();
    }

    public static CodeGuruProfilerClient create() {
        return CodeGuruProfilerClient.builder()
                .overrideConfiguration(getClientConfiguration())
                .build();
    }
}
