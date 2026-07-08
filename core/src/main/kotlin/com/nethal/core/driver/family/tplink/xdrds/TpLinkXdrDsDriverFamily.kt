package com.nethal.core.driver.family.tplink.xdrds

import com.nethal.core.catalog.CapabilityReadResult
import com.nethal.core.catalog.CompatibilityProfile
import com.nethal.core.catalog.DriverFamily
import com.nethal.core.catalog.DriverFamilyFactory
import com.nethal.core.discovery.PrivateIpRanges
import com.nethal.core.driver.NetworkFailureReason
import com.nethal.core.driver.RetryOutcome
import com.nethal.core.driver.classifyNetworkFailure
import com.nethal.core.driver.executeWithRetry
import com.nethal.core.model.CapabilityId
import com.nethal.core.protocol.http.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class TpLinkXdrDsFailureReason {
    DEVICE_UNREACHABLE,
    TIMEOUT,
    UNEXPECTED_RESPONSE,
    INVALID_CREDENTIALS,
    COMMUNICATION_ERROR,
}

internal sealed interface TpLinkXdrDsLoginOutcome {
    data class Success(val session: TpLinkXdrDsSession) : TpLinkXdrDsLoginOutcome
    data class Failure(val reason: TpLinkXdrDsFailureReason, val message: String) : TpLinkXdrDsLoginOutcome
}

internal sealed interface TpLinkXdrDsReadOutcome {
    data class Success(val rawBody: String) : TpLinkXdrDsReadOutcome
    data class Failure(val reason: TpLinkXdrDsFailureReason, val message: String) : TpLinkXdrDsReadOutcome
}

internal class TpLinkXdrDsDriverFamily(
    private val host: String,
    private val config: TpLinkXdrDsDriverConfig,
    private val transport: HttpTransport,
    private val maxAttempts: Int = 2,
    private val backoffMillis: (attempt: Int) -> Long = { attempt -> 1_000L * attempt },
) : DriverFamily {

    init {
        require(PrivateIpRanges.isPrivate(host)) {
            "TpLinkXdrDsDriverFamily só opera contra IP privado (RFC 1918) da LAN; host recebido: $host"
        }
    }

    suspend fun login(username: String, password: String): TpLinkXdrDsLoginOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkXdrDsLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkXdrDsLoginFailureReason.INVALID_CREDENTIALS -> TpLinkXdrDsFailureReason.INVALID_CREDENTIALS
                    TpLinkXdrDsLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                    TpLinkXdrDsLoginFailureReason.UNEXPECTED_RESPONSE,
                    -> null
                }
            },
            classifyFinalFailure = ::classifyFailure,
        ) {
            TpLinkXdrDsAuthenticationClient(host, config, transport).login(username, password)
        }
        when (outcome) {
            is RetryOutcome.Success -> TpLinkXdrDsLoginOutcome.Success(outcome.value)
            is RetryOutcome.Failure -> TpLinkXdrDsLoginOutcome.Failure(
                outcome.reason,
                outcome.error.message ?: outcome.error.toString(),
            )
        }
    }

    suspend fun readRaw(username: String, password: String): TpLinkXdrDsReadOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkXdrDsLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkXdrDsLoginFailureReason.INVALID_CREDENTIALS -> TpLinkXdrDsFailureReason.INVALID_CREDENTIALS
                    TpLinkXdrDsLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                    TpLinkXdrDsLoginFailureReason.UNEXPECTED_RESPONSE,
                    -> null
                }
            },
            classifyFinalFailure = ::classifyFailure,
        ) {
            val client = TpLinkXdrDsAuthenticationClient(host, config, transport)
            client.login(username, password)
            client.fetchAuthenticatedRaw(config.authenticatedReadPayloadJson)
        }
        when (outcome) {
            is RetryOutcome.Success -> TpLinkXdrDsReadOutcome.Success(outcome.value)
            is RetryOutcome.Failure -> TpLinkXdrDsReadOutcome.Failure(
                outcome.reason,
                outcome.error.message ?: outcome.error.toString(),
            )
        }
    }

    override suspend fun readCapability(id: CapabilityId): CapabilityReadResult =
        CapabilityReadResult.Unavailable(
            reason = "TpLinkXdrDsDriverFamily ja implementa login e leitura JSON bruta do endpoint /ds, " +
                "mas ainda nao possui parser estruturado por capability nem sessao gerenciada pelo Capability Engine.",
        )

    private fun classifyFailure(error: Throwable): TpLinkXdrDsFailureReason = when (classifyNetworkFailure(error)) {
        NetworkFailureReason.DEVICE_UNREACHABLE -> TpLinkXdrDsFailureReason.DEVICE_UNREACHABLE
        NetworkFailureReason.TIMEOUT -> TpLinkXdrDsFailureReason.TIMEOUT
        NetworkFailureReason.UNEXPECTED_RESPONSE -> TpLinkXdrDsFailureReason.UNEXPECTED_RESPONSE
        NetworkFailureReason.COMMUNICATION_ERROR -> TpLinkXdrDsFailureReason.COMMUNICATION_ERROR
    }
}

internal class TpLinkXdrDsDriverFamilyFactory : DriverFamilyFactory {
    override val familyId: String = "tplink-xdr-ds-driver"

    override fun create(profile: CompatibilityProfile, host: String, transport: HttpTransport): DriverFamily {
        val config = TpLinkXdrDsDriverConfig.fromJsonElement(profile.driverConfig)
        return TpLinkXdrDsDriverFamily(host = host, config = config, transport = transport)
    }
}
