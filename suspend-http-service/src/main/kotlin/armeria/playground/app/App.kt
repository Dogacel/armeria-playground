@file:OptIn(ExperimentalStdlibApi::class)

package armeria.playground.app

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.kotlin.asCoroutineContext
import com.linecorp.armeria.common.kotlin.asCoroutineDispatcher
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.docs.DocService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

suspend fun main() {
    val server =
        Server.builder()
            .http(8080)
            .serviceUnder("/docs", DocService())
            .service("/sample/blocking", SampleBlockingService())
            .service("/sample/eventloop", SampleEventLoopService())
            .service("/sample/default", SampleDefaultDispatcherService())
            .service("/sample/io", SampleIODispatcherService())
            .build()

    server.start().join()

    val stub =
        WebClient
            .builder("http://localhost:" + server.activeLocalPort())
            .build()

    stub.get("/sample/blocking").aggregate().await().also { println(it.contentUtf8()) }
    stub.get("/sample/eventloop").aggregate().await().also { println(it.contentUtf8()) }
    stub.get("/sample/default").aggregate().await().also { println(it.contentUtf8()) }
    stub.get("/sample/io").aggregate().await().also { println(it.contentUtf8()) }

    server.stop()
}

class SampleBlockingService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())
        val result =
            CoroutineScope(ctx.blockingTaskExecutor().asCoroutineDispatcher()).async {
                logger.info(
                    "Inside blocking task executor, processing a request: {}",
                    coroutineContext[CoroutineDispatcher.Key],
                )
                suspendServe()
            }.asCompletableFuture()

        return HttpResponse.of(result)
    }

    private suspend fun suspendServe(): HttpResponse {
        // Do something
        logger.info("Processing a request...")
        delay(100.milliseconds)
        return HttpResponse.of("Hello, world!")
    }
}

class SampleEventLoopService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())
        val result =
            CoroutineScope(ctx.eventLoop().asCoroutineDispatcher()).async {
                logger.info(
                    "Inside event loop, processing a request: {}",
                    coroutineContext[CoroutineDispatcher.Key],
                )
                suspendServe()
            }.asCompletableFuture()

        return HttpResponse.of(result)
    }

    private suspend fun suspendServe(): HttpResponse {
        // Do something
        logger.info("Processing a request...")
        delay(100.milliseconds)
        return HttpResponse.of("Hello, world!")
    }
}

class SampleDefaultDispatcherService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())

        val result =
            CoroutineScope(ctx.asCoroutineContext()).async {
                logger.info(
                    "Inside unmanaged service, processing a request: {}",
                    coroutineContext[CoroutineDispatcher.Key],
                )
                suspendServe()
            }.asCompletableFuture()

        return HttpResponse.of(result)
    }

    private suspend fun suspendServe(): HttpResponse {
        // Do something
        logger.info("Processing a request...")
        delay(100.milliseconds)
        return HttpResponse.of("Hello, world!")
    }
}

class SampleIODispatcherService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())

        val result =
            CoroutineScope(ctx.asCoroutineContext() + Dispatchers.IO).async {
                logger.info(
                    "Inside unmanaged service, processing a request: {}",
                    coroutineContext[CoroutineDispatcher.Key],
                )
                suspendServe()
            }.asCompletableFuture()

        return HttpResponse.of(result)
    }

    private suspend fun suspendServe(): HttpResponse {
        // Do something
        logger.info("Processing a request...")
        delay(100.milliseconds)
        return HttpResponse.of("Hello, world!")
    }
}
