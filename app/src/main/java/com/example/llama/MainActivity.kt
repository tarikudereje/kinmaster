package com.example.llama

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.InferenceEngine
import com.arm.aichat.internal.InferenceEngineImpl
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var attachImageButton: MaterialButton
    private lateinit var statusBadge: TextView

    private lateinit var engine: InferenceEngine
    private var isModelReady = false
    private var modelFilePath: String? = null

    private val messages = mutableListOf<Message>()
    private lateinit var messageAdapter: MessageAdapter

    private lateinit var userProfile: UserProfile
    private val masteredTopics = mutableListOf<String>()
    private val profileFile by lazy { File(filesDir, "profile.json") }
    private val masteredFile by lazy { File(filesDir, "mastered_topics.txt") }

    // Image analysis (ML Kit)
    private val imageProcessor = ImageProcessor()
    private var currentImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "KinMaster"

        messagesRv = findViewById(R.id.messages)
        messagesRv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messageAdapter = MessageAdapter(messages)
        messagesRv.adapter = messageAdapter

        userInputEt = findViewById(R.id.user_input)
        sendButton = findViewById(R.id.fab)
        attachImageButton = findViewById(R.id.btn_attach_image)
        statusBadge = findViewById(R.id.status_badge)

        loadProfile()
        loadMasteredTopics()

        engine = InferenceEngineImpl.getInstance(applicationContext)

        // Send button: only send messages (model must be loaded)
        sendButton.setOnClickListener {
            if (!isModelReady) {
                Toast.makeText(this, "Load a model first (⋮ → Load Model)", Toast.LENGTH_LONG).show()
            } else {
                handleUserInput()
            }
        }

        // Attach image button
        attachImageButton.setOnClickListener {
            if (!isModelReady) {
                Toast.makeText(this, "Load a model first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickImage.launch("image/*")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_load_model -> {
                pickModel.launch(arrayOf("*/*"))
                true
            }
            R.id.action_edit_profile -> {
                ProfileEditorDialog(this, userProfile) { updated ->
                    userProfile = updated
                    saveProfile()
                    Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                    if (isModelReady) {
                        lifecycleScope.launch { setTutorSystemPrompt() }
                    }
                }.show()
                true
            }
            R.id.action_manage_mastered -> {
                startActivity(android.content.Intent(this, MasteredTopicsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadProfile() {
        userProfile = if (profileFile.exists()) {
            UserProfile.fromJson(profileFile.readText()) ?: UserProfile()
        } else UserProfile()
    }

    private fun saveProfile() {
        profileFile.writeText(userProfile.toJson())
    }

    private fun loadMasteredTopics() {
        if (masteredFile.exists()) {
            masteredTopics.clear()
            masteredTopics.addAll(masteredFile.readLines().filter { it.isNotBlank() })
        }
    }

    private fun saveMasteredTopics() {
        masteredFile.writeText(masteredTopics.joinToString("\n"))
    }

    private suspend fun setTutorSystemPrompt() {
        val masteredListStr = if (masteredTopics.isEmpty()) "none" else masteredTopics.joinToString(", ")
        val profileStr = "Age: ${userProfile.age}, Education: ${userProfile.educationLevel}, " +
                "Working areas: ${userProfile.workingAreas.joinToString()}, " +
                "Years experience: ${userProfile.yearsExperience}, Other: ${userProfile.otherInfo}"
        val systemPrompt = """
You are **Mastery Tutor** – an expert teacher who follows the user’s natural learning path.  
Your goal is to help the student understand, not to test them endlessly.

User profile: $profileStr  
Topics already mastered: $masteredListStr  

### SPECIAL INSTRUCTIONS (must follow)

1. **When the user attaches an image** (you will see `[IMAGE ANALYSIS]` in the prompt):
   - First, clearly describe what the image shows (objects, text, layout).
   - If the image contains educational content (diagram, chart, formula, code, textbook page), teach it like a teacher – explain the concept step by step.
   - If the image is not educational (e.g., a photo of a cat, a landscape), just describe it naturally and answer any follow up question.
   - Do not ask the user what they already know about the image – just teach or explain.

2. **When the user says "I don't know" or expresses confusion**:
   - Do **not** keep asking probing questions.
   - Immediately teach the topic from the very basics (first principles).
   - Assume zero prior knowledge. Break it down into the smallest pieces.
   - Use simple analogies and short sentences.
   - After teaching a small chunk, ask **one simple check**: "Does that make sense?" or "Can you repeat that in your own words?"
   - Then continue building up.

3. **For normal (text only) conversations**:
   - You may still use Socratic questioning, but if the user says "I don't know" or seems stuck, switch to direct teaching (as above).

### YOUR STANDARD TEACHING METHOD (use when user is engaged)

1. **Start with a straight question** – but only once. If the user cannot answer, teach instead of repeating the question.
2. **Diagnose the missing gap** – then fill it with explanation.
3. **Teach from first principles** – use short sentences and concrete examples.
4. **Use analogies with explicit mapping** – discard if not helpful.
5. **Active recall** – after teaching, ask a focused question. If wrong or "I don't know", re teach differently.
6. **Build up** from sentence to paragraph to full mental model.
7. **Mastery declaration** (strict criteria) – `[MASTERY_ACHIEVED] Topic Name Here`.
8. **If user wants to change topic** – acknowledge and save progress.

### GOLDEN RULES
- **Never stop teaching** – but adjust your method to the student's response.
- **your response must not be more than 4 sentence unless users asks you to discuss in detail** 
- **If the user says "I don't know", stop questioning and teach from zero.**
- **Keep explanations short** (2-4 sentences) unless depth is requested.
- **Be patient, encouraging, and relentlessly focused on filling the missing gap.**
- **when you notice student do not want to answer and the intention is only learning continue teaching only**
- **when students answer a good answers save it in mastery list and says you mastered the topic**

Now begin. When the user asks to learn a topic or attaches an image, follow this protocol perfectly.
""".trimIndent()
        engine.setSystemPrompt(systemPrompt)
    }

    // Model picker (only from menu)
    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadModel(it) }
    }

    // Function to send warm-up message automatically
    private suspend fun sendWarmupMessage() {
        try {
            val warmupResponse = StringBuilder()
            engine.sendUserPrompt("tell me about your self in short").collect { token ->
                warmupResponse.append(token)
            }
            Log.d("KinMaster", "starting")
        } catch (e: Exception) {
            Log.e("KinMaster", "Warm-up failed", e)
        }
    }

    private fun loadModel(modelUri: Uri) {
        sendButton.isEnabled = false
        attachImageButton.isEnabled = false
        userInputEt.hint = "Loading model..."
        statusBadge.text = "⏳ Loading model..."
        statusBadge.visibility = TextView.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelFile = copyUriToFile(modelUri, "model.gguf")
                modelFilePath = modelFile.absolutePath
                withContext(Dispatchers.Main) {
                    statusBadge.text = "🔧 Loading into engine..."
                }

                engine.loadModel(modelFilePath!!)
                setTutorSystemPrompt()

                // === AUTO WARM-UP: Send "Hello" to initialize the model ===
                withContext(Dispatchers.Main) {
                    statusBadge.text = "🔥 Warming up model..."
                }
                sendWarmupMessage()
                // === END OF WARM-UP ===

                withContext(Dispatchers.Main) {
                    isModelReady = true
                    userInputEt.isEnabled = true
                    sendButton.isEnabled = true
                    attachImageButton.isEnabled = true
                    userInputEt.hint = "Ask KinMaster..."
                    statusBadge.text = "✅ Model ready"
                    Toast.makeText(this@MainActivity, "Model loaded", Toast.LENGTH_SHORT).show()

                    // Add welcome message to chat
                    messages.add(Message(UUID.randomUUID().toString(), "Hello! I'm KinMaster. What would you like to learn today?", false))
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    messagesRv.scrollToPosition(messages.size - 1)

                    Handler(Looper.getMainLooper()).postDelayed({
                        statusBadge.visibility = TextView.GONE
                    }, 3000)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Load failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    sendButton.isEnabled = true
                    attachImageButton.isEnabled = true
                    statusBadge.text = "❌ Failed"
                    statusBadge.visibility = TextView.VISIBLE
                }
            }
        }
    }

    private fun copyUriToFile(uri: Uri, fileName: String): File {
        val destFile = File(filesDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    // Image picker
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val imageFile = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(it)?.use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }
            currentImagePath = imageFile.absolutePath
            Toast.makeText(this, "🖼️ Image attached. Type your question.", Toast.LENGTH_LONG).show()
        }
    }

    // Unified handler for text‑only and image+text input
    private fun handleUserInput() {
        val userMsg = userInputEt.text.toString()
        if (userMsg.isEmpty() && currentImagePath == null) {
            Toast.makeText(this, "Type a message or attach an image", Toast.LENGTH_SHORT).show()
            return
        }

        userInputEt.isEnabled = false
        sendButton.isEnabled = false
        attachImageButton.isEnabled = false

        val imagePath = currentImagePath
        currentImagePath = null
        val userMsgText = userMsg
        userInputEt.text = null

        // Add user message to UI
        val displayText = if (imagePath != null) "[Image] $userMsgText" else userMsgText
        messages.add(Message(UUID.randomUUID().toString(), displayText, true))
        messageAdapter.notifyItemInserted(messages.size - 1)
        messagesRv.scrollToPosition(messages.size - 1)

        // Assistant placeholder
        messages.add(Message(UUID.randomUUID().toString(), "", false))
        val assistantIndex = messages.size - 1
        messageAdapter.notifyItemInserted(assistantIndex)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var finalPrompt = userMsgText

                if (imagePath != null) {
                    withContext(Dispatchers.Main) {
                        statusBadge.text = "🔍 Analyzing image..."
                        statusBadge.visibility = TextView.VISIBLE
                    }
                    val analysis = imageProcessor.analyzeImage(File(imagePath))
                    finalPrompt = """
                        [IMAGE ANALYSIS]
                        Objects/Labels: ${analysis.labels}
                        Text in image: ${if (analysis.extractedText.isNotBlank()) "\"${analysis.extractedText}\"" else "none"}
                        Student's question: $userMsgText
                        Answer as Mastery Tutor.
                    """.trimIndent()
                }

                val fullResponse = StringBuilder()
                engine.sendUserPrompt(finalPrompt).collect { token ->
                    fullResponse.append(token)
                    withContext(Dispatchers.Main) {
                        val updated = messages[assistantIndex].copy(content = fullResponse.toString())
                        messages[assistantIndex] = updated
                        messageAdapter.notifyItemChanged(assistantIndex)
                        messagesRv.scrollToPosition(assistantIndex)
                    }
                }

                // Mastery detection
                val responseText = fullResponse.toString()
                val masteryPattern = Regex("\\[MASTERY_ACHIEVED\\]\\s*(.*?)(?:\n|$)")
                val match = masteryPattern.find(responseText)
                if (match != null) {
                    val topic = match.groupValues[1].trim()
                    withContext(Dispatchers.Main) { showMasteryConfirmationDialog(topic) }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    if (messages.size > assistantIndex) {
                        messages.removeAt(assistantIndex)
                        messageAdapter.notifyItemRemoved(assistantIndex)
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    userInputEt.isEnabled = true
                    sendButton.isEnabled = true
                    attachImageButton.isEnabled = true
                    userInputEt.requestFocus()
                    statusBadge.visibility = TextView.GONE
                }
            }
        }
    }

    private fun showMasteryConfirmationDialog(topic: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Topic Mastered?")
            .setMessage("The model believes you have mastered: '$topic'. Do you want to add this to your mastered topics list?")
            .setPositiveButton("Yes") { _, _ ->
                if (!masteredTopics.contains(topic)) {
                    masteredTopics.add(topic)
                    saveMasteredTopics()
                    Toast.makeText(this, "Added '$topic' to mastered topics", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        engine.resetConversation()
                        setTutorSystemPrompt()
                        withContext(Dispatchers.Main) {
                            messages.clear()
                            messageAdapter.notifyDataSetChanged()
                            messages.add(Message(UUID.randomUUID().toString(), "✅ Mastered '$topic'! Ready for next topic.", false))
                            messageAdapter.notifyItemInserted(0)
                        }
                    }
                } else {
                    Toast.makeText(this, "Topic already mastered", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Not yet", null)
            .show()
    }

    override fun onDestroy() {
        engine.cleanUp()
        engine.destroy()
        super.onDestroy()
    }
}