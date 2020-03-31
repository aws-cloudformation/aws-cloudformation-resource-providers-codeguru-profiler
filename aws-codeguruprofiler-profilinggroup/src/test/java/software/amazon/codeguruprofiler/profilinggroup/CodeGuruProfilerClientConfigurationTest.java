package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CodeGuruProfilerClientConfigurationTest {

    @Nested
    class DescribeRetryCondition {
        @Test
        public void itShouldNotRetryClientErrorsByDefault() {
            assertThat(getRetryCondition().shouldRetry(createRetryContext(SdkClientException.create("test")))).isFalse();
        }

        @Test
        public void itShouldRetryClientErrorsCausedByAbortedException() {
            // Until https://github.com/aws/aws-sdk-java-v2/issues/1684 is fixed
            SdkClientException clientException =
                SdkClientException.builder()
                    .message("test")
                    .cause(AbortedException.create("test aborted exception"))
                    .build();

            assertThat(getRetryCondition().shouldRetry(createRetryContext(clientException))).isTrue();
        }
    }

    private RetryCondition getRetryCondition() {
        return getClientConfiguration()
                   .retryPolicy().get()
                   .retryCondition();
    }

    private ClientOverrideConfiguration getClientConfiguration() {
        return CodeGuruProfilerClientBuilder.getClientConfiguration();
    }

    private RetryPolicyContext createRetryContext(SdkException exception) {
        return RetryPolicyContext.builder()
                   .exception(exception)
                   .build();
    }
}
