package armeria.playground.app

import com.example.Msg
import com.example.SampleServiceGrpcKt
import com.example.SampleServiceGrpcKt.SampleServiceCoroutineImplBase
import com.example.msg
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linecorp.armeria.client.grpc.GrpcClients
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.annotation.PathPrefix
import com.linecorp.armeria.server.annotation.Post
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.grpc.GrpcService
import io.grpc.Status
import org.slf4j.LoggerFactory

suspend fun main() {
    val grpcService =
        GrpcService
            .builder()
            .addService(SampleServiceImpl())
            .enableUnframedRequests(true)
            .build()

    val server =
        Server.builder()
            .verboseResponses(true)
            .http(8080)
            .serviceUnder("/", grpcService)
            .serviceUnder("/docs", DocService())
            .annotatedService(ConsensusService())
            .build()

    server.start().join()

    val stub =
        GrpcClients
            .builder("http://localhost:" + server.activeLocalPort())
            .build(SampleServiceGrpcKt.SampleServiceCoroutineStub::class.java)

    stub.echoMsg(msg { })

    runCatching { stub.throwStatus(msg { }) }.onFailure {
        println("Got: $it")
    }

//    server.stop()
}

class SampleServiceImpl : SampleServiceCoroutineImplBase() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun echoMsg(request: Msg): Msg {
        return request
    }

    override suspend fun throwStatus(request: Msg): Msg {
        throw Status.INVALID_ARGUMENT
            .withCause(IllegalArgumentException("I am the reason."))
            .withDescription("Hey, it failed.")
            .asException()
    }
}

@PathPrefix("/consensus")
class ConsensusService {
    private val me = Facilitator("foo-service", Channel("foo-engs@gmail.com"))

    data class HttpMethodSpec(val method: HttpMethod, val path: String)

    private val consensusManager =
        object : ConsensusManager<HttpMethodSpec>(me) {
            override fun canAgree(
                participant: Participant,
                spec: HttpMethodSpec,
            ): Boolean {
                if (participant.id == "foo-service-client") {
                    return concensuses.contains(
                        Agreement(
                            facilitator,
                            participant,
                            spec,
                        ),
                    ).not()
                }

                return false
            }
        }

    private val objectMapper = ObjectMapper()

    @Post("/participate")
    fun register(
        ctx: ServiceRequestContext,
        req: HttpRequest,
    ): HttpResponse {
        val participantId = req.headers().get("X-Participant-Id")
        val participantSlug = req.headers().get("X-Participant-Slug")

        if (participantId == null) {
            return HttpResponse.of("Missing X-Participant-Id")
        }

        if (participantSlug == null) {
            return HttpResponse.of("Missing X-Participant-Slug")
        }

        val canAgree =
            req.aggregate().thenApply { result ->
                val bodyJson = objectMapper.readValue(result.contentUtf8(), JsonNode::class.java)
                consensusManager.agree(
                    Participant(
                        slug = participantSlug,
                        id = participantId,
                        channel = Channel("unknown"),
                    ),
                    HttpMethodSpec(
                        method = bodyJson.get("method").let { HttpMethod.valueOf(it.asText()) },
                        path = bodyJson.get("path").asText(),
                    ),
                )
            }

        return HttpResponse.of(
            canAgree.thenApply {
                HttpResponse.of(
                    if (it) "Agreed" else "Disagreed",
                )
            },
        )
    }
}
