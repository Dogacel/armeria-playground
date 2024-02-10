package armeria.playground.app

import com.example.Msg
import com.example.SampleServiceGrpcKt
import com.example.SampleServiceGrpcKt.SampleServiceCoroutineImplBase
import com.example.msg
import com.linecorp.armeria.client.grpc.GrpcClients
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.internal.common.grpc.GrpcStatus
import com.linecorp.armeria.server.DecoratingHttpServiceFunction
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.annotation.Decorator
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.grpc.GrpcService
import io.grpc.Status
import org.slf4j.LoggerFactory

suspend fun main() {
    val grpcService =
        GrpcService
            .builder()
            .addService(SampleServiceImpl())
            .enableUnframedRequests(true)
            .build()

    val server =
        Server.builder()
            .verboseResponses(true)
            .http(8080)
            .serviceUnder("/", grpcService)
            .serviceUnder("/docs", DocService())
            .build()

    server.start().join()

    val stub =
        GrpcClients
            .builder("http://localhost:" + server.activeLocalPort())
            .build(SampleServiceGrpcKt.SampleServiceCoroutineStub::class.java)

    stub.echoMsg(msg { })

    runCatching { stub.throwStatus(msg { }) }.onFailure {
        println("Got: $it")
    }

    server.stop()
}

class Decorator1 : DecoratingHttpServiceFunction {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        delegate: HttpService,
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Request received: {}", req)

        req.aggregate().thenApply {
            logger.info("Request route: {}", req.path())
            logger.info("Request method: {}", req.method())
            logger.info("Content type: {}", it.contentType())
            logger.info("Request content: {}", it.contentUtf8())
            logger.info("Request content: {}", it.content())
        }

        val response = delegate.serve(ctx, req)

        return HttpResponse.of(
            response.aggregate().thenApply {
                logger.info("Response status: {}", it.status())
                logger.info("Response headers: {}", it.headers())
                logger.info("Response content type: {}", it.contentType())
                logger.info("Response content: {}", it.contentUtf8())
                logger.info("Response content: {}", it.content())

                if (it.headers().contains("grpc-status")) {
                    GrpcStatus.reportStatus(
                        it.headers(),
                    ) { status, metadata ->
                        logger.info("gRPC status: {} {}", status, metadata)
                    }
                }

                it.toHttpResponse()
            },
        )
    }
}

@Decorator(Decorator1::class)
class SampleServiceImpl : SampleServiceCoroutineImplBase() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun echoMsg(request: Msg): Msg {
        return request
    }

    override suspend fun throwStatus(request: Msg): Msg {
        throw Status.INVALID_ARGUMENT
            .withCause(IllegalArgumentException("I am the reason."))
            .withDescription("Hey, it failed.")
            .asException()
    }
}
