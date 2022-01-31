package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.codeguruprofiler.model.Channel;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ListProfilingGroupsRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ListProfilingGroupsResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.NotificationConfiguration;
import software.amazon.awssdk.services.codeguruprofiler.model.ProfilingGroupDescription;
import software.amazon.awssdk.services.codeguruprofiler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.codeguruprofiler.profilinggroup.AgentPermissionHelper.GetPrincipalsFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeValidRequest;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest {

    @Mock
    private final AmazonWebServicesClientProxy proxy = mock(AmazonWebServicesClientProxy.class);

    @Mock
    private final Logger logger = mock(Logger.class);

    private final ResourceHandlerRequest<ResourceModel> request = makeValidRequest();

    @SuppressWarnings("unchecked")
    private final GetPrincipalsFunction<AmazonWebServicesClientProxy, String, List<String>> getPrincipalsFunction = mock(GetPrincipalsFunction.class);

    private final ListHandler subject = new ListHandler(getPrincipalsFunction);

    private final String testPrincipalArn = "arn:aws:iam:012345678901:user/User";

    private final ProfilingGroupDescription pgDescription1 = ProfilingGroupDescription.builder()
        .name("test-pg-1")
        .computePlatform("Default")
        .arn("arn:aws:codeguru-profiler:us-east-1:000000000000:profilingGroup/test-pg-1")
        .tags(new HashMap<String, String>() {{ put("counter", "1"); }})
        .build();

    private final ProfilingGroupDescription pgDescription2 = ProfilingGroupDescription.builder()
        .name("test-pg-2")
        .computePlatform("Lambda")
        .arn("arn:aws:codeguru-profiler:us-east-1:000000000000:profilingGroup/test-pg-2")
        .tags(new HashMap<String, String>() {{ put("counter", "2"); }})
        .build();

    private final Channel testChannel = Channel.builder().id("channelId").uri("channelUri").build();

    final ResourceModel resourceModelPg1 = ResourceModel.builder()
        .profilingGroupName(pgDescription1.name())
        .computePlatform("Default")
        .arn(pgDescription1.arn())
        .anomalyDetectionNotificationConfiguration(
            singletonList(
                software.amazon.codeguruprofiler.profilinggroup.Channel
                    .builder()
                    .channelId(testChannel.id())
                    .channelUri(testChannel.uri())
                    .build()
            )
        )
        .agentPermissions(AgentPermissions.builder().principals(singletonList(testPrincipalArn)).build())
        .tags(new ArrayList<>(TagHelper.convertTagMapIntoSet(pgDescription1.tags())))
        .build();

    @BeforeEach
    public void setup() {
        doReturn(singletonList(testPrincipalArn)).when(getPrincipalsFunction).apply(eq(proxy), any());
    }

    @Test
    public void testSuccessState() {
        doReturn(ListProfilingGroupsResponse.builder()
                .profilingGroups(pgDescription1)
                .nextToken("page3")
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                        ArgumentMatchers.eq(ListProfilingGroupsRequest
                                .builder()
                                .nextToken("page2")
                                .maxResults(100)
                                .includeDescription(true)
                                .build()),
                        any());

        doReturn(
            GetNotificationConfigurationResponse.builder()
                .notificationConfiguration(NotificationConfiguration.builder().channels(testChannel).build())
                .build()
        )
            .when(proxy).injectCredentialsAndInvokeV2(
            eq(GetNotificationConfigurationRequest
                   .builder()
                   .profilingGroupName(pgDescription1.name())
                   .build()), any());

        request.setNextToken("page2");
        final ProgressEvent<ResourceModel, CallbackContext> response = subject.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).containsExactly(resourceModelPg1);
        assertThat(response.getNextToken()).isEqualTo("page3");
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testProfilingGroupDeletedDuringRequest() {
        doReturn(ListProfilingGroupsResponse.builder()
                .profilingGroups(pgDescription1, pgDescription2)
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                        ArgumentMatchers.eq(ListProfilingGroupsRequest
                        .builder()
                        .maxResults(100)
                        .includeDescription(true)
                        .build()),
                any());

        doReturn(
            GetNotificationConfigurationResponse.builder()
                .notificationConfiguration(NotificationConfiguration.builder().channels(testChannel).build())
                .build()
        )
            .when(proxy).injectCredentialsAndInvokeV2(
            eq(GetNotificationConfigurationRequest
                .builder()
                .profilingGroupName(pgDescription1.name())
                .build()), any());

        // When fetching the notification configuration for pg2, throw a resource-not-found exception to simulate
        // it having been deleted after we called listProfilingGroups. The handler should then safely handle the
        // exception and omit this pg from the response.
        doThrow(ResourceNotFoundException.class).when(proxy).injectCredentialsAndInvokeV2(
            eq(GetNotificationConfigurationRequest
                .builder()
                .profilingGroupName(pgDescription2.name())
                .build()), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = subject.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).containsExactly(resourceModelPg1);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testInternalServerException() {
        doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class, () -> subject.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testThrottlingException() {
        doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnThrottlingException.class, () -> subject.handleRequest(proxy, request, null, logger));
    }
}
