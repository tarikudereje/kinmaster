package com.arm.aichat.internal

import android.content.Context
import android.util.Log
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import dalvik.annotation.optimization.FastNative
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.IOException

class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine {

    companion object {
        private val TAG = InferenceEngineImpl::class.java.simpleName

        @Volatile
        private var instance: InferenceEngine? = null

        fun getInstance(context: Context): InferenceEngine =
            instance ?: synchronized(this) {
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                require(nativeLibDir.isNotBlank()) { "Invalid native library path!" }
                InferenceEngineImpl(nativeLibDir).also { instance = it }
            }
    }

    // region Native methods (text‑only)
    @FastNative
    private external fun init(nativeLibDir: String)

    @FastNative
    private external fun load(modelPath: String): Int

    @FastNative
    private external fun prepare(): Int

    @FastNative
    private external fun systemInfo(): String

    @FastNative
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String

    @FastNative
    private external fun processSystemPrompt(systemPrompt: String): Int

    @FastNative
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int

    @FastNative
    private external fun generateNextToken(): String?

    @FastNative
    private external fun resetConversationNative()

    @FastNative
    private external fun unload()

    @FastNative
    private external fun shutdown()
    // endregion

    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    private var _readyForSystemPrompt = false
    @Volatile
    private var _cancelGeneration = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        llamaScope.launch {
            try {
                check(_state.value is InferenceEngine.State.Uninitialized)
                _state.value = InferenceEngine.State.Initializing
                System.loadLibrary("ai-chat")
                init(nativeLibDir)
                _state.value = InferenceEngine.State.Initialized
                Log.i(TAG, "Native library loaded. System info:\n${systemInfo()}")
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                throw e
            }
        }
    }

    override suspend fun loadModel(pathToModel: String): Unit = withContext(llamaDispatcher) {
        check(_state.value is InferenceEngine.State.Initialized)
        File(pathToModel).apply { require(exists() && isFile && canRead()) }
        _readyForSystemPrompt = false
        _state.value = InferenceEngine.State.LoadingModel
        if (load(pathToModel) != 0) throw UnsupportedArchitectureException()
        if (prepare() != 0) throw IOException("Failed to prepare context")
        _readyForSystemPrompt = true
        _cancelGeneration = false
        _state.value = InferenceEngine.State.ModelReady
        Log.i(TAG, "Model loaded")
    }

    override suspend fun setSystemPrompt(prompt: String): Unit = withContext(llamaDispatcher) {
        require(prompt.isNotBlank())
        check(_readyForSystemPrompt && _state.value is InferenceEngine.State.ModelReady)
        _readyForSystemPrompt = false
        _state.value = InferenceEngine.State.ProcessingSystemPrompt
        if (processSystemPrompt(prompt) != 0) {
            throw RuntimeException("Failed to process system prompt")
        }
        _readyForSystemPrompt = true
        _state.value = InferenceEngine.State.ModelReady
        Log.i(TAG, "System prompt set")
    }

    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
        require(message.isNotEmpty())
        check(_state.value is InferenceEngine.State.ModelReady)
        try {
            _readyForSystemPrompt = false
            _state.value = InferenceEngine.State.ProcessingUserPrompt
            if (processUserPrompt(message, predictLength) != 0) {
                Log.e(TAG, "processUserPrompt failed")
                return@flow
            }
            _state.value = InferenceEngine.State.Generating
            while (!_cancelGeneration) {
                val token = generateNextToken() ?: break
                if (token.isNotEmpty()) emit(token)
            }
            if (_cancelGeneration) Log.i(TAG, "Generation cancelled")
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: CancellationException) {
            _state.value = InferenceEngine.State.ModelReady
            throw e
        } catch (e: Exception) {
            _state.value = InferenceEngine.State.Error(e)
            throw e
        } finally {
            _readyForSystemPrompt = true
        }
    }.flowOn(llamaDispatcher)

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = withContext(llamaDispatcher) {
        check(_state.value is InferenceEngine.State.ModelReady)
        _readyForSystemPrompt = false
        _state.value = InferenceEngine.State.Benchmarking
        val result = benchModel(pp, tg, pl, nr)
        _state.value = InferenceEngine.State.ModelReady
        _readyForSystemPrompt = true
        result
    }

    override suspend fun resetConversation(): Unit = withContext(llamaDispatcher) {
        check(_state.value is InferenceEngine.State.ModelReady || _state.value is InferenceEngine.State.Generating)
        resetConversationNative()
        Log.i(TAG, "Conversation reset")
    }

    override fun cleanUp() {
        _cancelGeneration = true
        runBlocking(llamaDispatcher) {
            when (val s = _state.value) {
                is InferenceEngine.State.ModelReady -> {
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.UnloadingModel
                    unload()
                    _state.value = InferenceEngine.State.Initialized
                }
                is InferenceEngine.State.Error -> {
                    _state.value = InferenceEngine.State.Initialized
                }
                else -> throw IllegalStateException("Cannot cleanUp in ${s.javaClass.simpleName}")
            }
        }
    }

    override fun destroy() {
        _cancelGeneration = true
        runBlocking(llamaDispatcher) {
            _readyForSystemPrompt = false
            when (_state.value) {
                is InferenceEngine.State.Uninitialized -> {}
                is InferenceEngine.State.Initialized -> shutdown()
                else -> {
                    unload()
                    shutdown()
                }
            }
        }
        llamaScope.cancel()
    }
}