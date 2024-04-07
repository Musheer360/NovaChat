package com.musheer360.novachat

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private var animationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Find the views we'll be using
        val ETPrompt = findViewById<EditText>(R.id.ETPrompt)
        val BTNSend = findViewById<ImageButton>(R.id.BTNSend)
        val BTNClear = findViewById<ImageButton>(R.id.BTNClear)
        val chatContainer = findViewById<LinearLayout>(R.id.chat_container)
        val greetingText = findViewById<TextView>(R.id.greeting_text)

        // Set up a click listener for the "Clear" button
        BTNClear.setOnClickListener {
            // Get the number of child views in the chat container
            val childCount = chatContainer.childCount

            // Loop through all child views starting from index 1 (skipping the greeting bubble at index 0)
            for (i in 1 until childCount) {
                // Remove each child view except the greeting bubble
                chatContainer.removeViewAt(1)
            }
        }

        // Display the greeting message with a typewriter effect
        displayGreetingMessage()

        // Set up a click listener for the "Send" button
        BTNSend.setOnClickListener {
            val prompt = ETPrompt.text.toString().trim() // Trim any leading or trailing whitespace
            if (prompt.isNotEmpty()) { // Check if prompt is not empty
                ETPrompt.text.clear()

                // Add the user's question bubble
                val questionBubble = layoutInflater.inflate(R.layout.chat_bubble, null)
                val questionText = questionBubble.findViewById<TextView>(R.id.chat_text)
                questionText.text = prompt
                val questionBubbleParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.END
                    marginStart = resources.getDimensionPixelSize(R.dimen.chat_bubble_margin)
                    marginEnd = resources.getDimensionPixelSize(R.dimen.chat_bubble_margin)
                    topMargin = resources.getDimensionPixelSize(R.dimen.chat_bubble_top_margin)
                }
                questionBubble.layoutParams = questionBubbleParams
                chatContainer.addView(questionBubble)

                // Add a dummy response bubble
                val responseBubble = layoutInflater.inflate(R.layout.chat_bubble, null)
                val responseBubbleParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.START
                    marginStart = resources.getDimensionPixelSize(R.dimen.chat_bubble_margin)
                    marginEnd = resources.getDimensionPixelSize(R.dimen.chat_bubble_margin)
                    topMargin = resources.getDimensionPixelSize(R.dimen.chat_bubble_top_margin)
                }
                responseBubble.layoutParams = responseBubbleParams
                chatContainer.addView(responseBubble)

                // Create a GenerativeModel instance to fetch the response
                val generativeModel = GenerativeModel(
                    modelName = "gemini-pro",
                    apiKey = "API-KEY-HERE"
                )

                // Start animation to show "Generating response..." while waiting for the response
                animationJob = CoroutineScope(Dispatchers.Main).launch {
                    animateDots(responseBubble)
                }

                CoroutineScope(Dispatchers.Main).launch {
                    // Fetch response from the generative model
                    val response = generativeModel.generateContent(prompt)

                    // Cancel the animation
                    animationJob?.cancel()

                    // Find the dummy response bubble and update its text with the response
                    val responseText = responseBubble.findViewById<TextView>(R.id.chat_text)
                    responseText.text = ""
                    displayTextWithTypewriterEffect(response.text.toString(), responseText)

                    // Check if the text is longer than the visible area of the ScrollView
                    val scrollView = findViewById<ScrollView>(R.id.scrollView)
                    val isTextTooLong = responseText.height > scrollView.height

                    // Scroll to the bottom of the ScrollView only if the text is too long
                    if (isTextTooLong) {
                        scrollView.post {
                            scrollView.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
            } else {
                // Prompt is empty, display a message to the user
                Toast.makeText(this, "Please enter a prompt!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Display the greeting message with a typewriter effect
    private fun displayGreetingMessage() {
        val greetingText = findViewById<TextView>(R.id.greeting_text)
        CoroutineScope(Dispatchers.Main).launch {
            displayTextWithTypewriterEffect(
                "Hello, I'm Nova - an AI assistant created by Musheer Alam aka Musheer360 using the Gemini API. I don't currently have contextual understanding capabilities, so I can only respond to the specific message you send me. However, I'll do my best to provide helpful and relevant responses based on your inquiries. Please feel free to ask me anything!",
                greetingText
            )
        }
    }

    // Display text with a typewriter effect
    private suspend fun displayTextWithTypewriterEffect(text: String, textView: TextView) {
        var isBold = false
        val boldStringBuilder = StringBuilder()
        for (char in text) {
            if (char == '*') {
                isBold = !isBold
                if (!isBold) {
                    textView.append(boldStringBuilder.toString())
                    boldStringBuilder.clear()
                }
            } else {
                if (isBold) {
                    boldStringBuilder.append(char)
                } else {
                    textView.append(char.toString())
                }
            }
            delay(1)
        }
    }

    // Animate the "Generating response..." text
    private suspend fun animateDots(responseBubble: View) {
        val responseText = responseBubble.findViewById<TextView>(R.id.chat_text)
        while (true) {
            responseText.text = "Generating response.  "
            delay(500)
            responseText.text = "Generating response.. "
            delay(500)
            responseText.text = "Generating response..."
            delay(500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel animation job if the activity is destroyed
        animationJob?.cancel()
    }
}