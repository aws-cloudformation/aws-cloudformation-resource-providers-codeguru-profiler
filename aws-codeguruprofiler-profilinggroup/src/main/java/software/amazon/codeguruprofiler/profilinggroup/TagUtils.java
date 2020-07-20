package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.CodeGuruProfilerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.TagResourceRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.TagResourceResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.UntagResourceRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.UntagResourceResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;

public class TagUtils {
    private static final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();

    public static Set<Tag> convertTagMapIntoSet(Map<String, String> tags) {
        return tags.entrySet()
                   .stream()
                   .map(tag ->
                            Tag.builder().key(tag.getKey()).value(tag.getValue()).build()
                   )
                   .collect(Collectors.toSet());
    }

    public static void updateTags(AmazonWebServicesClientProxy proxy,
                                  ResourceModel desiredModel,
                                  String awsAccountId,
                                  String resourceArn,
                                  Logger logger) {
        Set<Tag> existingTags = convertTagMapIntoSet(listTagsForResource(proxy, resourceArn).tags());

        Set<Tag> desiredTags = tagsFromModel(desiredModel);

        if (!existingTags.containsAll(desiredTags)) {
            logger.log(
                String.format("Tag change detected for [%s] for accountId [%s]: Old: %s, New: %s",
                    resourceArn,
                    awsAccountId,
                    existingTags,
                    desiredTags
                )
            );
            final Set<Tag> tagsToRemove = new HashSet<>(existingTags);
            tagsToRemove.removeIf(desiredTags::contains);
            desiredTags.removeIf(existingTags::contains);

            if (!tagsToRemove.isEmpty()) {
                logger.log(
                    String.format("Untagging tags from [%s] for accountId [%s]: %s",
                        resourceArn,
                        awsAccountId,
                        tagsToRemove
                    )
                );
                untagResource(proxy, resourceArn, tagsToRemove.stream().map(Tag::getKey).collect(Collectors.toSet()));
                logger.log(
                    String.format("Successfully untagged tags from [%s] for accountId [%s]",
                        resourceArn,
                        awsAccountId
                    )
                );
            }

            if (!desiredTags.isEmpty()) {
                logger.log(
                    String.format("Adding new tags to [%s] for accountId [%s]: %s",
                        resourceArn,
                        awsAccountId,
                        desiredTags
                    )
                );
                try {
                    tagResource(proxy, resourceArn, desiredTags.stream().collect(Collectors.toMap(Tag :: getKey, Tag :: getValue)));
                } catch(CodeGuruProfilerException e) {
                    logger.log(
                        String.format("Failed to add new tags to [%s] for accountId [%s]",
                            resourceArn,
                            awsAccountId
                        )
                    );

                    if (!tagsToRemove.isEmpty()) {
                        logger.log(
                            String.format("Adding back old tags to [%s] for accountId [%s]: %s",
                                resourceArn,
                                awsAccountId,
                                desiredTags
                            )
                        );
                        tagResource(proxy, resourceArn, tagsToRemove.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue)));
                        logger.log(
                            String.format("Successfully added back old tags to [%s] for accountId [%s]",
                                resourceArn,
                                awsAccountId
                            )
                        );
                    }
                    throw e;
                }
                logger.log(
                    String.format("Successfully added new tags to [%s] for accountId [%s]",
                        resourceArn,
                        awsAccountId
                    )
                );
            }
        }
    }

    private static UntagResourceResponse untagResource(AmazonWebServicesClientProxy proxy, String resourceArn, Collection<String> tagKeys) {
        return proxy.injectCredentialsAndInvokeV2(
            UntagResourceRequest.builder().resourceArn(resourceArn).tagKeys(tagKeys).build(),
            profilerClient::untagResource
        );
    }

    private static ListTagsForResourceResponse listTagsForResource(AmazonWebServicesClientProxy proxy, String resourceArn) {
        return proxy.injectCredentialsAndInvokeV2(
            ListTagsForResourceRequest.builder().resourceArn(resourceArn).build(),
            profilerClient::listTagsForResource
        );
    }

    private static TagResourceResponse tagResource(AmazonWebServicesClientProxy proxy, String resourceArn, Map<String, String> tags) {
        return proxy.injectCredentialsAndInvokeV2(
            TagResourceRequest.builder().resourceArn(resourceArn).tags(tags).build(),
            profilerClient::tagResource
        );
    }

    private static Set<Tag> tagsFromModel(final ResourceModel model) {
        List<Tag> tags = model.getTags();
        if (tags == null) {
            return emptySet();
        } else {
            return new HashSet<>(tags);
        }
    }
}
