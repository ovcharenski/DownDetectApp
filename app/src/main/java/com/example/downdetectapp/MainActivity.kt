package com.example.downdetectapp

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var autoRefreshJob: Job? = null
    private val refreshInterval = 120000L
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private val appList = mutableListOf<AppData>()
    private var connectionProblem = false
    private val apiBaseUrl: String by lazy {
        loadApiBaseUrlFromConfig()
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransparentStatusBar()
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        setupRefreshButton()
        loadApps()
        startAutoRefresh()
    }

    private fun loadApiBaseUrlFromConfig(): String {
        return try {
            val inputStream: InputStream = resources.openRawResource(R.raw.config)
            val properties = Properties()
            properties.load(inputStream)
            inputStream.close()

            val url = properties.getProperty("api_base_url")
            Log.d("DownDetect", "Loaded API URL from config: $url")
            url
        } catch (e: Exception) {
            Log.e("DownDetect", "Error loading config.properties", e)
            ""
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view)

        val layoutManager = GridLayoutManager(this, calculateSpanCount())

        // ВАЖНО: Добавляем spanSizeLookup для правильного отображения сообщения
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // Если список пустой (показываем сообщение), занимаем все колонки
                return if (appList.isEmpty()) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }

        recyclerView.layoutManager = layoutManager

        adapter = AppAdapter()
        recyclerView.adapter = adapter

        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutManager.spanCount = calculateSpanCount()
        }
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidthPx = displayMetrics.widthPixels
        val screenWidthDp = screenWidthPx / displayMetrics.density

        val minCardWidthDp = 300f

        val maxCardsPerRow = (screenWidthDp / minCardWidthDp).toInt()

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return when {
            screenWidthDp < 600 && !isLandscape -> 1

            screenWidthDp < 720 -> if (isLandscape) 2 else 1

            else -> {
                if (maxCardsPerRow >= 3) 3
                else if (maxCardsPerRow >= 2) 2
                else 1
            }
        }
    }

    private fun setupTransparentStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun setupRefreshButton() {
        findViewById<ImageView>(R.id.refresh_header).setOnClickListener {
            loadApps()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            while (isActive) {
                delay(refreshInterval)
                loadApps()
            }
        }
    }

    private fun loadApps() {
        scope.launch {
            val url = "$apiBaseUrl/api/apps"
            val refreshButton = findViewById<ImageView>(R.id.refresh_header)

            refreshButton.animate().rotationBy(360f).setDuration(500).start()

            try {
                val response = withContext(Dispatchers.IO) {
                    try {
                        Log.d("DownDetect", "Loading apps from: $url")
                        val request = Request.Builder()
                            .url(url)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.string()
                            } else {
                                null
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (response != null) {
                    try {
                        val appsArray = JSONArray(response)
                        appList.clear()
                        connectionProblem = false

                        for (i in 0 until appsArray.length()) {
                            val appObject = appsArray.getJSONObject(i)
                            val appData = parseAppData(appObject)
                            appList.add(appData)
                        }

                        adapter.notifyDataSetChanged()

                        if (appList.isEmpty()) {
                            showMessage("No apps found")
                        }
                    } catch (e: Exception) {
                        showMessage("Data error")
                    }
                } else {
                    connectionProblem = true
                    showMessage("Connection failed")
                }
            } catch (e: Exception) {
                connectionProblem = true
                showMessage("Error loading apps")
            } finally {
                refreshButton.animate().rotation(0f).setDuration(0).start()
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseAppData(appObject: JSONObject): AppData {
        val displayName = appObject.getString("displayName")
        val baseUrl = appObject.getString("baseUrl")
        val isActive = appObject.getBoolean("isActive")

        val appData = AppData(displayName, baseUrl, isActive)

        if (appObject.has("lastCheck") && !appObject.isNull("lastCheck")) {
            val lastCheck = appObject.getJSONObject("lastCheck")

            appData.status = lastCheck.getString("status")

            if (lastCheck.has("version") && !lastCheck.isNull("version")) {
                appData.version = lastCheck.getString("version")
            }

            if (lastCheck.has("checkedAt") && !lastCheck.isNull("checkedAt")) {
                val timestamp = lastCheck.getString("checkedAt")
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val date = inputFormat.parse(timestamp)
                    val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                    appData.timestamp = outputFormat.format(date!!)
                } catch (e: Exception) {
                    appData.timestamp = timestamp
                }
            }

            if (lastCheck.has("responseTime") && !lastCheck.isNull("responseTime")) {
                appData.responseTime = lastCheck.getInt("responseTime")
            }
        }

        return appData
    }

    private fun showMessage(message: String) {
        appList.clear()
        adapter.notifyDataSetChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
        layoutManager?.spanCount = calculateSpanCount()

        // Обновляем spanSizeLookup при изменении конфигурации
        layoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (appList.isEmpty()) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    data class AppData(
        val displayName: String,
        val baseUrl: String,
        val isActive: Boolean,
        var status: String = "unknown",
        var version: String = "--",
        var timestamp: String = "--",
        var responseTime: Int? = null
    )

    inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val VIEW_TYPE_APP = 0
        private val VIEW_TYPE_MESSAGE = 1

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val appName: TextView = itemView.findViewById(R.id.app_name)
            val appUrl: TextView = itemView.findViewById(R.id.app_url)
            val statusIndicator: TextView = itemView.findViewById(R.id.status_indicator)
            val statusText: TextView = itemView.findViewById(R.id.status_text)
            val versionText: TextView = itemView.findViewById(R.id.version_text)
            val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)
            val responseTimeText: TextView = itemView.findViewById(R.id.response_time_text)
        }

        inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val messageText: TextView = itemView.findViewById(R.id.message_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_APP) {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.app_card, parent, false)
                AppViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_card, parent, false)
                MessageViewHolder(view)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (appList.isEmpty()) VIEW_TYPE_MESSAGE else VIEW_TYPE_APP
        }

        override fun getItemCount(): Int {
            return if (appList.isEmpty()) 1 else appList.size
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is AppViewHolder) {
                val app = appList[position]

                holder.appName.text = app.displayName
                holder.appUrl.text = app.baseUrl

                if (!app.isActive) {
                    holder.appName.setTextColor(
                        ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)
                    )
                }

                when (app.status.lowercase(Locale.US)) {
                    "healthy" -> {
                        holder.statusText.text = "Healthy"
                        holder.statusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_healthy))
                        holder.statusIndicator.setBackgroundResource(R.drawable.circle_green)
                    }
                    "unhealthy", "down" -> {
                        holder.statusText.text = "Unhealthy"
                        holder.statusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_unhealthy))
                        holder.statusIndicator.setBackgroundResource(R.drawable.circle_red)
                    }
                    "degraded", "warning" -> {
                        holder.statusText.text = "Degraded"
                        holder.statusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_degraded))
                        holder.statusIndicator.setBackgroundResource(R.drawable.circle_yellow)
                    }
                    else -> {
                        holder.statusText.text = "Unknown"
                        holder.statusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_unknown))
                        holder.statusIndicator.setBackgroundResource(R.drawable.circle_gray)
                    }
                }

                holder.versionText.text = "Version: ${app.version}"
                holder.timestampText.text = "Time: ${app.timestamp}"

                val responseTime = app.responseTime ?: "--"
                holder.responseTimeText.text = "Response time: ${responseTime}${if (responseTime != "--") "ms" else ""}"

                holder.itemView.setOnClickListener {
                    Log.d("DownDetect", "Clicked on app: ${app.displayName}")
                }
            } else if (holder is MessageViewHolder) {
                holder.messageText.text = if (connectionProblem) {
                    "Connection problem. Check your internet connection and server."
                } else {
                    "No applications available"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRefreshJob?.cancel()
        scope.cancel()
    }
}