package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.CreateProfilingGroupResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ProfilingGroupDescription;
import software.amazon.awssdk.services.codeguruprofiler.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    private final ResourceModel validResourceModel = ResourceModel.builder()
            .profilingGroupName("IronMan-Suite-34")
            .build();

    private final ResourceModel invalidResourceModel = ResourceModel.builder()
            .build();

    @Test
    public void testSuccessState() {
        final ResourceHandlerRequest<ResourceModel> request = makeRequest(validResourceModel);

        doReturn(CreateProfilingGroupResponse.builder()
                .profilingGroup(ProfilingGroupDescription.builder()
                        .name("IronMan-Suite-34")
                        .build())
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = new CreateHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testConflictException() {
        final ResourceHandlerRequest<ResourceModel> request = makeRequest(validResourceModel);

        doThrow(ConflictException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnAlreadyExistsException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testInternalServerException() {
        final ResourceHandlerRequest<ResourceModel> request = makeRequest(validResourceModel);

        doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testServiceQuotaExceededException() {
        final ResourceHandlerRequest<ResourceModel> request = makeRequest(validResourceModel);

        doThrow(ServiceQuotaExceededException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceLimitExceededException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testThrottlingException() {
        final ResourceHandlerRequest<ResourceModel> request = makeRequest(validResourceModel);

        doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnThrottlingException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testValidationException() {
        final ResourceHandlerRequest<ResourceModel> request = makeRequest(invalidResourceModel);

        doThrow(ValidationException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInvalidRequestException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    private ResourceHandlerRequest<ResourceModel> makeRequest(ResourceModel model) {
        return ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
    }
}
