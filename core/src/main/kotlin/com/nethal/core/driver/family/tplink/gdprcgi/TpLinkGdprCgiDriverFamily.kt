package com.nethal.core.driver.family.tplink.gdprcgi

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

internal enum class TpLinkGdprCgiFailureReason {
    DEVICE_UNREACHABLE,
    TIMEOUT,
    UNEXPECTED_RESPONSE,
    INVALID_CREDENTIALS,
    COMMUNICATION_ERROR,
}

internal sealed interface TpLinkGdprCgiLoginOutcome {
    data class Success(val session: TpLinkGdprCgiSession) : TpLinkGdprCgiLoginOutcome
    data class Failure(val reason: TpLinkGdprCgiFailureReason, val message: String) : TpLinkGdprCgiLoginOutcome
}

internal sealed interface TpLinkGdprCgiReadOutcome {
    data class Success(val rawBody: String) : TpLinkGdprCgiReadOutcome
    data class Failure(val reason: TpLinkGdprCgiFailureReason, val message: String) : TpLinkGdprCgiReadOutcome
}

internal class TpLinkGdprCgiDriverFamily(
    private val host: String,
    private val config: TpLinkGdprCgiDriverConfig,
    private val transport: HttpTransport,
    private val maxAttempts: Int = 2,
    private val backoffMillis: (attempt: Int) -> Long = { attempt -> 1_000L * attempt },
) : DriverFamily {

    init {
        require(PrivateIpRanges.isPrivate(host)) {
            "TpLinkGdprCgiDriverFamily só opera contra IP privado (RFC 1918) da LAN; host recebido: $host"
        }
    }

    suspend fun login(username: String, password: String): TpLinkGdprCgiLoginOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkGdprCgiLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkGdprCgiLoginFailureReason.INVALID_CREDENTIALS -> TpLinkGdprCgiFailureReason.INVALID_CREDENTIALS
                    TpLinkGdprCgiLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                    TpLinkGdprCgiLoginFailureReason.UNEXPECTED_RESPONSE,
                    -> null
                }
            },
            classifyFinalFailure = ::classifyFailure,
        ) {
            TpLinkGdprCgiAuthenticationClient(host, config, transport).login(username, password)
        }
        when (outcome) {
            is RetryOutcome.Success -> TpLinkGdprCgiLoginOutcome.Success(outcome.value)
            is RetryOutcome.Failure -> TpLinkGdprCgiLoginOutcome.Failure(
                outcome.reason,
                outcome.error.message ?: outcome.error.toString(),
            )
        }
    }

    suspend fun readRaw(username: String, password: String): TpLinkGdprCgiReadOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkGdprCgiLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkGdprCgiLoginFailureReason.INVALID_CREDENTIALS -> TpLinkGdprCgiFailureReason.INVALID_CREDENTIALS
                    TpLinkGdprCgiLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                    TpLinkGdprCgiLoginFailureReason.UNEXPECTED_RESPONSE,
                    -> null
                }
            },
            classifyFinalFailure = ::classifyFailure,
        ) {
            val client = TpLinkGdprCgiAuthenticationClient(host, config, transport)
            client.login(username, password)
            client.fetchAuthenticatedRaw(config.authenticatedReadPath, config.authenticatedReadPlaintext)
        }
        when (outcome) {
            is RetryOutcome.Success -> TpLinkGdprCgiReadOutcome.Success(outcome.value)
            is RetryOutcome.Failure -> TpLinkGdprCgiReadOutcome.Failure(
                outcome.reason,
                outcome.error.message ?: outcome.error.toString(),
            )
        }
    }

    override suspend fun readCapability(id: CapabilityId): CapabilityReadResult =
        CapabilityReadResult.Unavailable(
            reason = "TpLinkGdprCgiDriverFamily ja implementa login e leitura autenticada bruta do ramo /cgi_gdpr, " +
                "mas ainda nao tem parser estruturado por capability nem sessao gerenciada pelo Capability Engine.",
        )

    private fun classifyFailure(error: Throwable): TpLinkGdprCgiFailureReason = when (classifyNetworkFailure(error)) {
        NetworkFailureReason.DEVICE_UNREACHABLE -> TpLinkGdprCgiFailureReason.DEVICE_UNREACHABLE
        NetworkFailureReason.TIMEOUT -> TpLinkGdprCgiFailureReason.TIMEOUT
        NetworkFailureReason.UNEXPECTED_RESPONSE -> TpLinkGdprCgiFailureReason.UNEXPECTED_RESPONSE
        NetworkFailureReason.COMMUNICATION_ERROR -> TpLinkGdprCgiFailureReason.COMMUNICATION_ERROR
    }
}

internal class TpLinkGdprCgiDriverFamilyFactory : DriverFamilyFactory {
    override val familyId: String = "tplink-gdpr-cgi-driver"

    override fun create(profile: CompatibilityProfile, host: String, transport: HttpTransport): DriverFamily {
        val config = TpLinkGdprCgiDriverConfig.fromJsonElement(profile.driverConfig)
        return TpLinkGdprCgiDriverFamily(host = host, config = config, transport = transport)
    }
}
