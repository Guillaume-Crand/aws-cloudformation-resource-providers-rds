package software.amazon.rds.dbparametergroup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mockito.internal.util.collections.Sets;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBParameterGroup;
import software.amazon.awssdk.services.rds.model.DescribeDbParametersRequest;
import software.amazon.awssdk.services.rds.model.DescribeDbParametersResponse;
import software.amazon.awssdk.services.rds.model.DescribeEngineDefaultParametersRequest;
import software.amazon.awssdk.services.rds.model.DescribeEngineDefaultParametersResponse;
import software.amazon.awssdk.services.rds.model.EngineDefaults;
import software.amazon.awssdk.services.rds.model.Parameter;
import software.amazon.awssdk.services.rds.paginators.DescribeDBParametersIterable;
import software.amazon.awssdk.services.rds.paginators.DescribeEngineDefaultParametersIterable;
import software.amazon.cloudformation.loggers.LogPublisher;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.rds.common.logging.RequestLogger;

public class AbstractTestBase {
    protected static final Credentials MOCK_CREDENTIALS;
    protected static final LoggerProxy logger;

    protected static final ResourceModel RESOURCE_MODEL;
    protected static final ResourceModel RESET_RESOURCE_MODEL;
    protected static final ResourceModel RESOURCE_MODEL_WITH_TAGS;
    protected static final DBParameterGroup DB_PARAMETER_GROUP_ACTIVE;
    protected static final Set<Tag> TAG_SET;
    protected static final String LOGICAL_RESOURCE_IDENTIFIER;
    protected static final Map<String, Object> PARAMS;
    protected static final Map<String, Object> RESET_PARAMS;
    protected static final RequestLogger EMPTY_REQUEST_LOGGER;



    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        logger = new LoggerProxy();
        EMPTY_REQUEST_LOGGER = new RequestLogger(logger, ResourceHandlerRequest.builder().build(), null);
        LOGICAL_RESOURCE_IDENTIFIER = "db-parameter-group";

        PARAMS = new HashMap<>();
        PARAMS.put("param1", "value");
        PARAMS.put("param2", "value");

        RESET_PARAMS = new HashMap<>(PARAMS);
        RESET_PARAMS.remove("param1");

        RESOURCE_MODEL = ResourceModel.builder()
                .dBParameterGroupName("testDBParameterGroup")
                .description("test DB Parameter group description")
                .family("testFamily")
                .tags(Collections.emptyList())
                .parameters(PARAMS)
                .build();

        RESET_RESOURCE_MODEL = ResourceModel.builder()
                .dBParameterGroupName("testDBParameterGroup")
                .description("test DB Parameter group description")
                .family("testFamily")
                .tags(Collections.emptyList())
                .parameters(RESET_PARAMS)
                .build();

        RESOURCE_MODEL_WITH_TAGS = ResourceModel.builder()
                .dBParameterGroupName("testDBParameterGroup2")
                .description("test DB Parameter group description")
                .family("testFamily2")
                .tags(Collections.singletonList(Tag.builder().key("Key").value("Value").build()))
                .parameters(PARAMS)
                .build();

        DB_PARAMETER_GROUP_ACTIVE = DBParameterGroup.builder()
                .dbParameterGroupArn("arn")
                .dbParameterGroupName("testDBParameterGroup")
                .description("test DB Parameter group description")
                .dbParameterGroupFamily("testFamily")
                .build();

        TAG_SET = Sets.newSet(Tag.builder().key("key").value("value").build());
    }

    static Map<String, String> translateTagsToMap(final Set<Tag> tags) {
        return tags.stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

    }

    static String getClientRequestToken() {
        return UUID.randomUUID().toString();
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

    void mockDescribeDbParametersResponse(ProxyClient<RdsClient> proxyClient,
                                          String firstParamApplyType,
                                          String secondParamApplyType,
                                          boolean isModifiable,
                                          boolean mockDescribeParameters) {
        Parameter param1 = Parameter.builder()
                .parameterName("param1")
                .parameterValue("system_value")
                .isModifiable(isModifiable)
                .applyType(firstParamApplyType)
                .applyMethod("pending-reboot")
                .build();
        Parameter defaultParam1 = param1.toBuilder()
                .parameterValue("default_value")
                .applyMethod("")
                .build();
        Parameter param2 = Parameter.builder()
                .parameterName("param2")
                .parameterValue("system_value")
                .isModifiable(isModifiable)
                .applyType(secondParamApplyType)
                .build();
        //Adding parameter to current parameters and not adding it to default. Expected behaviour is to ignore it
        Parameter param3 = Parameter.builder()
                .parameterName("param3")
                .parameterValue("system_value")
                .isModifiable(isModifiable)
                .applyType(secondParamApplyType)
                .build();
        //Adding parameter to default parameters and not adding it to current. Expected behaviour is to ignore it
        Parameter param4 = Parameter.builder()
                .parameterName("param4")
                .parameterValue("system_value")
                .isModifiable(isModifiable)
                .applyType(secondParamApplyType)
                .build();


        DescribeEngineDefaultParametersIterable describeEngineDefaultParametersResponses = mock(DescribeEngineDefaultParametersIterable.class);
        final DescribeEngineDefaultParametersResponse describeEngineDefaultParametersResponse = DescribeEngineDefaultParametersResponse.builder()
                .engineDefaults(EngineDefaults.builder()
                        .parameters(defaultParam1, param2, param4)
                        .build()
                ).build();
        when(describeEngineDefaultParametersResponses.stream())
                .thenReturn(Stream.<DescribeEngineDefaultParametersResponse>builder().
                        add(describeEngineDefaultParametersResponse)
                        .build()
                );
        when(proxyClient.client().describeEngineDefaultParametersPaginator(any(DescribeEngineDefaultParametersRequest.class))).thenReturn(describeEngineDefaultParametersResponses);

        if (!mockDescribeParameters)
            return;

        final DescribeDbParametersResponse describeDbParametersResponse = DescribeDbParametersResponse.builder().marker(null)
                .parameters(param1, param2, param3).build();

        final DescribeDBParametersIterable describeDbParametersIterable = mock(DescribeDBParametersIterable.class);
        when(describeDbParametersIterable.stream())
                .thenReturn(Stream.<DescribeDbParametersResponse>builder()
                        .add(describeDbParametersResponse)
                        .build()
                );
        when(proxyClient.client().describeDBParametersPaginator(any(DescribeDbParametersRequest.class))).thenReturn(describeDbParametersIterable);
    }
}
