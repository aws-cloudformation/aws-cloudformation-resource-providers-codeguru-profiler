package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup;
import software.amazon.awssdk.services.codeguruprofiler.model.CodeGuruProfilerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.CreateProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.DeleteProfilingGroupRequest;
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
import java.util.Optional;

import static java.lang.String.format;

public class CreateHandler extends BaseHandler<CallbackContext> {

    private final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final String awsAccountId = request.getAwsAccountId();
        final ResourceModel model = request.getDesiredResourceState();
        final String pgName = model.getProfilingGroupName();

        CreateProfilingGroupRequest createProfilingGroupRequest = CreateProfilingGroupRequest.builder()
                .profilingGroupName(pgName)
                .clientToken(request.getClientRequestToken())
                .build();
        try {
            proxy.injectCredentialsAndInvokeV2(createProfilingGroupRequest, profilerClient::createProfilingGroup);
        } catch (CodeGuruProfilerException e) {
            wrapException(e);
        }
        logger.log(format("%s [%s] for accountId [%s] has been successfully created!", ResourceModel.TYPE_NAME, pgName, awsAccountId));

        Optional<List<String>> principals = principalsForAgentPermissionsFrom(model);
        if (principals.isPresent()) {
            putAgentPermissions(proxy, pgName, principals.get());
            logger.log(format("%s [%s] for accountId [%s] has been successfully updated with agent permissions!",
                    ResourceModel.TYPE_NAME, pgName, awsAccountId));
        }

        return ProgressEvent.defaultSuccessHandler(model);
    }

    private void putAgentPermissions(final AmazonWebServicesClientProxy proxy, final String pgName, final List<String> principals) {
        PutPermissionRequest putPermissionRequest = PutPermissionRequest.builder()
                .profilingGroupName(pgName)
                .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                .principals(principals)
                .build();
        try {
            proxy.injectCredentialsAndInvokeV2(putPermissionRequest, profilerClient::putPermission);
        } catch (CodeGuruProfilerException e) {
            try {
                DeleteProfilingGroupRequest deletePgRequest = DeleteProfilingGroupRequest.builder().profilingGroupName(pgName).build();
                proxy.injectCredentialsAndInvokeV2(deletePgRequest, profilerClient::deleteProfilingGroup);
            } catch (Throwable deleteException) {
                e.addSuppressed(deleteException);
            } finally {
                wrapException(e);
            }
        }
    }

    private static void wrapException(final CodeGuruProfilerException e) {
        if (e instanceof ConflictException) {
            throw new CfnAlreadyExistsException(e);
        }
        if (e instanceof InternalServerException) {
            throw new CfnServiceInternalErrorException(e);
        }
        if (e instanceof ServiceQuotaExceededException) {
            throw new CfnServiceLimitExceededException(e);
        }
        if (e instanceof ThrottlingException) {
            throw new CfnThrottlingException(e);
        }
        if (e instanceof ValidationException) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME + e.getMessage(), e);
        }
        throw e;
    }

    private static Optional<List<String>> principalsForAgentPermissionsFrom(final ResourceModel model) {
        if (model.getAgentPermissions() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.getAgentPermissions().getPrincipals());
    }
}
