package com.anuvad

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.*

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var tts: TextToSpeech
    private lateinit var swapBtn: Button

    private lateinit var inputText: EditText
    private lateinit var resultText: TextView
    private lateinit var translateBtn: Button
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var speakBtn: Button
    private lateinit var micBtn: Button
    private lateinit var googleBtn: Button
    private lateinit var rootLayout: LinearLayout

    private val REQUEST_CODE_SPEECH_INPUT = 100

    private val languageMap = mapOf(
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Hindi" to "hi",
        "Chinese" to "zh",
        "Arabic" to "ar",
        "Japanese" to "ja"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.ENGLISH
            }
        }

        inputText = findViewById(R.id.inputText)
        resultText = findViewById(R.id.resultText)
        translateBtn = findViewById(R.id.translateBtn)
        sourceSpinner = findViewById(R.id.sourceLangSpinner)
        targetSpinner = findViewById(R.id.targetLangSpinner)
        speakBtn = findViewById(R.id.speakBtn)
        micBtn = findViewById(R.id.micBtn)
        rootLayout = findViewById(R.id.rootLayout)
        swapBtn = findViewById(R.id.swapBtn)

        googleBtn = Button(this).apply {
            text = "🌐 View in Google Translate"
        }
        rootLayout.addView(googleBtn)

        val languages = languageMap.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        sourceSpinner.adapter = adapter
        targetSpinner.adapter = adapter

        sourceSpinner.setSelection(languages.indexOf("English"))
        targetSpinner.setSelection(languages.indexOf("Hindi"))

        // 🔁 Swap languages
        swapBtn.setOnClickListener {
            val src = sourceSpinner.selectedItemPosition
            val tgt = targetSpinner.selectedItemPosition
            sourceSpinner.setSelection(tgt)
            targetSpinner.setSelection(src)
        }

        // 🚫 Prevent duplicate languages
        sourceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedSource = sourceSpinner.selectedItem.toString()
                val filteredTargets = languageMap.keys.filter { it != selectedSource }
                val targetAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, filteredTargets)
                targetSpinner.adapter = targetAdapter
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Translate
        translateBtn.setOnClickListener {
            val text = inputText.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "Please enter or speak text first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceLang = sourceSpinner.selectedItem?.toString() ?: "English"
            val targetLang = targetSpinner.selectedItem?.toString() ?: "Hindi"

            if (sourceLang == targetLang) {
                Toast.makeText(this, "Please select two different languages", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceCode = languageMap[sourceLang] ?: "en"
            val targetCode = languageMap[targetLang] ?: "hi"

            translateText(text, sourceCode, targetCode)
        }

        speakBtn.setOnClickListener {
            val textToSpeak = resultText.text.toString()
            if (textToSpeak.isNotEmpty()) {
                tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        micBtn.setOnClickListener {
            startVoiceInput()
        }

        googleBtn.setOnClickListener {
            val text = inputText.text.toString()
            if (text.isNotBlank()) {
                val sourceCode = languageMap[sourceSpinner.selectedItem.toString()] ?: "en"
                val targetCode = languageMap[targetSpinner.selectedItem.toString()] ?: "hi"
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.google.com/?sl=$sourceCode&tl=$targetCode&text=$encoded&op=translate"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } else {
                Toast.makeText(this, "Please enter text to open in Google Translate", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun translateText(text: String, source: String, target: String) {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$source|$target"

        val loading = ProgressDialog(this).apply {
            setMessage("Translating...")
            setCancelable(false)
            show()
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loading.dismiss()
                    showRetryDialog(text, source, target)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                loading.dismiss()
                Log.d("TranslateDebug", "Response: $responseBody")

                if (!response.isSuccessful || responseBody == null) {
                    runOnUiThread {
                        showRetryDialog(text, source, target)
                    }
                    return
                }

                try {
                    val json = JSONObject(responseBody)
                    val translated = json
                        .getJSONObject("responseData")
                        .getString("translatedText")

                    runOnUiThread {
                        resultText.text = translated
                    }
                } catch (e: Exception) {
                    Log.e("TranslateDebug", "JSON parse error", e)
                    runOnUiThread {
                        resultText.text = "Error parsing translation"
                    }
                }
            }
        })
    }

    private fun showRetryDialog(text: String, source: String, target: String) {
        AlertDialog.Builder(this)
            .setTitle("Translation Failed")
            .setMessage("Do you want to try again?")
            .setPositiveButton("Retry") { _, _ ->
                translateText(text, source, target)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now…")
        }

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (e: Exception) {
            Toast.makeText(this, "Your device doesn't support voice input", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == Activity.RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            inputText.setText(result?.firstOrNull() ?: "")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}
