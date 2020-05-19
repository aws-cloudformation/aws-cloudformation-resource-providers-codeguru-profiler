package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.RemovePermissionRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;

public class UpdateHandler extends BaseHandler<CallbackContext> {

    private final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        AmazonWebServicesClientProxy proxy,
        ResourceHandlerRequest<ResourceModel> request,
        CallbackContext callbackContext,
        Logger logger) {
        final ResourceModel model = request.getDesiredResourceState();

        final String awsAccountId = request.getAwsAccountId();
        final String profilingGroupName = model.getProfilingGroupName();

        Permissions newPermissions = model.getPermissions();

        try {
            GetPolicyResponse getPolicyResponse = getExistingPolicy(proxy, profilingGroupName);

            if (newPermissions != null) {
                List<String> principals = newPermissions.getAgentPermissions().getPrincipals();
                putAgentPermissions(proxy, profilingGroupName, principals, getPolicyResponse.revisionId());
                logger.log(
                    String.format("Policy for [%s] for accountId [%s] has been successfully updated! actionGroup: %s, principals: %s",
                        profilingGroupName,
                        awsAccountId,
                        ActionGroup.AGENT_PERMISSIONS,
                        principals)
                );
            } else if(getPolicyResponse.policy() != null) {
                removeAgentPermission(proxy, profilingGroupName, getPolicyResponse.revisionId());
                logger.log(String.format("Policy for [%s] for accountId [%s] has been successfully removed!", profilingGroupName, awsAccountId));
            }

            logger.log(String.format("%s [%s] for accountId [%s] has been successfully updated!", ResourceModel.TYPE_NAME, model.getProfilingGroupName(), awsAccountId));

            return ProgressEvent.defaultSuccessHandler(model);
        } catch (ConflictException e) {
            throw new CfnAlreadyExistsException(e);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME + e.getMessage(), e);
        }
    }

    private GetPolicyResponse getExistingPolicy(AmazonWebServicesClientProxy proxy, String profilingGroupName) {
        return proxy.injectCredentialsAndInvokeV2(
            GetPolicyRequest.builder().profilingGroupName(profilingGroupName).build(),
            profilerClient::getPolicy
        );
    }

    private void putAgentPermissions(AmazonWebServicesClientProxy proxy,
                                     String profilingGroupName,
                                     List<String> principals,
                                     String revisionId) {
        PutPermissionRequest putPermissionRequest = PutPermissionRequest.builder()
                                                        .profilingGroupName(profilingGroupName)
                                                        .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                                                        .principals(principals)
                                                        .revisionId(revisionId)
                                                        .build();

        proxy.injectCredentialsAndInvokeV2(putPermissionRequest, profilerClient::putPermission);
    }

    private void removeAgentPermission(AmazonWebServicesClientProxy proxy,
                                       String profilingGroupName,
                                       String revisionId) {
        RemovePermissionRequest removePermissionRequest = RemovePermissionRequest.builder()
                                                              .profilingGroupName(profilingGroupName)
                                                              .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                                                              .revisionId(revisionId)
                                                              .build();

        proxy.injectCredentialsAndInvokeV2(removePermissionRequest, profilerClient::removePermission);
    }
}
