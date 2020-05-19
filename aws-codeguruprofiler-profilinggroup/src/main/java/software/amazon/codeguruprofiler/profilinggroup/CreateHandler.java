package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.CreateProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionRequest;
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
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;

import static java.util.Collections.emptyList;

public class CreateHandler extends BaseHandler<CallbackContext> {

    private final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        final String awsAccountId = request.getAwsAccountId();
        final String idempotencyToken = request.getClientRequestToken();

        try {
            CreateProfilingGroupRequest createProfilingGroupRequest = CreateProfilingGroupRequest.builder()
                    .profilingGroupName(model.getProfilingGroupName())
                    .clientToken(idempotencyToken)
                    .build();

            proxy.injectCredentialsAndInvokeV2(createProfilingGroupRequest, profilerClient::createProfilingGroup);

            PutPermissionRequest putPermissionRequest = PutPermissionRequest.builder()
                    .profilingGroupName(model.getProfilingGroupName())
                    .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                    .principals(extractPrincipalsForAgentPermissions(model))
                    .build();

            proxy.injectCredentialsAndInvokeV2(putPermissionRequest, profilerClient::putPermission);

            logger.log(String.format("%s [%s] for accountId [%s] has been successfully created!", ResourceModel.TYPE_NAME, model.getProfilingGroupName(), awsAccountId));

            return ProgressEvent.defaultSuccessHandler(model);
        } catch (ConflictException e) {
            throw new CfnAlreadyExistsException(e);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (ServiceQuotaExceededException e) {
            throw new CfnServiceLimitExceededException(e);
        } catch (ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME + e.getMessage(), e);
        }
    }

    private static List<String> extractPrincipalsForAgentPermissions(final ResourceModel model) {
        if (model.getPermissions() == null) {
            return emptyList();
        }
        if (model.getPermissions().getAgentPermissions() == null) {
            return emptyList();
        }
        return model.getPermissions().getAgentPermissions().getPrincipals();
    }
}
