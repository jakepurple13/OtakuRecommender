package com.programmersbox.otakurecommender

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val HARASSMENT_PARAM = SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE)
private val HATE_SPEECH_PARAM = SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE)
private val DANGEROUS_CONTENT_PARAM =
    SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
private val SEXUALLY_EXPLICIT_PARAM =
    SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE)
private val SAFETY_SETTINGS =
    listOf(HARASSMENT_PARAM, HATE_SPEECH_PARAM, DANGEROUS_CONTENT_PARAM, SEXUALLY_EXPLICIT_PARAM)

class GeminiRecommendationViewModel : ViewModel() {

    val generativeModel = GenerativeModel(
        "gemini-1.5-flash",
        // Retrieve API key as an environmental variable defined in a Build Configuration
        // see https://github.com/google/secrets-gradle-plugin for further instructions
        BuildConfig.apiKey,
        generationConfig = generationConfig {
            temperature = 1f
            topK = 64
            topP = 0.95f
            maxOutputTokens = 8192
            responseMimeType = "application/json"
            responseSchema = Schema.obj(
                "response",
                "a response",
                Schema.arr(
                    "recommendations",
                    "a list of recommendations",
                    Schema.obj(
                        "recommendation",
                        "a single recommendation",
                        Schema.str("title", "the title of the recommendation"),
                        Schema.str("description", "a short description of the recommendation"),
                        Schema.str("reason", "a short reason for the recommendation"),
                        Schema.arr("genre", "a list of genres", Schema.str("genre", "a genre"))
                    )
                )
            )
        },
        // safetySettings = Adjust safety settings
        // See https://ai.google.dev/gemini-api/docs/safety-settings
        safetySettings = SAFETY_SETTINGS,
        systemInstruction = content { text("You are a human-like, minimalistic bot speaking to adults who really like anime, manga, and novels. They are asking about recommendations based on what they have currently read or watched or just random recommendations in general. When responding, make sure to include the title, a short summary without any spoilers, and a few genre tags for the recommendation. Try to recommend at least 3 per response.\nWhen responding, respond with json like the following:\n{\"response\":response,\"recommendations\":[{\"title\":title, \"description\":description, \"reason\": reason, genre:[genres]}]}") },
    )

    private val chat = generativeModel.startChat()

    val messageList = mutableStateListOf<Message>()

    var isLoading by mutableStateOf(false)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun send(input: String) {
        viewModelScope.launch {
            isLoading = true
            runCatching {
                messageList.add(Message.User(input))
                chat.sendMessage(input)
            }
                .onSuccess {
                    println(it.text)
                    runCatching {
                        messageList.add(
                            Message.Gemini(
                                json.decodeFromString(
                                    it.text.orEmpty().trim()
                                )
                            )
                        )
                    }.onFailure {
                        it.printStackTrace()
                        messageList.add(Message.Error(it.localizedMessage.orEmpty()))
                    }
                }
                .onFailure {
                    it.printStackTrace()
                    messageList.add(Message.Error(it.localizedMessage.orEmpty()))
                }
            isLoading = false
        }
    }
}

sealed class Message {
    data class Gemini(val recommendationResponse: RecommendationResponse) : Message()
    data class User(val text: String) : Message()
    data class Error(val text: String) : Message()
}

@Serializable
data class Recommendation(
    val title: String,
    val description: String,
    val reason: String,
    val genre: List<String>,
)

@Serializable
data class RecommendationResponse(
    val response: String? = null,
    val recommendations: List<Recommendation>,
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class, ExperimentalLayoutApi::class
)
@Composable
fun GeminiRecommendationScreen(
    viewModel: GeminiRecommendationViewModel = androidx.lifecycle.viewmodel.compose.viewModel { GeminiRecommendationViewModel() },
) {
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val lazyState = rememberLazyListState()
    LaunchedEffect(viewModel.messageList.lastIndex) {
        lazyState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OtakuBot: Powered by Gemini") },
                scrollBehavior = topBarScrollBehavior
            )
        },
        bottomBar = {
            MessageInput(
                onSendMessage = { viewModel.send(it) },
                resetScroll = { scope.launch { lazyState.animateScrollToItem(0) } },
                modifier = Modifier
                    .background(BottomAppBarDefaults.containerColor)
                    .navigationBarsPadding()
            )
        },
        modifier = Modifier
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
            .imePadding()
    ) { padding ->
        LazyColumn(
            state = lazyState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            if (viewModel.isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(
                viewModel.messageList.reversed(),
                contentType = { it }
            ) {
                when (it) {
                    is Message.Error -> ErrorMessage(
                        message = it,
                        modifier = Modifier.animateItemPlacement()
                    )

                    is Message.Gemini -> GeminiMessage(
                        message = it,
                        modifier = Modifier.animateItemPlacement()
                    )

                    is Message.User -> UserMessage(
                        message = it,
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }

            item {
                GeminiMessage(
                    message = Message.Gemini(
                        RecommendationResponse(
                            response = "Hi! Ask me for anime, manga, or novel recommendations and I will give them!",
                            recommendations = emptyList()
                        )
                    ),
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }
    }
}

@Composable
private fun GeminiMessage(
    message: Message.Gemini,
    modifier: Modifier = Modifier,
) {
    ChatBubbleItem(
        chatMessage = message,
        modifier = modifier
    )
}

@Composable
private fun UserMessage(
    message: Message.User,
    modifier: Modifier = Modifier,
) {
    ChatBubbleItem(
        chatMessage = message,
        modifier = modifier
    )
}

@Composable
private fun ErrorMessage(
    message: Message.Error,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = "Person Icon",
            tint = MaterialTheme.colorScheme.error
        )
        SelectionContainer {
            Text(
                text = message.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun ChatBubbleItem(
    chatMessage: Message,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when (chatMessage) {
        is Message.Gemini -> MaterialTheme.colorScheme.primaryContainer
        is Message.User -> MaterialTheme.colorScheme.secondaryContainer
        is Message.Error -> MaterialTheme.colorScheme.errorContainer
    }

    val bubbleShape = when (chatMessage) {
        is Message.Error -> MaterialTheme.shapes.medium
        is Message.Gemini -> RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
        is Message.User -> RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    }

    val horizontalAlignment = when (chatMessage) {
        is Message.Error -> Alignment.CenterHorizontally
        is Message.Gemini -> Alignment.Start
        is Message.User -> Alignment.End
    }

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize()
            .fillMaxWidth()
    ) {
        Text(
            text = when (chatMessage) {
                is Message.Error -> "Error"
                is Message.Gemini -> "OtakuBot"
                is Message.User -> "You"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        BoxWithConstraints {
            Card(
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = bubbleShape,
                modifier = Modifier.widthIn(0.dp, maxWidth * 0.9f)
            ) {
                when (chatMessage) {
                    is Message.Error -> ErrorMessage(message = chatMessage)
                    is Message.Gemini -> {
                        Text(
                            chatMessage
                                .recommendationResponse
                                .response
                                ?: "Showing recommendations",
                            modifier = Modifier.padding(16.dp)
                        )

                        if (chatMessage.recommendationResponse.recommendations.isNotEmpty()) {
                            var showRecs by remember { mutableStateOf(false) }

                            AnimatedContent(showRecs, label = "") { target ->
                                if (target) {
                                    Column {
                                        ListItem(
                                            headlineContent = { Text("Recommendations") },
                                            trailingContent = {
                                                Icon(
                                                    Icons.Default.KeyboardArrowUp,
                                                    null
                                                )
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                            modifier = Modifier.clickable { showRecs = !showRecs }
                                        )

                                        Recommendations(chatMessage.recommendationResponse.recommendations)
                                    }
                                } else {
                                    ListItem(
                                        headlineContent = {
                                            Column {
                                                chatMessage
                                                    .recommendationResponse
                                                    .recommendations
                                                    .forEach { Text(it.title) }
                                            }
                                        },
                                        trailingContent = {
                                            Icon(Icons.Default.KeyboardArrowDown, null)
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier.clickable {
                                            showRecs = !showRecs
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is Message.User -> {
                        SelectionContainer {
                            Text(
                                text = chatMessage.text,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Recommendations(
    recommendations: List<Recommendation>,
) {
    recommendations.forEach {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            ListItem(
                headlineContent = { Text(it.title) },
                supportingContent = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(it.description)
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(0.5f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Reason: " + it.reason)
                    }
                },
                overlineContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        it.genre.forEach {
                            Text(it)
                        }
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetScroll: () -> Unit = {},
) {
    var userMessage by rememberSaveable { mutableStateOf("") }

    ElevatedCard(
        shape = RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 12.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        ),
        modifier = modifier
            .animateContentSize()
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = userMessage,
            label = { Text("Message") },
            onValueChange = { userMessage = it },
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (userMessage.isNotBlank()) {
                        onSendMessage(userMessage)
                        userMessage = ""
                        resetScroll()
                    }
                }
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (userMessage.isNotBlank()) {
                            onSendMessage(userMessage)
                            userMessage = ""
                            resetScroll()
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "send",
                        modifier = Modifier
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}