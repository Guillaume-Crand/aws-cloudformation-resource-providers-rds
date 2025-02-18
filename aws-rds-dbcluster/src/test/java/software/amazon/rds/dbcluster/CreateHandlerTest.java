package software.amazon.rds.dbcluster;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Iterables;
import lombok.Getter;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.AddRoleToDbClusterRequest;
import software.amazon.awssdk.services.rds.model.AddRoleToDbClusterResponse;
import software.amazon.awssdk.services.rds.model.AddTagsToResourceRequest;
import software.amazon.awssdk.services.rds.model.AddTagsToResourceResponse;
import software.amazon.awssdk.services.rds.model.CreateDbClusterRequest;
import software.amazon.awssdk.services.rds.model.CreateDbClusterResponse;
import software.amazon.awssdk.services.rds.model.DBCluster;
import software.amazon.awssdk.services.rds.model.DbClusterAlreadyExistsException;
import software.amazon.awssdk.services.rds.model.DescribeDbClustersRequest;
import software.amazon.awssdk.services.rds.model.ModifyDbClusterRequest;
import software.amazon.awssdk.services.rds.model.ModifyDbClusterResponse;
import software.amazon.awssdk.services.rds.model.RdsException;
import software.amazon.awssdk.services.rds.model.RestoreDbClusterFromSnapshotRequest;
import software.amazon.awssdk.services.rds.model.RestoreDbClusterFromSnapshotResponse;
import software.amazon.awssdk.services.rds.model.RestoreDbClusterToPointInTimeRequest;
import software.amazon.awssdk.services.rds.model.RestoreDbClusterToPointInTimeResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.rds.common.error.ErrorCode;
import software.amazon.rds.common.handler.HandlerConfig;
import software.amazon.rds.common.handler.Tagging;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractHandlerTest {

    @Mock
    @Getter
    RdsClient rdsClient;
    @Mock
    @Getter
    private AmazonWebServicesClientProxy proxy;
    @Mock
    @Getter
    private ProxyClient<RdsClient> rdsProxy;
    @Getter
    private CreateHandler handler;

    @BeforeEach
    public void setup() {
        handler = new CreateHandler(
                HandlerConfig.builder()
                        .backoff(Constant.of()
                                .delay(Duration.ofSeconds(1))
                                .timeout(Duration.ofSeconds(120))
                                .build())
                        .build()
        );
        rdsClient = mock(RdsClient.class);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        rdsProxy = MOCK_PROXY(proxy, rdsClient);
    }

    @AfterEach
    public void tear_down() {
        verify(rdsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(rdsClient);
    }

    @Test
    public void handleRequest_CreateDbCluster_SimpleSuccess() {
        when(rdsProxy.client().createDBCluster(any(CreateDbClusterRequest.class)))
                .thenReturn(CreateDbClusterResponse.builder().build());
        when(rdsProxy.client().addRoleToDBCluster(any(AddRoleToDbClusterRequest.class)))
                .thenReturn(AddRoleToDbClusterResponse.builder().build());

        test_handleRequest_base(
                new CallbackContext(),
                () -> DBCLUSTER_ACTIVE,
                () -> RESOURCE_MODEL,
                expectSuccess()
        );

        verify(rdsProxy.client(), times(1)).createDBCluster(any(CreateDbClusterRequest.class));
        verify(rdsProxy.client(), times(1)).addRoleToDBCluster(any(AddRoleToDbClusterRequest.class));
        verify(rdsProxy.client(), times(3)).describeDBClusters(any(DescribeDbClustersRequest.class));
    }

    @Test
    public void handleRequest_CreateDbCluster_AccessDeniedTagging() {
        when(rdsProxy.client().createDBCluster(any(CreateDbClusterRequest.class)))
                .thenThrow(
                        RdsException.builder()
                                .awsErrorDetails(AwsErrorDetails.builder()
                                        .errorCode(ErrorCode.AccessDeniedException.toString())
                                        .build()
                                ).build())
                .thenReturn(CreateDbClusterResponse.builder().build());
        when(rdsProxy.client().addRoleToDBCluster(any(AddRoleToDbClusterRequest.class)))
                .thenReturn(AddRoleToDbClusterResponse.builder().build());
        when(rdsProxy.client().addTagsToResource(any(AddTagsToResourceRequest.class)))
                .thenReturn(AddTagsToResourceResponse.builder().build());

        final Tagging.TagSet extraTags = Tagging.TagSet.builder()
                .stackTags(TAG_SET.getStackTags())
                .resourceTags(TAG_SET.getResourceTags())
                .build();

        test_handleRequest_base(
                new CallbackContext(),
                ResourceHandlerRequest.<ResourceModel>builder()
                        .systemTags(Translator.translateTagsToRequest(Translator.translateTagsFromSdk(TAG_SET.getSystemTags())))
                        .desiredResourceTags(Translator.translateTagsToRequest(Translator.translateTagsFromSdk(TAG_SET.getStackTags()))),
                () -> DBCLUSTER_ACTIVE,
                null,
                () -> RESOURCE_MODEL.toBuilder()
                        .tags(Translator.translateTagsFromSdk(TAG_SET.getResourceTags()))
                        .build(),
                expectSuccess()
        );

        ArgumentCaptor<CreateDbClusterRequest> createCaptor = ArgumentCaptor.forClass(CreateDbClusterRequest.class);
        verify(rdsProxy.client(), times(2)).createDBCluster(createCaptor.capture());
        final CreateDbClusterRequest requestWithAllTags = createCaptor.getAllValues().get(0);
        final CreateDbClusterRequest requestWithSystemTags = createCaptor.getAllValues().get(1);
        Assertions.assertThat(requestWithAllTags.tags()).containsExactlyInAnyOrder(
                Iterables.toArray(Tagging.translateTagsToSdk(TAG_SET), software.amazon.awssdk.services.rds.model.Tag.class));
        Assertions.assertThat(requestWithSystemTags.tags()).containsExactlyInAnyOrder(
                Iterables.toArray(TAG_SET.getSystemTags(), software.amazon.awssdk.services.rds.model.Tag.class));

        verify(rdsProxy.client(), times(1)).addRoleToDBCluster(any(AddRoleToDbClusterRequest.class));
        verify(rdsProxy.client(), times(4)).describeDBClusters(any(DescribeDbClustersRequest.class));

        ArgumentCaptor<AddTagsToResourceRequest> addTagsCaptor = ArgumentCaptor.forClass(AddTagsToResourceRequest.class);
        verify(rdsProxy.client(), times(1)).addTagsToResource(addTagsCaptor.capture());
        Assertions.assertThat(addTagsCaptor.getValue().tags()).containsExactlyInAnyOrder(
                Iterables.toArray(Tagging.translateTagsToSdk(extraTags), software.amazon.awssdk.services.rds.model.Tag.class));
    }

    @Test
    public void handleRequest_CreateDbCluster_TestStabilisation() {
        when(rdsProxy.client().createDBCluster(any(CreateDbClusterRequest.class)))
                .thenReturn(CreateDbClusterResponse.builder().build());
        when(rdsProxy.client().addRoleToDBCluster(any(AddRoleToDbClusterRequest.class)))
                .thenReturn(AddRoleToDbClusterResponse.builder().build());

        Queue<DBCluster> transitions = new ConcurrentLinkedQueue<>();
        transitions.add(DBCLUSTER_INPROGRESS);

        test_handleRequest_base(
                new CallbackContext(),
                () -> {
                    if (transitions.size() > 0) {
                        return transitions.remove();
                    }
                    return DBCLUSTER_ACTIVE;
                },
                () -> RESOURCE_MODEL,
                expectSuccess()
        );

        verify(rdsProxy.client(), times(1)).createDBCluster(any(CreateDbClusterRequest.class));
        verify(rdsProxy.client(), times(1)).addRoleToDBCluster(any(AddRoleToDbClusterRequest.class));
        verify(rdsProxy.client(), times(4)).describeDBClusters(any(DescribeDbClustersRequest.class));
    }

    @Test
    public void handleRequest_CreateDbCluster_AlreadyExists() {
        when(rdsProxy.client().createDBCluster(any(CreateDbClusterRequest.class)))
                .thenThrow(DbClusterAlreadyExistsException.builder().message("already exists").build());

        test_handleRequest_base(
                new CallbackContext(),
                null,
                () -> RESOURCE_MODEL,
                expectFailed(HandlerErrorCode.AlreadyExists)
        );

        verify(rdsProxy.client(), times(1)).createDBCluster(any(CreateDbClusterRequest.class));
    }

    @Test
    public void handleRequest_CreateDbCluster_RuntimeException() {
        when(rdsProxy.client().createDBCluster(any(CreateDbClusterRequest.class)))
                .thenThrow(new RuntimeException("test exception"));

        test_handleRequest_base(
                new CallbackContext(),
                null,
                () -> RESOURCE_MODEL,
                expectFailed(HandlerErrorCode.InternalFailure)
        );

        verify(rdsProxy.client(), times(1)).createDBCluster(any(CreateDbClusterRequest.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterFromSnapshot_ModifyAfterCreate() {
        when(rdsProxy.client().restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class)))
                .thenReturn(RestoreDbClusterFromSnapshotResponse.builder().build());
        when(rdsProxy.client().modifyDBCluster(any(ModifyDbClusterRequest.class)))
                .thenReturn(ModifyDbClusterResponse.builder().build());

        test_handleRequest_base(
                new CallbackContext(),
                () -> DBCLUSTER_ACTIVE,
                () -> RESOURCE_MODEL_ON_RESTORE,
                expectSuccess()
        );

        verify(rdsProxy.client(), times(1)).restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class));
        verify(rdsProxy.client(), times(1)).modifyDBCluster(any(ModifyDbClusterRequest.class));
        verify(rdsProxy.client(), times(3)).describeDBClusters(any(DescribeDbClustersRequest.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterFromSnapshot_AccessDeniedTagging() {
        when(rdsProxy.client().restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class)))
                .thenThrow(
                        RdsException.builder()
                                .awsErrorDetails(AwsErrorDetails.builder()
                                        .errorCode(ErrorCode.AccessDeniedException.toString())
                                        .build()
                                ).build())
                .thenReturn(RestoreDbClusterFromSnapshotResponse.builder().build());
        when(rdsProxy.client().modifyDBCluster(any(ModifyDbClusterRequest.class)))
                .thenReturn(ModifyDbClusterResponse.builder().build());
        when(rdsProxy.client().addTagsToResource(any(AddTagsToResourceRequest.class)))
                .thenReturn(AddTagsToResourceResponse.builder().build());

        final Tagging.TagSet extraTags = Tagging.TagSet.builder()
                .stackTags(TAG_SET.getStackTags())
                .resourceTags(TAG_SET.getResourceTags())
                .build();

        test_handleRequest_base(
                new CallbackContext(),
                ResourceHandlerRequest.<ResourceModel>builder()
                        .systemTags(Translator.translateTagsToRequest(Translator.translateTagsFromSdk(TAG_SET.getSystemTags())))
                        .desiredResourceTags(Translator.translateTagsToRequest(Translator.translateTagsFromSdk(TAG_SET.getStackTags()))),
                () -> DBCLUSTER_ACTIVE,
                null,
                () -> RESOURCE_MODEL_ON_RESTORE.toBuilder()
                        .tags(Translator.translateTagsFromSdk(TAG_SET.getResourceTags()))
                        .build(),
                expectSuccess()
        );

        ArgumentCaptor<RestoreDbClusterFromSnapshotRequest> createCaptor = ArgumentCaptor.forClass(RestoreDbClusterFromSnapshotRequest.class);
        verify(rdsProxy.client(), times(2)).restoreDBClusterFromSnapshot(createCaptor.capture());

        final RestoreDbClusterFromSnapshotRequest requestWithAllTags = createCaptor.getAllValues().get(0);
        final RestoreDbClusterFromSnapshotRequest requestWithSystemTags = createCaptor.getAllValues().get(1);
        Assertions.assertThat(requestWithAllTags.tags()).containsExactlyInAnyOrder(
                Iterables.toArray(Tagging.translateTagsToSdk(TAG_SET), software.amazon.awssdk.services.rds.model.Tag.class));
        Assertions.assertThat(requestWithSystemTags.tags()).containsExactlyInAnyOrder(
                Iterables.toArray(TAG_SET.getSystemTags(), software.amazon.awssdk.services.rds.model.Tag.class));

        verify(rdsProxy.client(), times(4)).describeDBClusters(any(DescribeDbClustersRequest.class));
        verify(rdsProxy.client(), times(1)).modifyDBCluster(any(ModifyDbClusterRequest.class));

        ArgumentCaptor<AddTagsToResourceRequest> addTagsCaptor = ArgumentCaptor.forClass(AddTagsToResourceRequest.class);
        verify(rdsProxy.client(), times(1)).addTagsToResource(addTagsCaptor.capture());
        Assertions.assertThat(addTagsCaptor.getValue().tags()).containsExactlyInAnyOrder(
                Iterables.toArray(Tagging.translateTagsToSdk(extraTags), software.amazon.awssdk.services.rds.model.Tag.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterFromSnapshot_Success() {
        when(rdsProxy.client().restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class)))
                .thenReturn(RestoreDbClusterFromSnapshotResponse.builder().build());

        final CallbackContext context = new CallbackContext();
        context.setModified(true);

        test_handleRequest_base(
                context,
                () -> DBCLUSTER_ACTIVE,
                () -> RESOURCE_MODEL_ON_RESTORE,
                expectSuccess()
        );

        verify(rdsProxy.client(), times(1)).restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class));
        verify(rdsProxy.client(), times(2)).describeDBClusters(any(DescribeDbClustersRequest.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterFromSnapshot_SetKmsKeyId() {
        when(rdsProxy.client().restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class)))
                .thenReturn(RestoreDbClusterFromSnapshotResponse.builder().build());

        final CallbackContext context = new CallbackContext();
        context.setModified(true);

        final String kmsKeyId = randomString(32, ALPHA);

        test_handleRequest_base(
                context,
                () -> DBCLUSTER_ACTIVE,
                () -> RESOURCE_MODEL_ON_RESTORE.toBuilder().kmsKeyId(kmsKeyId).build(),
                expectSuccess()
        );

        final ArgumentCaptor<RestoreDbClusterFromSnapshotRequest> argumentCaptor = ArgumentCaptor.forClass(RestoreDbClusterFromSnapshotRequest.class);
        verify(rdsProxy.client(), times(1)).restoreDBClusterFromSnapshot(argumentCaptor.capture());
        verify(rdsProxy.client(), times(2)).describeDBClusters(any(DescribeDbClustersRequest.class));

        Assertions.assertThat(argumentCaptor.getValue().kmsKeyId()).isEqualTo(kmsKeyId);
    }

    @Test
    public void handleRequest_RestoreDbClusterFromSnapshot_AlreadyExists() {
        when(rdsProxy.client().restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class)))
                .thenThrow(DbClusterAlreadyExistsException.builder().message("already exists").build());

        final CallbackContext context = new CallbackContext();
        context.setModified(true);

        test_handleRequest_base(
                context,
                null,
                () -> RESOURCE_MODEL_ON_RESTORE,
                expectFailed(HandlerErrorCode.AlreadyExists)
        );

        verify(rdsProxy.client(), times(1)).restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterFromSnapshot_RuntimeException() {
        when(rdsProxy.client().restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class)))
                .thenThrow(new RuntimeException("test exception"));

        final CallbackContext context = new CallbackContext();
        context.setModified(true);

        test_handleRequest_base(
                context,
                null,
                () -> RESOURCE_MODEL_ON_RESTORE,
                expectFailed(HandlerErrorCode.InternalFailure)
        );

        verify(rdsProxy.client(), times(1)).restoreDBClusterFromSnapshot(any(RestoreDbClusterFromSnapshotRequest.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterToPointInTime_Success() {
        when(rdsProxy.client().restoreDBClusterToPointInTime(any(RestoreDbClusterToPointInTimeRequest.class)))
                .thenReturn(RestoreDbClusterToPointInTimeResponse.builder().build());

        test_handleRequest_base(
                new CallbackContext(),
                () -> DBCLUSTER_ACTIVE,
                () -> RESOURCE_MODEL_ON_RESTORE_IN_TIME,
                expectSuccess()
        );

        verify(rdsProxy.client(), times(1)).restoreDBClusterToPointInTime(any(RestoreDbClusterToPointInTimeRequest.class));
        verify(rdsProxy.client(), times(2)).describeDBClusters(any(DescribeDbClustersRequest.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterToPointInTime_AccessDeniedTagging() {
        when(rdsProxy.client().restoreDBClusterToPointInTime(any(RestoreDbClusterToPointInTimeRequest.class)))
                .thenThrow(
                        RdsException.builder()
                                .awsErrorDetails(AwsErrorDetails.builder()
                                        .errorCode(ErrorCode.AccessDeniedException.toString())
                                        .build()
                                ).build())
                .thenReturn(RestoreDbClusterToPointInTimeResponse.builder().build());
        when(rdsProxy.client().addTagsToResource(any(AddTagsToResourceRequest.class)))
                .thenReturn(AddTagsToResourceResponse.builder().build());

        final Tagging.TagSet extraTags = Tagging.TagSet.builder()
                .stackTags(TAG_SET.getStackTags())
                .resourceTags(TAG_SET.getResourceTags())
                .build();

        test_handleRequest_base(
                new CallbackContext(),
                ResourceHandlerRequest.<ResourceModel>builder()
                        .systemTags(Translator.translateTagsToRequest(Translator.translateTagsFromSdk(TAG_SET.getSystemTags())))
                        .desiredResourceTags(Translator.translateTagsToRequest(Translator.translateTagsFromSdk(TAG_SET.getStackTags()))),
                () -> DBCLUSTER_ACTIVE,
                null,
                () -> RESOURCE_MODEL_ON_RESTORE_IN_TIME.toBuilder()
                        .tags(Translator.translateTagsFromSdk(TAG_SET.getResourceTags()))
                        .build(),
                expectSuccess()
        );

        ArgumentCaptor<RestoreDbClusterToPointInTimeRequest> createCaptor = ArgumentCaptor.forClass(RestoreDbClusterToPointInTimeRequest.class);
        verify(rdsProxy.client(), times(2)).restoreDBClusterToPointInTime(createCaptor.capture());

        final RestoreDbClusterToPointInTimeRequest requestWithAllTags = createCaptor.getAllValues().get(0);
        final RestoreDbClusterToPointInTimeRequest requestWithSystemTags = createCaptor.getAllValues().get(1);
        Assertions.assertThat(requestWithAllTags.tags()).containsExactlyInAnyOrder(
                Iterables.toArray(Tagging.translateTagsToSdk(TAG_SET), software.amazon.awssdk.services.rds.model.Tag.class));
        Assertions.assertThat(requestWithSystemTags.tags()).containsExactlyInAnyOrder(
                Iterables.toArray(TAG_SET.getSystemTags(), software.amazon.awssdk.services.rds.model.Tag.class));

        verify(rdsProxy.client(), times(3)).describeDBClusters(any(DescribeDbClustersRequest.class));

        ArgumentCaptor<AddTagsToResourceRequest> addTagsCaptor = ArgumentCaptor.forClass(AddTagsToResourceRequest.class);
        verify(rdsProxy.client(), times(1)).addTagsToResource(addTagsCaptor.capture());
        Assertions.assertThat(addTagsCaptor.getValue().tags()).containsExactlyInAnyOrder(
                Iterables.toArray(Tagging.translateTagsToSdk(extraTags), software.amazon.awssdk.services.rds.model.Tag.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterToPointInTime_AlreadyExists() {
        when(rdsProxy.client().restoreDBClusterToPointInTime(any(RestoreDbClusterToPointInTimeRequest.class)))
                .thenThrow(DbClusterAlreadyExistsException.builder().message("already exists").build());

        test_handleRequest_base(
                new CallbackContext(),
                null,
                () -> RESOURCE_MODEL_ON_RESTORE_IN_TIME,
                expectFailed(HandlerErrorCode.AlreadyExists)
        );

        verify(rdsProxy.client(), times(1)).restoreDBClusterToPointInTime(any(RestoreDbClusterToPointInTimeRequest.class));
    }

    @Test
    public void handleRequest_RestoreDbClusterToPointInTime_RuntimeException() {
        when(rdsProxy.client().restoreDBClusterToPointInTime(any(RestoreDbClusterToPointInTimeRequest.class)))
                .thenThrow(new RuntimeException("test exception"));

        test_handleRequest_base(
                new CallbackContext(),
                null,
                () -> RESOURCE_MODEL_ON_RESTORE_IN_TIME,
                expectFailed(HandlerErrorCode.InternalFailure)
        );

        verify(rdsProxy.client(), times(1)).restoreDBClusterToPointInTime(any(RestoreDbClusterToPointInTimeRequest.class));
    }
}
