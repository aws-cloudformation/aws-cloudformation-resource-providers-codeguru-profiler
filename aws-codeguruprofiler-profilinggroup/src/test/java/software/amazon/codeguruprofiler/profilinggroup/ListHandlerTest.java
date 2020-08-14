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

    @BeforeEach
    public void setup() {
        doReturn(singletonList(testPrincipalArn)).when(getPrincipalsFunction).apply(eq(proxy), any());
    }

    @Test
    public void testSuccessState() {
        final String arn = "arn:aws:codeguru-profiler:us-east-1:000000000000:profilingGroup/IronMan-Suit-34";
        final Channel testChannel = Channel.builder().id("channelId").uri("channelUri").build();

        final ProfilingGroupDescription profilingGroupDescription = ProfilingGroupDescription.builder()
                        .name("IronMan-Suit-34")
                        .computePlatform("Default")
                        .arn(arn)
                        .tags(new HashMap<String, String>() {{ put("superhero", "blackWidow"); }})
                        .build();

        doReturn(ListProfilingGroupsResponse.builder()
                .profilingGroups(profilingGroupDescription)
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
                   .profilingGroupName(profilingGroupDescription.name())
                   .build()), any());

        request.setNextToken("page2");
        final ProgressEvent<ResourceModel, CallbackContext> response = subject.handleRequest(proxy, request, null, logger);

        final ResourceModel expectedModel = ResourceModel.builder()
                .profilingGroupName(profilingGroupDescription.name())
                .computePlatform("Default")
                .arn(profilingGroupDescription.arn())
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
                .tags(new ArrayList<>(TagHelper.convertTagMapIntoSet(profilingGroupDescription.tags())))
                .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).containsExactly(expectedModel);
        assertThat(response.getNextToken()).isEqualTo("page3");
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
