package software.amazon.codeguruprofiler.profilinggroup;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.Channel;
import software.amazon.awssdk.services.codeguruprofiler.model.DeleteProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.DeleteProfilingGroupResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.EventPublisher;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.NotificationConfiguration;
import software.amazon.awssdk.services.codeguruprofiler.model.RemoveNotificationChannelRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.RemoveNotificationChannelResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeInvalidRequest;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeValidRequest;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private ResourceHandlerRequest<ResourceModel> request;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);

        request = makeValidRequest();
    }

    @Test
    public void testSuccessState() {
        doReturn(DeleteProfilingGroupResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(
                ArgumentMatchers.eq(DeleteProfilingGroupRequest
                        .builder()
                        .profilingGroupName("IronMan-Suit-34")
                        .build()), any());

        doReturn(GetNotificationConfigurationResponse.builder()
                .notificationConfiguration(NotificationConfiguration.builder()
                        .channels(Channel.builder().uri("arn:aws:sns:us-east-1:177364881114:SampleTopic")
                                .eventPublishers(ImmutableSet.of(EventPublisher.ANOMALY_DETECTION))
                                .build())
                        .build())
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                ArgumentMatchers.eq(GetNotificationConfigurationRequest
                        .builder()
                        .profilingGroupName("IronMan-Suit-34")
                        .build()), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = new DeleteHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testNotFoundException() {
        doThrow(ResourceNotFoundException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnNotFoundException.class, () ->
                new DeleteHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testInternalServerException() {
        doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class, () ->
                new DeleteHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testThrottlingException() {
        doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnThrottlingException.class, () ->
                new DeleteHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testValidationException() {
        doThrow(ValidationException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInvalidRequestException.class, () ->
                new DeleteHandler().handleRequest(proxy, makeInvalidRequest(), null, logger));
    }
}
