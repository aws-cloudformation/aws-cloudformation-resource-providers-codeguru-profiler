package software.amazon.codeguruprofiler.profilinggroup;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup;
import software.amazon.awssdk.services.codeguruprofiler.model.Channel;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.EventPublisher;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationResponse;
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

        Optional<List<String>> principals = principalsForAgentPermissionsFrom(model);

        try {
            GetPolicyResponse getPolicyResponse = getExistingPolicy(proxy, profilingGroupName);

            if (principals.isPresent()) {
                putAgentPermissions(proxy, profilingGroupName, principals.get(), getPolicyResponse.revisionId());
                logger.log(
                        String.format("Policy for [%s] for accountId [%s] has been successfully updated! actionGroup: %s, principals: %s",
                                profilingGroupName,
                                awsAccountId,
                                ActionGroup.AGENT_PERMISSIONS,
                                principals.get())
                );
            } else if (getPolicyResponse.policy() != null) {
                removeAgentPermission(proxy, profilingGroupName, getPolicyResponse.revisionId());
                logger.log(String.format("Policy for [%s] for accountId [%s] has been successfully removed!", profilingGroupName, awsAccountId));
            }

            // Current channels means the channels from the model
            Optional<AnomalyDetectionNotificationConfiguration> anomalyDetectionNotificationConfiguration = anomalyDetectionNotificationConfiguration(model);
            if (anomalyDetectionNotificationConfiguration.isPresent()) {
                updateNotificationChannels(profilingGroupName, proxy,
                        model.getAnomalyDetectionNotificationConfiguration().getChannels().stream().map(channel -> {
                            Channel.Builder uri = Channel.builder()
                                    .uri(channel.getChannelUri())
                                    .eventPublishers(EventPublisher.fromValue(channel.getEventPublishers()));
                            // since ChannelId is an optional param, check here to avoid NPE
                            if (channel.getId() != null) {
                                uri.id(channel.getId());
                            }

                            return uri.build();
                        }).collect(Collectors.toList()));

                logger.log(String.format("%s [%s] for accountId [%s] has been successfully updated!", ResourceModel.TYPE_NAME, model.getProfilingGroupName(), awsAccountId));
            }

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

    private void updateNotificationChannels(String pgName, AmazonWebServicesClientProxy proxy, List<Channel> currentChannels) {
        List<Channel> existingChannels = getExistingNotificationConfiguration(proxy, pgName).notificationConfiguration().channels();
        updateNotificationChannels(existingChannels, currentChannels, pgName, proxy);
    }

    private GetNotificationConfigurationResponse getExistingNotificationConfiguration(AmazonWebServicesClientProxy proxy, String profilingGroupName) {
        return proxy.injectCredentialsAndInvokeV2(GetNotificationConfigurationRequest.builder()
                        .profilingGroupName(profilingGroupName)
                        .build(),
                profilerClient::getNotificationConfiguration);
    }

    // Generate the change-set and add / delete notification channel based on change-set
    private void updateNotificationChannels(List<Channel> existingChannels, List<Channel> currentChannels, String pgName, AmazonWebServicesClientProxy proxy) {
        Set<String> existingChannelsSet = existingChannels.stream().map(Channel::uri).collect(Collectors.toSet());
        Set<String> currentChannelsSet = currentChannels.stream().map(Channel::uri).collect(Collectors.toSet());
        for (Channel channel : existingChannels) {
            if (!currentChannelsSet.contains(channel.uri())) {
                // Can assert that channel.id() exists here, since this is from an existingChannel, which is fetched via the GetNotificationConfiguration, which has the id attached
                NotificationChannelHelper.deleteNotificationChannel(pgName, channel.id(), proxy, profilerClient);
            }
        }

        for (Channel channel : currentChannels) {
            if (!existingChannelsSet.contains(channel.uri())) {
                NotificationChannelHelper.addChannelNotification(pgName, channel, proxy, profilerClient);
            }
        }
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

    private static Optional<List<String>> principalsForAgentPermissionsFrom(final ResourceModel model) {
        if (model.getAgentPermissions() == null) {
            return Optional.empty();
        }
        if (model.getAgentPermissions().getPrincipals() == null) {
            return Optional.empty();
        }
        return Optional.of(model.getAgentPermissions().getPrincipals());
    }

    private static Optional<AnomalyDetectionNotificationConfiguration> anomalyDetectionNotificationConfiguration(final ResourceModel model) {
        if (model.getAnomalyDetectionNotificationConfiguration() == null ) {
            return Optional.empty();
        }

        if (model.getAnomalyDetectionNotificationConfiguration().getChannels() == null) {
            return Optional.empty();
        }

        return Optional.of(model.getAnomalyDetectionNotificationConfiguration());
    }
}
