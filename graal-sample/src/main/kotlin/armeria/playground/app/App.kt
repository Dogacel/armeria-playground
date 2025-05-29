package armeria.playground.app

import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.docs.DocService

suspend fun main() {
    val server =
        Server
            .builder()
            .http(8080)
            .serviceUnder("/docs", DocService())
            .build()

    server.start().join()
}
