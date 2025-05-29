@file:OptIn(ExperimentalStdlibApi::class)

package armeria.playground.app

import com.example.Msg
import com.example.SampleServiceGrpcKt
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.kotlin.asCoroutineContext
import com.linecorp.armeria.common.kotlin.asCoroutineDispatcher
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.grpc.GrpcService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.pool.HikariProxyConnection
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.withSuspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("App")

suspend fun main() {
//    Database.connect(
//        "jdbc:postgresql://localhost:5432/armeria-playground",
//        driver = "org.postgresql.Driver",
//        user = "md",
//        password = "md"
//    )

    Database.connect(
        HikariDataSource(
            HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = "jdbc:postgresql://localhost:5432/armeria-playground"
                username = "md"
                password = "md"
                maximumPoolSize = 3
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }
        )
    )

    val server =
        Server.builder()
            .http(8080)
            .service(
                GrpcService.builder()
                    .addService(DelayServiceImpl())
                    .enableHttpJsonTranscoding(true)
                    .enableUnframedRequests(true)
                    .useBlockingTaskExecutor(true)
                    .build()
            )
            .serviceUnder("/docs", DocService())
            .annotatedService("/sample/annotated", SampleAnnotatedService())
            .service("/sample/blocking", SampleBlockingService())
            .service("/sample/eventloop", SampleEventLoopService())
            .service("/sample/default", SampleDefaultDispatcherService())
            .service("/sample/io", SampleIODispatcherService())
            .requestTimeoutMillis(30_000)
            .build()

    server.start().join()
}

class DelayServiceImpl : SampleServiceGrpcKt.SampleServiceCoroutineImplBase() {
    override suspend fun delay(request: Msg): Msg {
        longTransaction(request.millis.milliseconds)
        return request
    }
}

class SampleAnnotatedService {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Get
    suspend fun get(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse {
        logger.info("Received a request: {}", req.path())
        val duration = ctx.queryParam("duration")!!.toLong().milliseconds

        longTransaction(duration)
        return HttpResponse.of(200)
    }
}

class SampleBlockingService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())
        val duration = ctx.queryParam("duration")!!.toLong().milliseconds

        val result =
            CoroutineScope(ctx.blockingTaskExecutor().asCoroutineDispatcher()).async {
                logger.info(
                    "Inside blocking task executor, processing a request: {}",
                    coroutineContext[CoroutineDispatcher.Key],
                )
                longTransaction(duration)
                HttpResponse.of(200)
            }.asCompletableFuture()

        return HttpResponse.of(result)
    }
}

class SampleEventLoopService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())
        val duration = ctx.queryParam("duration")!!.toLong().milliseconds

        val result =
            CoroutineScope(ctx.eventLoop().asCoroutineDispatcher()).async {
                logger.info(
                    "Inside event loop, processing a request: {}",
                    coroutineContext[CoroutineDispatcher.Key],
                )
                longTransaction(duration)
                HttpResponse.of(200)
            }.asCompletableFuture()

        return HttpResponse.of(result)
    }
}

class SampleDefaultDispatcherService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())

        val duration = ctx.queryParam("duration")!!.toLong().milliseconds

        val result = CoroutineScope(ctx.asCoroutineContext()).async {
            logger.info(
                "Inside default dispatcher service, processing a request: {}",
                coroutineContext[CoroutineDispatcher.Key],
            )
            longTransaction(duration)
            HttpResponse.of(200)
        }.asCompletableFuture()

        return HttpResponse.of(result)
    }
}

class SampleIODispatcherService : HttpService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        logger.info("Received a request: {}", req.path())
        val duration = ctx.queryParam("duration")!!.toLong().milliseconds

        val result = CoroutineScope(ctx.asCoroutineContext() + Dispatchers.IO).async {
            logger.info(
                "Inside io dispatcher service, processing a request: {}",
                coroutineContext[CoroutineDispatcher.Key],
            )
            longTransaction(duration)
            HttpResponse.of(200)
        }.asCompletableFuture()

        return HttpResponse.of(result)
    }
}

suspend fun longTransaction(duration: Duration) {
    newSuspendedTransaction {
        withSuspendTransaction {

            logger.info("Starting long transaction with duration: $duration")
            exec("SELECT 1")
            logger.info("Inside long transaction")
            exec("SELECT pg_sleep(${duration.inWholeSeconds})")
            logger.info("Completing long transaction")
            exec("SELECT pg_sleep(3)")
            exec("SELECT 1")
        }
    }
}
