package com.nethal.feature.devices.ui

import com.nethal.feature.devices.domain.LanDevice
import com.nethal.feature.devices.domain.LanDeviceScanner
import com.nethal.feature.devices.domain.LanDeviceType
import com.nethal.feature.devices.domain.LanScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val fakeDevice = LanDevice(
        ipAddress = "192.168.1.5",
        macAddress = "11:22:33:44:55:66",
        hostname = null,
        vendor = "TP-Link",
        deviceType = LanDeviceType.UNKNOWN,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts in Loading and transitions to Loaded on successful scan`() = runTest {
        val scanner = FakeScanner { LanScanResult.Success(listOf(fakeDevice)) }
        val viewModel = DevicesViewModel(scanner)

        assertTrue(viewModel.uiState.value is DevicesUiState.Loading)

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DevicesUiState.Loaded)
        assertEquals(listOf(fakeDevice), (state as DevicesUiState.Loaded).devices)
    }

    @Test
    fun `transitions to NoNetwork when scanner reports no network`() = runTest {
        val scanner = FakeScanner { LanScanResult.NoNetwork }
        val viewModel = DevicesViewModel(scanner)

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is DevicesUiState.NoNetwork)
    }

    @Test
    fun `transitions to Failed when scanner throws`() = runTest {
        val scanner = FakeScanner { throw IllegalStateException("boom") }
        val viewModel = DevicesViewModel(scanner)

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DevicesUiState.Failed)
        assertEquals("boom", (state as DevicesUiState.Failed).message)
    }

    @Test
    fun `refresh goes back to Loading before re-emitting result`() = runTest {
        val scanner = FakeScanner { LanScanResult.Success(listOf(fakeDevice)) }
        val viewModel = DevicesViewModel(scanner)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is DevicesUiState.Loaded)

        viewModel.refresh()
        assertTrue(viewModel.uiState.value is DevicesUiState.Loading)

        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is DevicesUiState.Loaded)
    }

    private class FakeScanner(private val result: () -> LanScanResult) : LanDeviceScanner {
        override suspend fun scan(): LanScanResult = result()
    }
}
