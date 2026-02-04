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
    private val apiBaseUrl = "http://192.168.31.150:4635"
    private val accessToken = "KEY_ACCESS"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private val appList = mutableListOf<AppData>()

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

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view)

        // Создаем GridLayoutManager с гибкой шириной карточек
        val layoutManager = GridLayoutManager(this, calculateSpanCount())
        recyclerView.layoutManager = layoutManager

        adapter = AppAdapter()
        recyclerView.adapter = adapter

        // Обновляем количество колонок при изменении ориентации
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutManager.spanCount = calculateSpanCount()
        }
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidthPx = displayMetrics.widthPixels
        val screenWidthDp = screenWidthPx / displayMetrics.density

        // Минимальная ширина карточки в dp
        val minCardWidthDp = 300f

        // Рассчитываем сколько карточек поместится
        val maxCardsPerRow = (screenWidthDp / minCardWidthDp).toInt()

        // Проверяем ориентацию
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return when {
            // На телефонах в портретной ориентации - 1 колонка
            screenWidthDp < 600 && !isLandscape -> 1

            // На телефонах в горизонтальной ориентации или маленьких планшетах
            screenWidthDp < 720 -> if (isLandscape) 2 else 1

            // На планшетах
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
                            .addHeader("Authorization", "Bearer $accessToken")
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
                    showMessage("Connection failed")
                }
            } catch (e: Exception) {
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

        // Можно добавить ViewHolder для отображения сообщения
        // или просто оставить пустой список
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Обновляем количество колонок при изменении ориентации
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = calculateSpanCount()
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

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val appName: TextView = itemView.findViewById(R.id.app_name)
            val appUrl: TextView = itemView.findViewById(R.id.app_url)
            val statusIndicator: TextView = itemView.findViewById(R.id.status_indicator)
            val statusText: TextView = itemView.findViewById(R.id.status_text)
            val versionText: TextView = itemView.findViewById(R.id.version_text)
            val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)
            val responseTimeText: TextView = itemView.findViewById(R.id.response_time_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.app_card, parent, false)
            return AppViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
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
        }

        override fun getItemCount(): Int = appList.size
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