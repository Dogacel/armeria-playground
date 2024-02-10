package armeria.playground.app

import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.kotlin.asCoroutineContext
import com.linecorp.armeria.server.AbstractHttpService
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory

class SinglePageLogin : AbstractHttpService() {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val classLoader = ClassLoader.getSystemClassLoader()

    private fun getLoginPage(): String {
        val loginPage = classLoader.getResourceAsStream("login.html")

        if (loginPage == null) {
            return "Internal server error"
        }

        // Get java resource `login.html` as string
        return loginPage.bufferedReader().use { it.readText() }
    }

    override fun doGet(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        return HttpResponse.of(MediaType.HTML_UTF_8, getLoginPage())
    }

    override fun doPost(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        val futureResult =
            CoroutineScope(ctx.asCoroutineContext()).future<HttpResponse> {
                if (ctx.queryParam("logout") == "true") {
                    ctx.mutateAdditionalResponseHeaders {
                        it.add(HttpHeaderNames.SET_COOKIE, "username=; Max-Age=0")
                        it.add(HttpHeaderNames.SET_COOKIE, "password=; Max-Age=0")
                    }
                    return@future HttpResponse.of("Logged out")
                }

                val requestAggregate = ctx.request().aggregate().await()
                val formData =
                    requestAggregate.contentUtf8().split("&").associate {
                        it.split('=')[0] to it.split('=')[1]
                    }

                val username = formData["username"]
                val password = formData["password"]

                ctx.mutateAdditionalResponseHeaders {
                    it.add(HttpHeaderNames.SET_COOKIE, "username=$username; Max-Age=3600")
                    it.add(HttpHeaderNames.SET_COOKIE, "password=$password; Max-Age=3600")
                }

                if (ctx.queryParam("redirect") != null) {
                    HttpResponse.ofRedirect(HttpStatus.MOVED_PERMANENTLY, ctx.queryParam("redirect")!!)
                } else {
                    HttpResponse.of(formData.toString())
                }
            }

        return HttpResponse.of(futureResult)
    }
}

class SinglePageLogout : HttpService {
    override fun serve(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        ctx.mutateAdditionalResponseHeaders {
            it.add(HttpHeaderNames.SET_COOKIE, "username=; Max-Age=0")
            it.add(HttpHeaderNames.SET_COOKIE, "password=; Max-Age=0")
        }

        return HttpResponse.ofRedirect("/login")
    }
}
