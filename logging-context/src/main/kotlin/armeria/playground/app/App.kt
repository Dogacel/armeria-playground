package armeria.playground.app

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.server.DecoratingHttpServiceFunction
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.annotation.PathPrefix
import com.linecorp.armeria.server.docs.DocService
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory

suspend fun main() {
    val server =
        Server.builder()
            .http(8080)
            .serviceUnder("/docs", DocService())
            .annotatedService(SampleService())
            .decorator(FooInsertDecorator())
            .build()

    server.start().join()

    val stub =
        WebClient
            .builder("http://localhost:" + server.activeLocalPort())
            .build()

    stub.get("/hello").aggregate().await().also { println(it.contentUtf8()) }

    server.stop()
}

class FooInsertDecorator : DecoratingHttpServiceFunction {
    override fun serve(
        delegate: HttpService,
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        ctx.setAttr(AttrKeys.FOO_KEY, "foo")
        return delegate.serve(ctx, req)
    }
}

@PathPrefix("/")
class SampleService {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Get("/hello")
    suspend fun hello(): HttpResponse {
        logger.info(ServiceRequestContext.current().attr(AttrKeys.FOO_KEY))

        logger.info("Received a request.")

        return HttpResponse.of("Hello, world!")
    }
}
