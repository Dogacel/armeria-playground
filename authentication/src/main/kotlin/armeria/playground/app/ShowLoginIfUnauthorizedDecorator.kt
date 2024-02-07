package armeria.playground.app

import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.kotlin.asCoroutineContext
import com.linecorp.armeria.server.DecoratingHttpServiceFunction
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

class ShowLoginIfUnauthorizedDecorator : DecoratingHttpServiceFunction {
    override fun serve(
        delegate: HttpService,
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        val futureResult =
            CoroutineScope(ctx.asCoroutineContext()).future<HttpResponse> {
                val response = delegate.serve(ctx, req)
                val responseAggregate = response.aggregate().await()

                if (responseAggregate.status() == HttpStatus.UNAUTHORIZED) {
                    return@future HttpResponse.ofRedirect("/login?redirect=${ctx.path()}")
                }

                responseAggregate.toHttpResponse()
            }

        return HttpResponse.of(futureResult)
    }
}
