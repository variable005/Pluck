package com.example.pluck.data.provider

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.pluck.data.network.StoryApi
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.ConnectionResult
import com.example.pluck.domain.model.GeneratedStory
import com.example.pluck.domain.model.StoryGenerationInput
import com.example.pluck.domain.provider.StoryProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
private val json = Json { ignoreUnknownKeys = true }

private class ProviderHttpException(val code: Int, message: String) : IOException(message)

private fun storyPrompt(input: StoryGenerationInput): String = buildString {
    append("You are Pluck's invisible fiction writer. The attached images are an ordered journey. Analyze EVERY image and its accompanying metadata. Create one continuous fictional narrative inspired by the places, not a diary, journal, travelogue, or image-by-image description. Each place must matter naturally to the plot. Keep characters, stakes, chronology, and details consistent. Never present uncertain location or identity guesses as facts. Write 700 to 1200 words in ${input.locale}. ")
    append("Narrative mood: ${input.mood.promptDirection}. Let this guide the voice, imagery, pacing, and atmosphere while keeping the story cohesive. ")
    input.genre?.let { append("Genre: $it. ") }
    append("Return exactly this format: TITLE: a compelling title, followed by a newline and STORY: followed by the narrative.")
    input.photos.forEachIndexed { index, photo ->
        append("\nImage ${index + 1}: captured at ${photo.timestamp}; place hint: ${photo.address ?: "not supplied"}; coordinates: ${photo.latitude ?: "unknown"}, ${photo.longitude ?: "unknown"}.")
    }
}

private fun parseStory(text: String): GeneratedStory {
    val titleMatch = Regex("(?is)TITLE\\s*:\\s*(.+?)(?:\\n|STORY\\s*:)").find(text)
    val bodyMatch = Regex("(?is)STORY\\s*:\\s*(.+)").find(text)
    val title = titleMatch?.groupValues?.get(1)?.trim()?.take(160).orEmpty().ifBlank { "A Plucked Story" }
    val body = bodyMatch?.groupValues?.get(1)?.trim().orEmpty().ifBlank { text.trim() }
    if (body.length < 80) throw IOException("The provider returned an incomplete story. Please try again.")
    return GeneratedStory(title, body)
}

private fun imageBase64(path: String): String {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    var sample = 1
    while (options.outWidth / sample > 1280 || options.outHeight / sample > 1280) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IOException("Unable to read a journey image.")
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        bitmap.recycle()
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}

abstract class RestStoryProvider(private val api: StoryApi) : StoryProvider {
    protected suspend fun request(url: String, headers: Map<String, String>, payload: JsonObject): JsonObject {
        val response = api.post(url, headers, payload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
        val raw = response.body()?.string().orEmpty()
        if (!response.isSuccessful) throw ProviderHttpException(response.code(), raw.take(500).ifBlank { "Provider request failed (${response.code()})" })
        return json.parseToJsonElement(raw).jsonObject
    }

    final override suspend fun testConnection(apiKey: String): ConnectionResult = try {
        probe(apiKey)
        ConnectionResult.Connected
    } catch (error: ProviderHttpException) {
        if (error.code == 401 || error.code == 403) ConnectionResult.InvalidKey else ConnectionResult.Failed(error.message ?: "Provider error")
    } catch (_: IOException) { ConnectionResult.NetworkError }
    catch (error: Exception) { ConnectionResult.Failed(error.message ?: "Unable to test connection") }

    protected abstract suspend fun probe(apiKey: String)
}

@Suppress("LongParameterList")
private fun openAiPayload(model: String, prompt: String, photos: List<String>, maxTokens: Int): JsonObject = buildJsonObject {
    put("model", model)
    put("max_tokens", maxTokens)
    put("messages", buildJsonArray {
        add(buildJsonObject {
            put("role", "user")
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", prompt) })
                photos.forEach { image -> add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$image") }) }) }
            })
        })
    })
}

abstract class OpenAiCompatibleProvider(private val api: StoryApi) : RestStoryProvider(api) {
    protected abstract val endpoint: String
    protected abstract val model: String
    protected open fun headers(key: String) = mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json")

    override suspend fun generateStory(input: StoryGenerationInput, apiKey: String): GeneratedStory {
        val photos = input.photos.map { imageBase64(it.imagePath) }
        val response = request(endpoint, headers(apiKey), openAiPayload(model, storyPrompt(input), photos, 1800))
        return parseStory(response["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
    }

    override suspend fun probe(apiKey: String) {
        request(endpoint, headers(apiKey), openAiPayload(model, "Reply only OK.", emptyList(), 4))
    }
}

class OpenAiStoryProvider @Inject constructor(api: StoryApi) : OpenAiCompatibleProvider(api) {
    override val type = AiProvider.OPENAI
    override val endpoint = "https://api.openai.com/v1/chat/completions"
    override val model = "gpt-4.1-mini"
}

class GroqStoryProvider @Inject constructor(api: StoryApi) : OpenAiCompatibleProvider(api) {
    override val type = AiProvider.GROQ
    override val endpoint = "https://api.groq.com/openai/v1/chat/completions"
    override val model = "meta-llama/llama-4-scout-17b-16e-instruct"
}

class TogetherStoryProvider @Inject constructor(api: StoryApi) : OpenAiCompatibleProvider(api) {
    override val type = AiProvider.TOGETHER
    override val endpoint = "https://api.together.xyz/v1/chat/completions"
    override val model = "meta-llama/Llama-4-Scout-17B-16E-Instruct"
}

class OpenRouterStoryProvider @Inject constructor(api: StoryApi) : OpenAiCompatibleProvider(api) {
    override val type = AiProvider.OPENROUTER
    override val endpoint = "https://openrouter.ai/api/v1/chat/completions"
    override val model = "google/gemini-2.5-flash"
    override fun headers(key: String) = super.headers(key) + ("HTTP-Referer" to "https://pluck.local") + ("X-Title" to "Pluck")
}

class GeminiStoryProvider @Inject constructor(private val api: StoryApi) : RestStoryProvider(api) {
    override val type = AiProvider.GEMINI
    private fun url(key: String) = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key"
    private fun payload(prompt: String, photos: List<String>) = buildJsonObject {
        put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }); photos.forEach { encoded -> add(buildJsonObject { put("inlineData", buildJsonObject { put("mimeType", "image/jpeg"); put("data", encoded) }) }) } }) }) })
        put("generationConfig", buildJsonObject { put("maxOutputTokens", 1800); put("temperature", 0.85) })
    }
    override suspend fun generateStory(input: StoryGenerationInput, apiKey: String): GeneratedStory {
        val response = request(url(apiKey), emptyMap(), payload(storyPrompt(input), input.photos.map { imageBase64(it.imagePath) }))
        val text = response["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        return parseStory(text)
    }
    override suspend fun probe(apiKey: String) { request(url(apiKey), emptyMap(), payload("Reply only OK.", emptyList())) }
}

class ClaudeStoryProvider @Inject constructor(private val api: StoryApi) : RestStoryProvider(api) {
    override val type = AiProvider.CLAUDE
    private val endpoint = "https://api.anthropic.com/v1/messages"
    private fun headers(key: String) = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "Content-Type" to "application/json")
    private fun payload(prompt: String, photos: List<String>) = buildJsonObject {
        put("model", "claude-sonnet-4-20250514"); put("max_tokens", 1800)
        put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", buildJsonArray { photos.forEach { encoded -> add(buildJsonObject { put("type", "image"); put("source", buildJsonObject { put("type", "base64"); put("media_type", "image/jpeg"); put("data", encoded) }) }) }; add(buildJsonObject { put("type", "text"); put("text", prompt) }) }) }) })
    }
    override suspend fun generateStory(input: StoryGenerationInput, apiKey: String): GeneratedStory {
        val response = request(endpoint, headers(apiKey), payload(storyPrompt(input), input.photos.map { imageBase64(it.imagePath) }))
        return parseStory(response["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }
    override suspend fun probe(apiKey: String) { request(endpoint, headers(apiKey), payload("Reply only OK.", emptyList())) }
}
