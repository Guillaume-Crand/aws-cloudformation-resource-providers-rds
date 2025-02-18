package software.amazon.rds.common.test;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.assertj.core.api.Assertions;

import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class AbstractTestBase<ResourceT, ModelT, CallbackT> {

    final private static SecureRandom random = new SecureRandom();
    final public static String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    final public static String ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    protected abstract String getLogicalResourceIdentifier();

    protected abstract void expectResourceSupply(final Supplier<ResourceT> supplier);

    protected abstract ProgressEvent<ModelT, CallbackT> invokeHandleRequest(ResourceHandlerRequest<ModelT> request, CallbackT context);

    protected String newClientRequestToken() {
        return UUID.randomUUID().toString();
    }

    protected String newStackId() {
        return UUID.randomUUID().toString();
    }

    protected String randomString(final int length, final String alphabet) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    protected Consumer<ProgressEvent<ModelT, CallbackT>> expectInProgress(int pause) {
        return (response) -> {
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
            Assertions.assertThat(response.getCallbackDelaySeconds()).isEqualTo(pause);
            Assertions.assertThat(response.getResourceModels()).isNull();
            Assertions.assertThat(response.getMessage()).isNull();
            Assertions.assertThat(response.getErrorCode()).isNull();
        };
    }

    protected Consumer<ProgressEvent<ModelT, CallbackT>> expectSuccess() {
        return (response) -> {
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            Assertions.assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
            Assertions.assertThat(response.getMessage()).isNull();
            Assertions.assertThat(response.getErrorCode()).isNull();
        };
    }

    protected Consumer<ProgressEvent<ModelT, CallbackT>> expectFailed(final HandlerErrorCode errorCode) {
        return (response) -> {
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
            Assertions.assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
            Assertions.assertThat(response.getMessage()).isNotNull();
            Assertions.assertThat(response.getErrorCode()).isEqualTo(errorCode);
            Assertions.assertThat(response.getResourceModels()).isNull();
        };
    }

    protected ProgressEvent<ModelT, CallbackT> test_handleRequest_base(
            final CallbackT context,
            final Supplier<ResourceT> resourceSupplier,
            final Supplier<ModelT> desiredStateSupplier,
            final Consumer<ProgressEvent<ModelT, CallbackT>> expect
    ) {
        return test_handleRequest_base(context, resourceSupplier, null, desiredStateSupplier, expect);
    }

    protected ProgressEvent<ModelT, CallbackT> test_handleRequest_base(
            final CallbackT context,
            final Supplier<ResourceT> resourceSupplier,
            final Supplier<ModelT> previousStateSupplier,
            final Supplier<ModelT> desiredStateSupplier,
            final Consumer<ProgressEvent<ModelT, CallbackT>> expect
    ) {
        return test_handleRequest_base(
                context,
                ResourceHandlerRequest.<ModelT>builder(),
                resourceSupplier,
                previousStateSupplier,
                desiredStateSupplier,
                expect
        );
    }

    protected ProgressEvent<ModelT, CallbackT> test_handleRequest_base(
            final CallbackT context,
            final ResourceHandlerRequest.ResourceHandlerRequestBuilder<ModelT> builder,
            final Supplier<ResourceT> resourceSupplier,
            final Supplier<ModelT> previousStateSupplier,
            final Supplier<ModelT> desiredStateSupplier,
            final Consumer<ProgressEvent<ModelT, CallbackT>> expect
    ) {
        if (resourceSupplier != null) {
            expectResourceSupply(resourceSupplier);
        }

        builder.desiredResourceState(desiredStateSupplier.get());
        if (previousStateSupplier != null) {
            builder.previousResourceState(previousStateSupplier.get());
        }
        builder.logicalResourceIdentifier(getLogicalResourceIdentifier());
        builder.clientRequestToken(newClientRequestToken());
        builder.stackId(newStackId());

        final ProgressEvent<ModelT, CallbackT> response = invokeHandleRequest(builder.build(), context);
        expect.accept(response);

        return response;
    }

}
