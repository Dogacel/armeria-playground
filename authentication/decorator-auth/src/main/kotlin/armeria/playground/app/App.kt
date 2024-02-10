package armeria.playground.app

import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.SimpleDecoratingHttpService
import com.linecorp.armeria.server.annotation.DecoratorFactory
import com.linecorp.armeria.server.annotation.DecoratorFactoryFunction
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.annotation.PathPrefix
import com.linecorp.armeria.server.auth.AuthService
import com.linecorp.armeria.server.auth.Authorizer
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.logging.LoggingService
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Function

fun main() {
    val server =
        Server.builder()
            .http(8080)
            // Normal service with dummy auth
            .annotatedService()
            .pathPrefix("/normal")
            .build(FooService())
            // Need auth by default
            .annotatedService()
            .pathPrefix("/needsAuth")
            .decorator(NeedsAuthDecoratorFactory.newDecorator())
            .build(FooService())
            // Doc service
            .serviceUnder("/docs", DocService())
            .decorator(LoggingService.newDecorator())
            .build()

    server.start().join()

    server.stop()
}

@NeedsAuth
@PathPrefix("/")
class FooService {
    @NeedsAuth
    @Get("/foo")
    fun foo(): String {
        return "foo"
    }

    @PublicApi
    @Get("/bar")
    fun bar(): String {
        return "bar"
    }

    @Get("/baz")
    fun baz(): String {
        return "baz"
    }

    @NeedsAuth
    @PublicApi
    @Get("/qux")
    fun qux(): String {
        return "qux"
    }

    @PublicApi
    @NeedsAuth
    @Get("/quux")
    fun quux(): String {
        return "quux"
    }
}

@DecoratorFactory(NeedsAuthDecoratorFactory::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class NeedsAuth

@DecoratorFactory(PublicApiDecoratorFactory::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class PublicApi

class NeedsAuthDecoratorFactory : DecoratorFactoryFunction<NeedsAuth> {
    companion object {
        fun newDecorator(): Function<in HttpService, out HttpService> {
            return Function { delegate ->
                val maybePublic: PublicApiService? = delegate.`as`(PublicApiService::class.java)
                val maybeAuthenticated: NeedsAuthService? = delegate.`as`(NeedsAuthService::class.java)

                if (maybePublic != null || maybeAuthenticated != null) {
                    return@Function delegate
                }

                NeedsAuthService(delegate)
            }
        }
    }

    override fun newDecorator(parameter: NeedsAuth): Function<in HttpService, out HttpService> {
        return newDecorator()
    }
}

class PublicApiDecoratorFactory : DecoratorFactoryFunction<PublicApi> {
    companion object {
        fun newDecorator(): Function<in HttpService, out HttpService> {
            return Function { delegate ->
                val maybePublic: PublicApiService? = delegate.`as`(PublicApiService::class.java)

                if (maybePublic != null) {
                    return@Function delegate
                }

                PublicApiService(delegate)
            }
        }
    }

    override fun newDecorator(parameter: PublicApi): Function<in HttpService, out HttpService> {
        return newDecorator()
    }
}

class NeedsAuthService(delegate: HttpService) : SimpleDecoratingHttpService(delegate) {
    private val authWrappedService = AuthService.newDecorator(DummyAuthorizer()).apply(delegate)

    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        return authWrappedService.serve(ctx, req)
    }
}

class PublicApiService(private val delegate: HttpService) : SimpleDecoratingHttpService(delegate) {
    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        return delegate.serve(ctx, req)
    }
}

class DummyAuthorizer : Authorizer<HttpRequest> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun authorize(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): CompletionStage<Boolean> {
        val authHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION)

        logger.info("Authorization header: $authHeader")

        return CompletableFuture.completedFuture(authHeader?.startsWith("Bearer") ?: false)
    }
}
