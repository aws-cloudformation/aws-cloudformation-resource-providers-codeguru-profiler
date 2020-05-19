package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.CreateProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.CreateProfilingGroupResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ProfilingGroupDescription;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionResponse;
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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeInvalidRequest;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeRequest;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeValidRequest;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

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
    public void testSuccessStateNoPermissions() {
        String pgName = "IronMan-Suit-34";
        String clientToken = "clientTokenXXX";

        // Use Mockito's lenient feature to allow stubbed method to be invoked with different arguments.
        lenient().doReturn(CreateProfilingGroupResponse.builder()
                .profilingGroup(ProfilingGroupDescription.builder()
                        .name(pgName)
                        .build())
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                    ArgumentMatchers.eq(CreateProfilingGroupRequest.builder()
                        .profilingGroupName(pgName).clientToken(clientToken)
                        .build()), any());

        lenient().doReturn(PutPermissionResponse.builder()
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                    ArgumentMatchers.eq(PutPermissionRequest.builder()
                        .build()), any());

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
    public void testSuccessStatePermissions() {
        // Use Mockito's lenient feature to allow stubbed method to be invoked with different arguments.
        lenient().doReturn(CreateProfilingGroupResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(
                ArgumentMatchers.eq(CreateProfilingGroupRequest.builder().build()), any());

        lenient().doReturn(PutPermissionResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(
                ArgumentMatchers.eq(PutPermissionRequest.builder().build()), any());

        CreateHandler handler = new CreateHandler();

        assertThat(handler.handleRequest(proxy, newRequestWithPermissions(null), null, logger)).isNotNull();

        Permissions noAgentPermissions = Permissions.builder().agentPermissions(null).build();
        assertThat(handler.handleRequest(proxy, newRequestWithPermissions(noAgentPermissions), null, logger)).isNotNull();

        Permissions agentPermissions = Permissions.builder().agentPermissions(
                AgentPermissions.builder().principals(Arrays.asList("a", "bc")).build())
                .build();
        assertThat(handler.handleRequest(proxy, newRequestWithPermissions(agentPermissions), null, logger)).isNotNull();
    }

    private static ResourceHandlerRequest<ResourceModel> newRequestWithPermissions(final Permissions permissions) {
        return makeRequest(newResourceModel(permissions));
    }

    private static ResourceModel newResourceModel(final Permissions permissions) {
        return  ResourceModel.builder().permissions(permissions).build();
    }

    @Test
    public void testConflictException() {
        doThrow(ConflictException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnAlreadyExistsException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testInternalServerException() {
        doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testServiceQuotaExceededException() {
        doThrow(ServiceQuotaExceededException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceLimitExceededException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testThrottlingException() {
        doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnThrottlingException.class, () ->
                new CreateHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testValidationException() {
        doThrow(ValidationException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInvalidRequestException.class, () ->
                new CreateHandler().handleRequest(proxy, makeInvalidRequest(), null, logger));
    }
}
