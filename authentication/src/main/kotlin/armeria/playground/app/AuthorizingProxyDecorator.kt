package armeria.playground.app

import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.server.DecoratingHttpServiceFunction
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext
import org.slf4j.LoggerFactory

/**
 * Authorizes requests based on cookies set by [[SinglePageLogin]].
 */
class AuthorizingProxyDecorator : DecoratingHttpServiceFunction {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun serve(
        delegate: HttpService,
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        if (req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            return delegate.serve(ctx, req)
        }

        logger.debug("No auth header found.")

        val cookies = req.headers().cookies()

        val username = cookies.find { it.name() == "username" }?.value()
        val password = cookies.find { it.name() == "password" }?.value()

        if (username != null && password != null) {
            logger.debug("Found auth cookies, authorizing.")
            val bearer = "Bearer $username:$password"
            val reqWithBearer =
                req.withHeaders(req.headers().toBuilder().set(HttpHeaderNames.AUTHORIZATION, bearer).build())
            return delegate.serve(ctx, reqWithBearer)
        } else {
            logger.debug("No auth cookies found, skipping authorization.")
            return delegate.serve(ctx, req)
        }
    }
}
