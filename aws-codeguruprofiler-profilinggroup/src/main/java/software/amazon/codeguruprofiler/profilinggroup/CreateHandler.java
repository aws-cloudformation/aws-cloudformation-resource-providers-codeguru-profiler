package software.amazon.codeguruprofiler.profilinggroup;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup.AGENT_PERMISSIONS;
import static software.amazon.codeguruprofiler.profilinggroup.NotificationChannelHelper.addChannelNotifications;
import static software.amazon.codeguruprofiler.profilinggroup.NotificationChannelHelper.anomalyDetectionNotificationConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
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
        final Map<String, String> tags = tagsFromModel(model);
        final String computePlatform = model.getComputePlatform();

        final CreateProfilingGroupRequest createProfilingGroupRequest =
            getCreateProfilingGroupRequest(
                pgName,
                computePlatform,
                request.getClientRequestToken(),
                tags
            );

        safelyInvokeApi(() -> {
            proxy.injectCredentialsAndInvokeV2(createProfilingGroupRequest, profilerClient::createProfilingGroup);
        });

        if (tags.isEmpty()) {
            logger.log(format("%s [%s] for accountId [%s] has been successfully created!", ResourceModel.TYPE_NAME, pgName, awsAccountId));
        } else {
            logger.log(format("%s [%s] for accountId [%s] with tags [%s] has been successfully created!", ResourceModel.TYPE_NAME, pgName, awsAccountId, tags));
        }

        Optional<List<String>> principals = principalsForAgentPermissionsFrom(model);
        if (principals.isPresent()) {
            putAgentPermissions(proxy, logger, pgName, principals.get(), awsAccountId);
            logger.log(format("%s [%s] for accountId [%s] has been successfully updated with agent permissions!",
                ResourceModel.TYPE_NAME, pgName, awsAccountId));
        }

        Optional<List<Channel>> anomalyDetectionNotificationConfiguration = anomalyDetectionNotificationConfiguration(model);
        if (anomalyDetectionNotificationConfiguration.isPresent()) {
            putChannelNotifications(proxy, logger, pgName, awsAccountId, anomalyDetectionNotificationConfiguration.get());
            logger.log(format("%s [%s] for accountId [%s] has successfully added a Notification Channel!",
                    ResourceModel.TYPE_NAME, pgName, awsAccountId));
        }

        return ProgressEvent.defaultSuccessHandler(model);
    }

    private void putChannelNotifications(final AmazonWebServicesClientProxy proxy, final Logger logger,
                                         final String pgName, final String awsAccountId, List<Channel> anomalyDetectionNotificationConfiguration) {
        safelyInvokeApi(() -> {
            try {
                addChannelNotifications(pgName, anomalyDetectionNotificationConfiguration, proxy, profilerClient);
            } catch (CodeGuruProfilerException addChannelNotificationException) {
                logger.log(format("%s [%s] for accountId [%s] has failed when adding Channel Notification, trying to delete the profiling group!",
                        ResourceModel.TYPE_NAME, pgName, awsAccountId));
                deleteProfilingGroup(proxy, logger, pgName, awsAccountId, addChannelNotificationException);
                throw addChannelNotificationException;
            }
        });
    }

    private void putAgentPermissions(final AmazonWebServicesClientProxy proxy, final Logger logger,
                                     final String pgName, final List<String> principals, final String awsAccountId) {
        PutPermissionRequest putPermissionRequest = PutPermissionRequest.builder()
            .profilingGroupName(pgName)
            .actionGroup(AGENT_PERMISSIONS)
            .principals(principals)
            .build();

        safelyInvokeApi(() -> {
            try {
                proxy.injectCredentialsAndInvokeV2(putPermissionRequest, profilerClient::putPermission);
            } catch (CodeGuruProfilerException putPermissionException) {
                logger.log(format("%s [%s] for accountId [%s] has failed when updating the agent permissions, trying to delete the profiling group!",
                    ResourceModel.TYPE_NAME, pgName, awsAccountId));
                deleteProfilingGroup(proxy, logger, pgName, awsAccountId, putPermissionException);
                throw putPermissionException;
            }
        });
    }

    private void deleteProfilingGroup(AmazonWebServicesClientProxy proxy, Logger logger,
                                      String pgName, String awsAccountId, CodeGuruProfilerException exception) {
        DeleteProfilingGroupRequest deletePgRequest = DeleteProfilingGroupRequest.builder().profilingGroupName(pgName).build();
        try {
            proxy.injectCredentialsAndInvokeV2(deletePgRequest, profilerClient::deleteProfilingGroup);
            logger.log(format("%s [%s] for accountId [%s] has succeeded when deleting the profiling group!",
                ResourceModel.TYPE_NAME, pgName, awsAccountId));
        } catch (CodeGuruProfilerException deleteException) {
            logger.log(format("%s [%s] for accountId [%s] has failed when deleting the profiling group!",
                ResourceModel.TYPE_NAME, pgName, awsAccountId));
            exception.addSuppressed(deleteException);
            throw exception;
        }
    }

    private static void safelyInvokeApi(final Runnable lambda) {
        try {
            lambda.run();
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

    private static Optional<List<String>> principalsForAgentPermissionsFrom(final ResourceModel model) {
        if (model.getAgentPermissions() == null) {
            return Optional.empty();
        }
        if (model.getAgentPermissions().getPrincipals() == null) {
            return Optional.empty();
        }
        return Optional.of(model.getAgentPermissions().getPrincipals());
    }

    private static CreateProfilingGroupRequest getCreateProfilingGroupRequest(
        final String pgName,
        final String computePlatform,
        final String requestToken,
        final Map<String, String> tags
    ) {
        if (tags.isEmpty()) {
            return CreateProfilingGroupRequest.builder()
                       .profilingGroupName(pgName)
                       .computePlatform(computePlatform)
                       .clientToken(requestToken)
                       .build();
        }
        return CreateProfilingGroupRequest.builder()
                   .profilingGroupName(pgName)
                   .computePlatform(computePlatform)
                   .clientToken(requestToken)
                   .tags(tags)
                   .build();
    }

    private static Map<String, String> tagsFromModel(final ResourceModel model) {
        List<Tag> tags = model.getTags();
        if (tags == null || tags.isEmpty()) {
            return emptyMap();
        }

        return tags.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }
}
