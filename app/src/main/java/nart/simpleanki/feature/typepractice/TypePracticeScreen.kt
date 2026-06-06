package nart.simpleanki.feature.typepractice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.typing.AnswerDiff
import nart.simpleanki.core.domain.typing.SessionReport
import nart.simpleanki.core.domain.typing.TypeDirection
import nart.simpleanki.di.StudyArgs
import nart.simpleanki.ui.components.AudioPlayButton
import nart.simpleanki.ui.components.MediaImage
import nart.simpleanki.ui.theme.AzriTheme
import nart.simpleanki.ui.theme.RatingColors
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

/** Stateless gamified Type-Practice UI, decoupled from the ViewModel for previews. */
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
    val inSession = !state.loading && !state.awaitingDirection && !state.finished
    val progressTarget = if (inSession && state.total > 0) (state.total - state.remaining).toFloat() / state.total else 0f
    val progress by animateFloatAsState(targetValue = progressTarget, animationSpec = tween(300), label = "progress")
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                title = {
                    if (inSession) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
                                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
                            color = if (state.celebrating) RatingColors.Easy else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        Text(if (state.finished) "Done" else "Type Practice")
                    }
                },
                actions = { if (inSession) ComboChip(state.combo) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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

private val ComboAmber = RatingColors.Hard

@Composable
private fun ComboChip(combo: Int) {
    val active = combo >= 1
    val pop = remember { Animatable(1f) }
    LaunchedEffect(combo) {
        if (combo >= 1) { pop.snapTo(1.25f); pop.animateTo(1f, tween(180)) }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) ComboAmber.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(end = 8.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = "Combo",
                tint = if (active) ComboAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$combo",
                style = MaterialTheme.typography.labelLarge,
                color = if (active) ComboAmber else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Text("What do you want to type?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the side you'll produce from memory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        DirectionOption("Type the back", "See the front → type the back (the answer)") { onChoose(TypeDirection.TypeBack) }
        Spacer(Modifier.height(12.dp))
        DirectionOption("Type the front", "See the back → type the front") { onChoose(TypeDirection.TypeFront) }
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    LaunchedEffect(state.cardTick) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.revealing, state.celebrating) {
        if (state.revealing || state.celebrating) keyboard?.hide()
    }

    Column(Modifier.fillMaxSize().padding(20.dp).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Upper zone: prompt hero card — scrolls if long, slides per card.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = state.cardTick,
                transitionSpec = {
                    (slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                },
                label = "card",
            ) { _ ->
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PromptCard(card, typeFront, celebrating = state.celebrating)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Lower zone (thumb-rail): answer / celebrate / reveal.
        when {
            state.celebrating -> CorrectInput(state.input)
            state.revealing -> RevealPanel(state, onContinue, onOverride)
            else -> AnswerInput(state, focus, onInput, onSubmit, onDontKnow)
        }
    }
}

@Composable
private fun PromptCard(card: Card, typeFront: Boolean, celebrating: Boolean) {
    val mint = RatingColors.Easy
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (celebrating) mint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = if (celebrating) BorderStroke(1.5.dp, mint) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (celebrating) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(mint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = "Correct", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(14.dp))
            } else {
                DirectionPill(typeFront)
                Spacer(Modifier.height(14.dp))
            }
            if (!typeFront) {
                card.image?.let { name ->
                    MediaImage(name, card.imagePath, Modifier.fillMaxWidth().height(160.dp))
                    Spacer(Modifier.height(16.dp))
                }
            }
            Text(
                if (typeFront) card.back else card.front,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            card.audioName?.let { name ->
                Spacer(Modifier.height(16.dp))
                AudioPlayButton(name, card.audioPath)
            }
        }
    }
}

@Composable
private fun DirectionPill(typeFront: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Text(
            if (typeFront) "TYPE THE FRONT" else "TYPE THE BACK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AnswerInput(
    state: TypePracticeUiState,
    focus: FocusRequester,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            label = { Text("Type the answer") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSubmit,
            enabled = state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Check") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDontKnow, modifier = Modifier.fillMaxWidth()) { Text("Don't know") }
    }
}

@Composable
private fun CorrectInput(typed: String) {
    val mint = RatingColors.Easy
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, mint),
        color = mint.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            typed,
            color = mint,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun RevealPanel(state: TypePracticeUiState, onContinue: () -> Unit, onOverride: () -> Unit) {
    val diff = remember(state.revealedAnswer, state.lastTyped) {
        AnswerDiff.diff(typed = state.lastTyped, expected = state.revealedAnswer)
    }
    val matchColor = RatingColors.Easy        // got it right = mint
    val missColor = RatingColors.Again        // wrong/missing = pink
    val typedMatchColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Correct answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                diff.expected.forEach { seg ->
                    when (seg.kind) {
                        AnswerDiff.Kind.Match -> withStyle(SpanStyle(color = matchColor)) { append(seg.text) }
                        AnswerDiff.Kind.Mismatch -> withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.Underline)) { append(seg.text) }
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (state.lastTyped.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("You typed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                buildAnnotatedString {
                    diff.typed.forEach { seg ->
                        when (seg.kind) {
                            AnswerDiff.Kind.Match -> withStyle(SpanStyle(color = typedMatchColor)) { append(seg.text) }
                            AnswerDiff.Kind.Mismatch -> withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.large) { Text("Continue") }
        if (state.canOverride) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) { Text("I was right") }
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
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.large) { Text("Practice again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.large) { Text("Done") }
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

private fun previewState(
    current: Card? = previewCard,
    input: String = "",
    revealing: Boolean = false,
    celebrating: Boolean = false,
    finished: Boolean = false,
    awaitingDirection: Boolean = false,
    report: SessionReport? = null,
    revealedAnswer: String = "",
    lastTyped: String = "",
    canOverride: Boolean = false,
    direction: TypeDirection? = TypeDirection.TypeBack,
) = TypePracticeUiState(
    loading = false, awaitingDirection = awaitingDirection, direction = direction, current = current,
    input = input, revealing = revealing, revealedAnswer = revealedAnswer, lastTyped = lastTyped,
    canOverride = canOverride, remaining = 3, total = 5, combo = 3, finished = finished,
    report = report, celebrating = celebrating,
)

@Composable
private fun PreviewWrap(state: TypePracticeUiState) {
    AzriTheme {
        TypePracticeContent(
            state = state,
            onChooseDirection = {}, onInput = {}, onSubmit = {}, onDontKnow = {},
            onContinue = {}, onOverride = {}, onRestart = {}, onDone = {},
        )
    }
}

@Preview(name = "Type · prompt", showBackground = true)
@Composable
private fun TypePromptPreview() = PreviewWrap(previewState(input = "How are"))

@Preview(name = "Type · prompt (type front)", showBackground = true)
@Composable
private fun TypeFrontPromptPreview() = PreviewWrap(previewState(input = "¿Cómo", direction = TypeDirection.TypeFront))

@Preview(name = "Type · celebrating", showBackground = true)
@Composable
private fun TypeCelebratingPreview() = PreviewWrap(previewState(input = "How are you?", celebrating = true))

@Preview(name = "Type · revealed (wrong)", showBackground = true)
@Composable
private fun TypeRevealPreview() = PreviewWrap(
    previewState(revealing = true, revealedAnswer = "How are you?", lastTyped = "how is you", canOverride = true),
)

@Preview(name = "Type · direction chooser", showBackground = true)
@Composable
private fun TypeDirectionChooserPreview() = PreviewWrap(previewState(awaitingDirection = true, current = null))

@Preview(name = "Type · report", showBackground = true)
@Composable
private fun TypeReportPreview() = PreviewWrap(
    previewState(current = null, finished = true, report = SessionReport(completed = 12, firstTryCorrect = 9, firstTryAccuracy = 75, bestCombo = 5, newlyMastered = 3)),
)
