package armeria.playground.app

import com.example.Msg
import com.example.SampleServiceGrpcKt
import com.example.SampleServiceGrpcKt.SampleServiceCoroutineImplBase
import com.example.msg
import com.linecorp.armeria.client.grpc.GrpcClients
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.grpc.GrpcService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

suspend fun main() {
    val grpcService =
        GrpcService
            .builder()
            .addService(SampleServiceImpl())
            .enableHealthCheckService(true)
            .enableUnframedRequests(true)
            .build()

    val server =
        Server.builder()
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

    server.stop()
}

class SampleServiceImpl : SampleServiceCoroutineImplBase() {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val src: ServiceRequestContext? get() = ServiceRequestContext.currentOrNull()

    override suspend fun echoMsg(request: Msg): Msg {
        val currentContext = src

        logger.info("Function body: {}", src)

        withContext(Dispatchers.IO) {
            logger.info("With context Dispatchers.IO: {}", src)

            require(currentContext == src) {
                "ServiceRequestContext should be propagated to the `withContext`"
            }

            launch {
                logger.info(
                    "With context Dispatchers.IO and launch context: {}",
                    ServiceRequestContext.current(),
                )

                require(currentContext == src) {
                    "ServiceRequestContext should be propagated to the `withContext.launch`"
                }
            }
        }

        coroutineScope {
            logger.info("Coroutine scope service request context: {}", src)

            require(currentContext == src) {
                "ServiceRequestContext should be propagated to the `coroutineScope`"
            }

            launch(Dispatchers.IO) {
                logger.info(
                    "Coroutine scope service request context and launch context: {}",
                    src,
                )

                require(currentContext == src) {
                    "ServiceRequestContext should be propagated to the `coroutineScope.launch`"
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            logger.info("Coroutine scope service request context: {}", src)

            require(currentContext != src) {
                "ServiceRequestContext is not propagated to the `CoroutineScope(Dispatchers.IO).launch`"
            }
        }

        return request
    }
}
