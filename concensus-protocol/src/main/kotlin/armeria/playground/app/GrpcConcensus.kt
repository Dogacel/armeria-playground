package armeria.playground.app

import com.google.protobuf.Descriptors

abstract class ConsensusManager<Spec>(val facilitator: Facilitator) {
    val concensuses = mutableSetOf<Agreement<Spec>>()

    abstract fun canAgree(
        participant: Participant,
        spec: Spec,
    ): Boolean

    fun agree(
        participant: Participant,
        spec: Spec,
    ): Boolean {
        return if (canAgree(participant, spec)) {
            concensuses.add(Agreement(facilitator, participant, spec))
        } else {
            false
        }
    }

    fun getParticipants(facilitator: Facilitator): Set<Participant> {
        return concensuses
            .filter { it.facilitator == facilitator }
            .map { it.participant }
            .toSet()
    }

    fun getAgreements(facilitator: Facilitator): Set<Agreement<Spec>> {
        return concensuses
            .filter { it.facilitator == facilitator }
            .toSet()
    }

    fun getAgreements(participant: Participant): Set<Agreement<Spec>> {
        return concensuses
            .filter { it.participant == participant }
            .toSet()
    }

    fun getAgreements(
        specFilter: Spec.() -> Boolean = { true },
        participantFilter: Participant.() -> Boolean = { true },
        facilitatorFilter: Facilitator.() -> Boolean = { true },
    ): Set<Agreement<Spec>> {
        return concensuses
            .filter { it.spec.specFilter() }
            .filter { it.participant.participantFilter() }
            .filter { it.facilitator.facilitatorFilter() }
            .toSet()
    }
}

data class Channel(
    val email: String? = null,
    val other: Set<String> = setOf(),
)

data class Facilitator(
    val slug: String,
    val channel: Channel,
)

data class Participant(
    val slug: String,
    val id: String,
    val channel: Channel,
)

data class Agreement<Spec>(
    val facilitator: Facilitator,
    val participant: Participant,
    val spec: Spec,
)

typealias GrpcMethodAgreement = Agreement<Descriptors.MethodDescriptor>
typealias GrpcServiceAgreement = Agreement<Descriptors.ServiceDescriptor>
