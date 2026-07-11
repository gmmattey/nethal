package com.nethal.lab.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nethal.core.catalog.CapabilityReadResult
import com.nethal.lab.ui.capabilities.CapabilityItem
import com.nethal.lab.ui.capabilities.capabilityLabel
import com.nethal.lab.ui.capabilities.capabilityPayloadSummary
import com.nethal.lab.ui.capabilities.capabilityStatusLine
import com.nethal.lab.ui.capabilities.isReadError
import com.nethal.lab.ui.capabilities.isSuccess

/**
 * Tela 6 — Relatório (spec §11): resultado geral, dados lidos, capabilities, erros, driver usado,
 * aviso de reprovisionamento por operadora (quando aplicável) e botão de envio de relatório
 * anônimo — que nunca envia nada de verdade nesta versão (ver `ReportViewModel.sendAnonymousReport`).
 *
 * `onFinish` encerra o fluxo de diagnóstico e entra no "modo uso diário" (host de bottom nav, #67)
 * em vez de voltar para a tela de boas-vindas — decisão superada em 2026-07-11: antes do host de
 * bottom nav existir, não havia destino pós-funil além de reiniciar do zero; agora que existe um
 * shell persistente (Status/Rede/Dispositivos/Configurações), faz mais sentido entrar nele do que
 * descartar o contexto do equipamento recém-diagnosticado. Ver `NetHalNavHost` para o `popUpTo`
 * que limpa todo o estado do funil de pareamento antes de entrar no host.
 */
@Composable
fun ReportScreen(viewModel: ReportViewModel, onFinish: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is ReportUiState.Unavailable -> UnavailableContent(state = state, onFinish = onFinish)
        is ReportUiState.Ready -> ReadyContent(
            state = state,
            onSendAnonymousReport = viewModel::sendAnonymousReport,
            onFinish = onFinish,
        )
    }
}

@Composable
private fun UnavailableContent(state: ReportUiState.Unavailable, onFinish: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Relatório indisponível", style = MaterialTheme.typography.headlineSmall)
            Text(text = state.reason, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar ao início")
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: ReportUiState.Ready,
    onSendAnonymousReport: () -> Unit,
    onFinish: () -> Unit,
) {
    val successItems = state.items.filter { it.isSuccess() }
    val errorItems = state.items.filter { it.isReadError() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Relatório", style = MaterialTheme.typography.headlineSmall)
            Text(text = outcomeLabel(state.outcome), style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Driver usado", style = MaterialTheme.typography.titleSmall)
                    Text(text = "${state.vendor} ${state.model}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = state.driverFamilyId, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.provisioningWarning != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.provisioningWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Text(text = "Dados lidos", style = MaterialTheme.typography.titleMedium)
            if (successItems.isEmpty()) {
                Text(text = "Nenhum dado foi lido com sucesso nesta sessão.", style = MaterialTheme.typography.bodyMedium)
            } else {
                successItems.forEach { item -> DataReadRow(item) }
            }

            Text(text = "Capabilities", style = MaterialTheme.typography.titleMedium)
            state.items.forEach { item ->
                Text(text = capabilityStatusLine(item), style = MaterialTheme.typography.bodySmall)
            }

            Text(text = "Erros", style = MaterialTheme.typography.titleMedium)
            if (errorItems.isEmpty()) {
                Text(text = "Nenhum erro de leitura nesta sessão.", style = MaterialTheme.typography.bodyMedium)
            } else {
                errorItems.forEach { item ->
                    Text(
                        text = capabilityStatusLine(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SendReportSection(sendReportState = state.sendReportState, onSendAnonymousReport = onSendAnonymousReport)

            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Concluir")
            }
        }
    }
}

@Composable
private fun DataReadRow(item: CapabilityItem) {
    val success = item.result as? CapabilityReadResult.Success ?: return
    Text(
        text = "${capabilityLabel(item.id)}: ${capabilityPayloadSummary(success.payload)}",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SendReportSection(sendReportState: SendReportState, onSendAnonymousReport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSendAnonymousReport, modifier = Modifier.fillMaxWidth()) {
            Text("Enviar relatório anônimo")
        }
        if (sendReportState is SendReportState.Unavailable) {
            Text(
                text = sendReportState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun outcomeLabel(outcome: ReportOutcome): String = when (outcome) {
    ReportOutcome.FULL_SUCCESS -> "Leitura concluída com sucesso"
    ReportOutcome.PARTIAL_SUCCESS -> "Leitura parcial — alguns dados não puderam ser lidos"
    ReportOutcome.NO_DATA -> "Não foi possível ler dados deste equipamento"
}
