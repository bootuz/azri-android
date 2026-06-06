package nart.simpleanki.feature.typepractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import nart.simpleanki.core.domain.typing.AnswerDiff
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.typing.SessionReport
import nart.simpleanki.core.domain.typing.TypeDirection
import nart.simpleanki.di.StudyArgs
import nart.simpleanki.ui.components.AudioPlayButton
import nart.simpleanki.ui.components.MediaImage
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TypePracticeScreen(
    deckId: String,
    onDone: () -> Unit,
    viewModel: TypePracticeViewModel = koinViewModel { parametersOf(StudyArgs(deckId = deckId)) },
) {
    val state by viewModel.uiState.collectAsState()
    TypePracticeContent(
        state = state,
        onChooseDirection = viewModel::chooseDirection,
        onInput = viewModel::onInput,
        onSubmit = viewModel::onSubmit,
        onDontKnow = viewModel::onDontKnow,
        onContinue = viewModel::onContinue,
        onOverride = viewModel::onOverride,
        onRestart = viewModel::restart,
        onDone = onDone,
    )
}

/** Stateless Type-Practice UI, decoupled from the ViewModel for previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypePracticeContent(
    state: TypePracticeUiState,
    onChooseDirection: (TypeDirection) -> Unit,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
    onRestart: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.finished) "Done" else "Type · ${state.remaining} left") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.awaitingDirection -> DirectionChooser(onChooseDirection)
                state.finished -> SessionReportView(state.report, onRestart, onDone)
                else -> PracticeCard(state, onInput, onSubmit, onDontKnow, onContinue, onOverride)
            }
        }
    }
}

@Composable
private fun DirectionChooser(onChoose: (TypeDirection) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "What do you want to type?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the side you'll produce from memory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        DirectionOption(
            title = "Type the back",
            subtitle = "See the front → type the back (the answer)",
            onClick = { onChoose(TypeDirection.TypeBack) },
        )
        Spacer(Modifier.height(12.dp))
        DirectionOption(
            title = "Type the front",
            subtitle = "See the back → type the front",
            onClick = { onChoose(TypeDirection.TypeFront) },
        )
    }
}

@Composable
private fun DirectionOption(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PracticeCard(
    state: TypePracticeUiState,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
) {
    val card = state.current ?: return
    val typeFront = state.direction == TypeDirection.TypeFront
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Re-focus the field each time the prompt changes (this also re-shows the keyboard for the next card).
    // runCatching: the field may be disabled (revealing state) right after a config-change/restore; safe to ignore.
    LaunchedEffect(state.cardTick) { runCatching { focus.requestFocus() } }
    // Hide the keyboard when a wrong answer is revealed, so it doesn't cover the Continue / "I was right" buttons.
    LaunchedEffect(state.revealing) { if (state.revealing) keyboard?.hide() }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "PROMPT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(12.dp))
        // The image is a front-side artifact: show it only when the front is the prompt (TypeBack).
        if (!typeFront) {
            card.image?.let { name ->
                MediaImage(name, card.imagePath, Modifier.fillMaxWidth().height(160.dp))
                Spacer(Modifier.height(16.dp))
            }
        }
        Text(
            if (typeFront) card.back else card.front,   // prompt = the side the user is NOT typing
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        card.audioName?.let { name ->
            Spacer(Modifier.height(12.dp))
            AudioPlayButton(name, card.audioPath)
        }

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            enabled = !state.revealing,
            singleLine = true,
            label = { Text("Type the answer") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!state.revealing) onSubmit() }),
        )

        Spacer(Modifier.height(16.dp))

        if (state.revealing) {
            RevealPanel(state, onContinue, onOverride)
        } else {
            Button(
                onClick = onSubmit,
                enabled = state.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text("Check") }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDontKnow, modifier = Modifier.fillMaxWidth()) {
                Text("Don't know")
            }
        }
    }
}

@Composable
private fun RevealPanel(state: TypePracticeUiState, onContinue: () -> Unit, onOverride: () -> Unit) {
    val diff = remember(state.revealedAnswer, state.lastTyped) {
        AnswerDiff.diff(typed = state.lastTyped, expected = state.revealedAnswer)
    }
    val matchColor = MaterialTheme.colorScheme.primary
    val missColor = MaterialTheme.colorScheme.error
    val typedMatchColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Correct answer",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                diff.expected.forEach { seg ->
                    when (seg.kind) {
                        AnswerDiff.Kind.Match ->
                            withStyle(SpanStyle(color = matchColor)) { append(seg.text) }
                        AnswerDiff.Kind.Mismatch ->
                            withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.Underline)) { append(seg.text) }
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (state.lastTyped.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "You typed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildAnnotatedString {
                    diff.typed.forEach { seg ->
                        when (seg.kind) {
                            AnswerDiff.Kind.Match ->
                                withStyle(SpanStyle(color = typedMatchColor)) { append(seg.text) }
                            AnswerDiff.Kind.Mismatch ->
                                withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Continue") }
        if (state.canOverride) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) {
                Text("I was right")
            }
        }
    }
}

@Composable
private fun SessionReportView(report: SessionReport?, onRestart: () -> Unit, onDone: () -> Unit) {
    val r = report ?: SessionReport(0, 0, 0, 0, 0)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Practice complete", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        ReportRow("Cards", r.completed.toString())
        ReportRow("First-try accuracy", "${r.firstTryAccuracy}%")
        ReportRow("Best combo", r.bestCombo.toString())
        ReportRow("Newly mastered", r.newlyMastered.toString())
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Practice again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Done") }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val previewCard = Card(
    id = "c1", front = "¿Cómo estás?", back = "How are you?", deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "Type · prompt", showBackground = true)
@Composable
private fun TypePromptPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(loading = false, current = previewCard, input = "How are", remaining = 5, direction = TypeDirection.TypeBack),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {}, onRestart = {}, onDone = {}, onChooseDirection = {},
        )
    }
}

@Preview(name = "Type · revealed (wrong)", showBackground = true)
@Composable
private fun TypeRevealPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(
                loading = false, current = previewCard, remaining = 5,
                revealing = true, revealedAnswer = "How are you?", lastTyped = "how is you", canOverride = true,
                direction = TypeDirection.TypeBack,
            ),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {}, onRestart = {}, onDone = {}, onChooseDirection = {},
        )
    }
}

@Preview(name = "Type · report", showBackground = true)
@Composable
private fun TypeReportPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(
                loading = false, finished = true,
                report = SessionReport(completed = 12, firstTryCorrect = 9, firstTryAccuracy = 75, bestCombo = 5, newlyMastered = 3),
            ),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {}, onRestart = {}, onDone = {}, onChooseDirection = {},
        )
    }
}

@Preview(name = "Type · direction chooser", showBackground = true)
@Composable
private fun TypeDirectionChooserPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(loading = false, awaitingDirection = true),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {},
            onRestart = {}, onDone = {}, onChooseDirection = {},
        )
    }
}

@Preview(name = "Type · prompt (type front)", showBackground = true)
@Composable
private fun TypeFrontPromptPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(
                loading = false, current = previewCard, input = "¿Cómo",
                remaining = 5, direction = TypeDirection.TypeFront,
            ),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {},
            onRestart = {}, onDone = {}, onChooseDirection = {},
        )
    }
}
