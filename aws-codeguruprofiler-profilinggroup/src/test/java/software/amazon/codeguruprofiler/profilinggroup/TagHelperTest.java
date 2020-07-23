package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.awssdk.services.codeguruprofiler.model.CodeGuruProfilerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.TagResourceRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.UntagResourceRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


public class TagHelperTest {
    @Mock
    private AmazonWebServicesClientProxy proxy = mock(AmazonWebServicesClientProxy.class);

    @Mock
    private Logger logger = mock(Logger.class);

    private final String profilingGroupName = "BlackWidow-2020";

    private final String awsAccountId = "123456789012";

    private final String groupArn = "arn:aws:codeguru-profiler:us-east-1:123456789012:profilingGroup/" + profilingGroupName;

    private String oldKeyToBeKept = "oldKeyToBeKept";
    private String oldKeyToBeRemoved = "oldKeyToBeRemoved";
    private String oldKeyToBeUpdated = "oldKeyToBeUpdated";

    private String newKeyToBeAdded = "newKeyToBeAdded";

    private String oldTagValue = "oldTagValue";
    private String newTagValue = "newTagValue";

    private final Map<String, String> oldTagsMap = new HashMap<String, String>()
    {{
        put(oldKeyToBeKept, oldTagValue);
        put(oldKeyToBeUpdated, oldTagValue);
        put(oldKeyToBeRemoved, oldTagValue);
    }};

    private final Map<String, String> newTagsMap = new HashMap<String, String>()
    {{
        put(oldKeyToBeKept, oldTagValue);
        put(oldKeyToBeUpdated, newTagValue);
        put(newKeyToBeAdded, newTagValue);
    }};

    @Nested
    class DescribeConvertTagMapIntoSet {
        @Test
        public void itConvertsSuccessfully() {
            Set<Tag> result = TagHelper.convertTagMapIntoSet(oldTagsMap);

            assertThat(result).containsExactlyInAnyOrder(
                Tag.builder().key(oldKeyToBeKept).value(oldTagValue).build(),
                Tag.builder().key(oldKeyToBeUpdated).value(oldTagValue).build(),
                Tag.builder().key(oldKeyToBeRemoved).value(oldTagValue).build()
            );
        }
    }

    @Nested
    class DescribeUpdateTags {
        @Nested
        class WhenNoTagUpdateExpected {
            private final ResourceModel desiredModel = ResourceModel.builder()
                                                           .profilingGroupName(profilingGroupName)
                                                           .arn(groupArn)
                                                           .tags(new ArrayList<>(TagHelper.convertTagMapIntoSet(oldTagsMap)))
                                                           .build();

            @BeforeEach
            public void setup() {
                doReturn(ListTagsForResourceResponse.builder().tags(oldTagsMap).build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(ListTagsForResourceRequest.class), any());
            }

            @Test
            public void itOnlyCallsListTags() {
                TagHelper.updateTags(proxy, desiredModel, awsAccountId, groupArn, logger);

                verify(proxy, times(1))
                    .injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());
                verifyNoMoreInteractions(proxy);
            }

            @Nested
            class WhenThereIsNoTags {
                private final ResourceModel desiredModel = ResourceModel.builder()
                                                               .profilingGroupName(profilingGroupName)
                                                               .arn(groupArn)
                                                               .build();

                @BeforeEach
                public void setup() {
                    doReturn(ListTagsForResourceResponse.builder().tags(emptyMap()).build())
                        .when(proxy).injectCredentialsAndInvokeV2(any(ListTagsForResourceRequest.class), any());
                }

                @Test
                public void itOnlyCallsListTags() {
                    TagHelper.updateTags(proxy, desiredModel, awsAccountId, groupArn, logger);

                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());
                    verifyNoMoreInteractions(proxy);
                }
            }
        }

        @Nested
        class WhenTagUpdateExpected {
            private final ResourceModel desiredModel = ResourceModel.builder()
                                                           .profilingGroupName(profilingGroupName)
                                                           .arn(groupArn)
                                                           .tags(new ArrayList<>(TagHelper.convertTagMapIntoSet(newTagsMap)))
                                                           .build();

            @BeforeEach
            public void setup() {
                doReturn(ListTagsForResourceResponse.builder().tags(oldTagsMap).build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(ListTagsForResourceRequest.class), any());
            }

            @Test
            public void itCallsListTagsTagAndUntagResource() {
                TagHelper.updateTags(proxy, desiredModel, awsAccountId, groupArn, logger);

                verify(proxy, times(1))
                    .injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());
                verify(proxy, times(1))
                    .injectCredentialsAndInvokeV2(eq(
                        UntagResourceRequest.builder()
                            .resourceArn(groupArn)
                            .tagKeys(new HashSet<String>() {{
                                add(oldKeyToBeRemoved);
                                add(oldKeyToBeUpdated);
                            }})
                            .build()
                    ), any());
                verify(proxy, times(1))
                    .injectCredentialsAndInvokeV2(eq(
                        TagResourceRequest.builder()
                            .resourceArn(groupArn)
                            .tags(new HashMap<String, String>() {{
                                put(oldKeyToBeUpdated, newTagValue);
                                put(newKeyToBeAdded, newTagValue);
                            }})
                            .build()
                    ), any());
                verifyNoMoreInteractions(proxy);
            }

            @Nested
            class WhenNotTagsProvided {
                private final ResourceModel desiredModel = ResourceModel.builder()
                                                               .profilingGroupName(profilingGroupName)
                                                               .arn(groupArn)
                                                               .build();

                @Test
                public void itCallsListTagsAndUntagResource() {
                    TagHelper.updateTags(proxy, desiredModel, awsAccountId, groupArn, logger);

                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());
                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(
                            UntagResourceRequest.builder()
                                .resourceArn(groupArn)
                                .tagKeys(new HashSet<String>() {{
                                    add(oldKeyToBeRemoved);
                                    add(oldKeyToBeUpdated);
                                    add(oldKeyToBeKept);
                                }})
                                .build()
                        ), any());
                    verifyNoMoreInteractions(proxy);
                }
            }

            @Nested
            class WhenUntagResourceFailed {
                @BeforeEach
                public void setup() {
                    doThrow(CodeGuruProfilerException.builder().build())
                        .when(proxy).injectCredentialsAndInvokeV2(any(UntagResourceRequest.class), any());
                }

                @Test
                public void itOnlyCallsListTagsAndStopAfterUntag() {
                    assertThrows(CodeGuruProfilerException.class, () -> TagHelper.updateTags(proxy, desiredModel, awsAccountId, groupArn, logger));

                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());
                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(any(UntagResourceRequest.class), any());
                    verifyNoMoreInteractions(proxy);
                }
            }

            @Nested
            class WhenTagResourceFailed {
                @BeforeEach
                public void setup() {
                    doThrow(CodeGuruProfilerException.builder().build())
                        .when(proxy).injectCredentialsAndInvokeV2(eq(
                            TagResourceRequest.builder()
                                .resourceArn(groupArn)
                                .tags(new HashMap<String, String>() {{
                                    put(oldKeyToBeUpdated, newTagValue);
                                    put(newKeyToBeAdded, newTagValue);
                                }})
                                .build()
                    ), any());
                }

                @Test
                public void itAddsBackRemovedTags() {
                    assertThrows(CodeGuruProfilerException.class, () -> TagHelper.updateTags(proxy, desiredModel, awsAccountId, groupArn, logger));

                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());
                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(
                            UntagResourceRequest.builder()
                                .resourceArn(groupArn)
                                .tagKeys(new HashSet<String>() {{
                                    add(oldKeyToBeRemoved);
                                    add(oldKeyToBeUpdated);
                                }})
                                .build()
                        ), any());
                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(
                            TagResourceRequest.builder()
                                .resourceArn(groupArn)
                                .tags(new HashMap<String, String>() {{
                                    put(oldKeyToBeUpdated, newTagValue);
                                    put(newKeyToBeAdded, newTagValue);
                                }})
                                .build()
                        ), any());
                    verify(proxy, times(1))
                        .injectCredentialsAndInvokeV2(eq(
                            TagResourceRequest.builder()
                                .resourceArn(groupArn)
                                .tags(new HashMap<String, String>() {{
                                    put(oldKeyToBeRemoved, oldTagValue);
                                    put(oldKeyToBeUpdated, oldTagValue);
                                }})
                                .build()
                        ), any());
                    verifyNoMoreInteractions(proxy);
                }

                @Nested
                class WhenNoTagsWereRemoved {
                    @BeforeEach
                    public void setup() {
                        Map<String, String> oldTagsMap = new HashMap<>(newTagsMap);
                        oldTagsMap.remove(newKeyToBeAdded);

                        doReturn(ListTagsForResourceResponse.builder().tags(oldTagsMap).build())
                            .when(proxy).injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());

                        doThrow(CodeGuruProfilerException.builder().build())
                            .when(proxy).injectCredentialsAndInvokeV2(any(TagResourceRequest.class), any());
                    }

                    @Test
                    public void itDoesNotNeedToAddBackAnyTags() {
                        assertThrows(CodeGuruProfilerException.class, () -> TagHelper.updateTags(proxy, desiredModel, awsAccountId, groupArn, logger));

                        verify(proxy, times(1))
                            .injectCredentialsAndInvokeV2(eq(ListTagsForResourceRequest.builder().resourceArn(groupArn).build()), any());
                        verify(proxy, times(1))
                            .injectCredentialsAndInvokeV2(eq(
                                TagResourceRequest.builder()
                                    .resourceArn(groupArn)
                                    .tags(new HashMap<String, String>() {{ put(newKeyToBeAdded, newTagValue); }})
                                    .build()
                            ), any());
                        verifyNoMoreInteractions(proxy);
                    }
                }
            }
        }
    }
}
