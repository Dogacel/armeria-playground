package armeria.playground.app

import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.annotation.PathPrefix
import com.linecorp.armeria.server.auth.AuthService
import com.linecorp.armeria.server.auth.Authorizer
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.logging.LoggingService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

fun main() {
    val server =
        Server.builder()
            .http(8080)
            // Normal service with dummy auth
            .annotatedService()
            .pathPrefix("/normal")
            .decorator(AuthService.newDecorator(DummyAuthorizer()))
            .build(FooService())
            // Normal service with proxy auth
            .annotatedService()
            .pathPrefix("/proxied")
            .decorator(AuthService.newDecorator(DummyAuthorizer()))
            .decorator(AuthorizingProxyDecorator())
            .build(FooService())
            // Doc service
            .serviceUnder(
                "/docs",
                DocService()
                    .decorate(AuthService.newDecorator(DummyAuthorizer()))
                    .decorate(AuthorizingProxyDecorator())
                    .decorate(ShowLoginIfUnauthorizedDecorator()),
            )
            // Login and logout
            .service("/login", SinglePageLogin())
            .service("/logout", SinglePageLogout())
            // End
            .decorator(LoggingService.newDecorator())
            .build()

    server.start().join()
}

@PathPrefix("/foo")
class FooService {
    @Get("/")
    fun foo(): String {
        return "foo"
    }

    @Get("/bar")
    fun bar(): String {
        return "bar"
    }
}

class DummyAuthorizer : Authorizer<HttpRequest> {
    override fun authorize(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): CompletionStage<Boolean> {
        val authHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION)

        println("[DummyAuthorizer] Authorization: $authHeader")

        return CompletableFuture.completedFuture(authHeader?.startsWith("Bearer") ?: false)
    }
}
