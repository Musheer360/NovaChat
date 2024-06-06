package com.musheer360.novachat

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private var animationJob: Job? = null

    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val rootView = findViewById<ViewGroup>(android.R.id.content)
        rootView.setOnApplyWindowInsetsListener { view, windowInsets ->
            val topInset = windowInsets.displayCutout?.safeInsetTop ?: 0
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            windowInsets
        }

        // Find the views we'll be using
        val ETPrompt = findViewById<EditText>(R.id.ETPrompt)
        val BTNSend = findViewById<ImageButton>(R.id.BTNSend)
        val BTNClear = findViewById<ImageButton>(R.id.BTNClear)
        val chatContainer = findViewById<LinearLayout>(R.id.chat_container)
        val promptContainer = findViewById<LinearLayout>(R.id.prompt_container)  // Initialize promptContainer

        // Set up a click listener for the "Clear" button
        BTNClear.setOnClickListener {
            // Get the number of child views in the chat container
            val childCount = chatContainer.childCount

            // Loop through all child views starting from index 1 (skipping the greeting bubble at index 0)
            for (i in childCount - 1 downTo 1) {
                // Get the view to be removed
                val viewToRemove = chatContainer.getChildAt(i)

                // Create a fade-out animation
                val fadeOutAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
                fadeOutAnimation.duration = 75 // Set the duration of the animation (in milliseconds)

                // Set an animation listener to remove the view after the animation completes
                fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}

                    override fun onAnimationEnd(animation: Animation?) {
                        // Remove the view from the chat container
                        chatContainer.removeView(viewToRemove)
                    }

                    override fun onAnimationRepeat(animation: Animation?) {}
                })

                // Start the fade-out animation on the view with a delay
                val delay = (childCount - i - 1) * 75L // Delay in milliseconds
                viewToRemove.postDelayed({ viewToRemove.startAnimation(fadeOutAnimation) }, delay)
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
                questionBubble.setBackgroundResource(R.drawable.prompt_bubble)
                val userIcon = questionBubble.findViewById<ImageView>(R.id.user_icon)
                userIcon.visibility = View.VISIBLE // Show the user icon
                userIcon.setImageResource(R.drawable.user_icon) // Replace with your desired icon
                val questionText = questionBubble.findViewById<TextView>(R.id.chat_text)
                questionText.text = prompt
                questionText.setOnLongClickListener {
                    copyTextToClipboard(questionText.text.toString())
                    true // Return true to indicate that the long-click event was handled
                }
                val questionTextParams = questionText.layoutParams as LinearLayout.LayoutParams
                questionTextParams.topMargin = resources.getDimensionPixelSize(R.dimen.chat_text_top_margin)
                questionTextParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.chat_text_bottom_margin)
                questionText.layoutParams = questionTextParams
                val questionBubbleParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = resources.getDimensionPixelSize(R.dimen.chat_bubble_top_margin)
                }

                questionBubble.layoutParams = questionBubbleParams
                chatContainer.addView(questionBubble)

                // Add a dummy response bubble
                val responseBubble = layoutInflater.inflate(R.layout.chat_bubble, null)
                responseBubble.setBackgroundResource(R.drawable.response_bubble)
                val responseText = responseBubble.findViewById<TextView>(R.id.chat_text)
                responseText.setOnLongClickListener {
                    copyTextToClipboard(responseText.text.toString())
                    true // Return true to indicate that the long-click event was handled
                }
                val responseBubbleParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = resources.getDimensionPixelSize(R.dimen.chat_bubble_top_margin)
                }
                responseBubble.layoutParams = responseBubbleParams
                chatContainer.addView(responseBubble)

                // Create a GenerativeModel instance to fetch the response
                val generativeModel = GenerativeModel(
                    modelName = "gemini-pro",
                    apiKey = "API_KEY_HERE"
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

        // Add layout change listener to adjust margin when the keyboard is shown/hidden
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            val layoutParams = promptContainer.layoutParams as ViewGroup.MarginLayoutParams
            if (keypadHeight > screenHeight * 0.15) { // Keyboard is open
                layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.keyboard_open_margin_bottom)
            } else { // Keyboard is closed
                layoutParams.bottomMargin = 0
            }
            promptContainer.layoutParams = layoutParams
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

    private fun copyTextToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("chat_text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
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