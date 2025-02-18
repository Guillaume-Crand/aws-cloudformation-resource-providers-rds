package software.amazon.rds.optiongroup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeOptionGroupsRequest;
import software.amazon.awssdk.services.rds.model.DescribeOptionGroupsResponse;
import software.amazon.awssdk.services.rds.model.OptionGroup;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.rds.common.handler.Tagging;

public abstract class AbstractTestBase extends software.amazon.rds.common.test.AbstractTestBase<OptionGroup, ResourceModel, CallbackContext> {

    protected static final String LOGICAL_RESOURCE_IDENTIFIER = "optiongroup";

    protected static final String MSG_NOT_FOUND_ERR = "OptionGroup not found";

    protected static final Credentials MOCK_CREDENTIALS;
    protected static final LoggerProxy logger;

    protected static final ResourceModel RESOURCE_MODEL;
    protected static final ResourceModel RESOURCE_MODEL_NO_OPTION_GROUP_NAME;
    protected static final ResourceModel RESOURCE_MODEL_WITH_CONFIGURATIONS;
    protected static final ResourceModel RESOURCE_MODEL_WITH_RESOURCE_TAGS;
    protected static final OptionGroup OPTION_GROUP_ACTIVE;
    protected static final Tagging.TagSet TAG_SET;

    protected static Constant TEST_BACKOFF_DELAY = Constant.of()
            .delay(Duration.ofSeconds(1L))
            .timeout(Duration.ofSeconds(10L))
            .build();

    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        logger = new LoggerProxy();

        TAG_SET = Tagging.TagSet.builder()
                .systemTags(ImmutableSet.of(
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("system-tag-1").value("system-tag-value1").build(),
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("system-tag-2").value("system-tag-value2").build(),
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("system-tag-3").value("system-tag-value3").build()
                )).stackTags(ImmutableSet.of(
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("stack-tag-1").value("stack-tag-value1").build(),
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("stack-tag-2").value("stack-tag-value2").build(),
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("stack-tag-3").value("stack-tag-value3").build()
                )).resourceTags(ImmutableSet.of(
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("resource-tag-1").value("resource-tag-value1").build(),
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("resource-tag-2").value("resource-tag-value2").build(),
                        software.amazon.awssdk.services.rds.model.Tag.builder().key("resource-tag-3").value("resource-tag-value3").build()
                )).build();

        RESOURCE_MODEL = ResourceModel.builder()
                .optionGroupName("testOptionGroup")
                .optionGroupDescription("test option group description")
                .engineName("testEngineVersion")
                .majorEngineVersion("testMajorVersionName")
                .build();

        RESOURCE_MODEL_NO_OPTION_GROUP_NAME = ResourceModel.builder()
                .optionGroupDescription("test option group description")
                .engineName("testEngineVersion")
                .majorEngineVersion("testMajorVersionName")
                .build();

        RESOURCE_MODEL_WITH_RESOURCE_TAGS = ResourceModel.builder()
                .optionGroupName("testOptionGroup")
                .optionGroupDescription("test option group description")
                .engineName("testEngineVersion")
                .majorEngineVersion("testMajorVersionName")
                .tags(Translator.translateTagsFromSdk(TAG_SET.getResourceTags()))
                .build();

        RESOURCE_MODEL_WITH_CONFIGURATIONS = ResourceModel.builder()
                .optionGroupName("testOptionGroup")
                .optionGroupDescription("test option group description")
                .engineName("testEngineVersion")
                .majorEngineVersion("testMajorVersionName")
                .optionConfigurations(ImmutableList.of(
                        OptionConfiguration.builder()
                                .optionName("testOptionConfiguration")
                                .optionVersion("1.2.3")
                                .build()
                ))
                .build();

        OPTION_GROUP_ACTIVE = OptionGroup.builder()
                .optionGroupArn("arn")
                .optionGroupName("testOptionGroup")
                .build();
    }

    static ProxyClient<RdsClient> MOCK_PROXY(
            final AmazonWebServicesClientProxy proxy,
            final RdsClient rdsClient) {
        return new ProxyClient<RdsClient>() {
            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
            injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
                return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
            CompletableFuture<ResponseT>
            injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
            IterableT
            injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
                return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
            injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
            injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RdsClient client() {
                return rdsClient;
            }
        };
    }

    protected abstract BaseHandlerStd getHandler();

    protected abstract AmazonWebServicesClientProxy getProxy();

    protected abstract ProxyClient<RdsClient> getProxyClient();

    @Override
    protected String getLogicalResourceIdentifier() {
        return LOGICAL_RESOURCE_IDENTIFIER;
    }

    @Override
    protected void expectResourceSupply(final Supplier<OptionGroup> supplier) {
        when(getProxyClient()
                .client()
                .describeOptionGroups(any(DescribeOptionGroupsRequest.class))
        ).then(res -> DescribeOptionGroupsResponse.builder()
                .optionGroupsList(supplier.get())
                .build()
        );
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> invokeHandleRequest(
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext context
    ) {
        return getHandler().handleRequest(getProxy(), request, context, getProxyClient(), logger);
    }
}
