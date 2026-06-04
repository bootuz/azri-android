package nart.simpleanki.feature.apkgimport

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
import nart.simpleanki.core.apkg.AnkiNote
import nart.simpleanki.core.apkg.AnkiNoteType
import nart.simpleanki.core.apkg.FakeApkgImportService
import nart.simpleanki.core.apkg.ParsedCollection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApkgImportViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val collection = ParsedCollection(
        noteTypes = listOf(AnkiNoteType(1, "Basic", listOf("Front", "Back"), 0)),
        notes = listOf(AnkiNote(1, "g", 1, listOf("F", "B"), emptyList())),
        media = emptyMap(),
    )

    private fun vm(service: FakeApkgImportService) = ApkgImportViewModel(service, "MyDeck")

    @Test fun parse_advancesToNoteTypeSelection() = runTest {
        val vm = vm(FakeApkgImportService(collection = collection))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertEquals(ImportStep.NoteTypeSelection, vm.uiState.value.step)
        assertEquals(1, vm.uiState.value.noteTypes.size)
    }

    @Test fun selectNoteType_thenValidateFieldMapping_requiresDistinctFields() = runTest {
        val vm = vm(FakeApkgImportService(collection = collection))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.selectNoteType(collection.noteTypes[0]); runCurrent()
        assertEquals(ImportStep.FieldMapping, vm.uiState.value.step)
        vm.setFrontField("Front"); vm.setBackField("Front")
        assertFalse(vm.uiState.value.canGeneratePreview)
        vm.setBackField("Back")
        assertTrue(vm.uiState.value.canGeneratePreview)
    }

    @Test fun generatePreview_thenImport_callsServiceWithSelectedCards() = runTest {
        val service = FakeApkgImportService(collection = collection)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.selectNoteType(collection.noteTypes[0]); runCurrent()
        vm.setFrontField("Front"); vm.setBackField("Back")
        vm.generatePreview(); runCurrent()
        assertEquals(ImportStep.Preview, vm.uiState.value.step)
        assertEquals(1, vm.uiState.value.previewCards.size)
        vm.import {}; runCurrent()
        assertEquals("MyDeck", service.importedDeckName)
        assertEquals(1, service.importedCards.size)
    }

    @Test fun toggleCard_deselects_excludesFromImport_andHandlesOutOfBounds() = runTest {
        val service = FakeApkgImportService(collection = collection)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.selectNoteType(collection.noteTypes[0]); runCurrent()
        vm.setFrontField("Front"); vm.setBackField("Back")
        vm.generatePreview(); runCurrent()
        assertEquals(1, vm.uiState.value.previewCards.size)
        vm.toggleCard(0)                                   // deselect the only card
        assertFalse(vm.uiState.value.previewCards[0].selected)
        vm.toggleCard(99)                                  // out of bounds: no-op, must not crash
        vm.import {}; runCurrent()
        assertEquals(ImportStep.Preview, vm.uiState.value.step)  // nothing selected -> import short-circuits
        assertEquals(0, service.importedCards.size)
    }

    @Test fun parseError_setsErrorMessage() = runTest {
        val vm = vm(FakeApkgImportService(parseError = RuntimeException("boom")))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertTrue(vm.uiState.value.error != null)
    }
}
