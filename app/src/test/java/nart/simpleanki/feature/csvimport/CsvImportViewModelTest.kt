package nart.simpleanki.feature.csvimport

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.csv.FakeCsvImportService
import nart.simpleanki.core.csv.ParsedCsv
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CsvImportViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val parsed = ParsedCsv(
        headers = listOf("front", "back"),
        rows = listOf(listOf("hola", "hello"), listOf("adios", "bye")),
    )

    private fun vm(service: FakeCsvImportService) = CsvImportViewModel(service, "MyDeck")

    @Test fun parse_advancesToColumnMapping_withDefaults() = runTest {
        val vm = vm(FakeCsvImportService(parsed = parsed))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertEquals(ImportStep.ColumnMapping, vm.uiState.value.step)
        assertEquals(listOf("front", "back"), vm.uiState.value.headers)
        assertEquals(0, vm.uiState.value.frontCol)
        assertEquals(1, vm.uiState.value.backCol)
        assertEquals(2, vm.uiState.value.sampleRows.size)
    }

    @Test fun setHasHeader_reparsesWithNewFlag() = runTest {
        val service = FakeCsvImportService(parsed = parsed)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.setHasHeader(false); runCurrent()
        assertFalse(vm.uiState.value.hasHeader)
        assertEquals(false, service.lastHasHeader)
    }

    @Test fun sameFrontAndBack_blocksPreview() = runTest {
        val vm = vm(FakeCsvImportService(parsed = parsed))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.setBackCol(0)
        assertFalse(vm.uiState.value.canGeneratePreview)
        vm.setBackCol(1)
        assertTrue(vm.uiState.value.canGeneratePreview)
    }

    @Test fun generatePreview_thenImport_passesSelectedCards() = runTest {
        val service = FakeCsvImportService(parsed = parsed)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.generatePreview()
        assertEquals(ImportStep.Preview, vm.uiState.value.step)
        assertEquals(2, vm.uiState.value.previewCards.size)
        vm.import {}; runCurrent()
        assertEquals("MyDeck", service.importedDeckName)
        assertEquals(2, service.importedCards.size)
    }

    @Test fun toggleCard_deselects_excludesFromImport_andHandlesOutOfBounds() = runTest {
        val service = FakeCsvImportService(parsed = parsed)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.generatePreview()
        vm.toggleCard(0); vm.toggleCard(1)   // deselect both
        vm.toggleCard(99)                     // out of bounds: no-op, must not crash
        vm.import {}; runCurrent()
        assertEquals(ImportStep.Preview, vm.uiState.value.step)  // nothing selected -> short-circuits
        assertEquals(0, service.importedCards.size)
    }

    @Test fun parseError_setsErrorMessage() = runTest {
        val vm = vm(FakeCsvImportService(parseError = RuntimeException("boom")))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertTrue(vm.uiState.value.error != null)
    }
}
