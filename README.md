# 🚀 DownDetect Android App

A clean, responsive Android application for monitoring the health and status of your web applications and services.  
Fetches real-time data from a monitoring API and displays it in a beautiful card-based interface.

---

## 📱 Features

- ✅ **Real-time app monitoring** with status indicators (Healthy/Unhealthy/Degraded)
- ✅ **Adaptive grid layout** that adjusts to screen size and orientation
- ✅ **Automatic refresh** every 2 minutes + manual refresh option
- ✅ **Clean Material Design** with card-based UI and status colors
- ✅ **Response time tracking** for performance monitoring
- ✅ **Version and timestamp display** for each service
- ✅ **Transparent status bar** for modern look
- ✅ **Secure API communication** with token-based authentication

---

## 🎨 UI Components

### `activity_main.xml`
- **Header** with app title and refresh button
- **RecyclerView** with adaptive grid layout
- Transparent status bar integration

### `app_card.xml`
- **CardView** with rounded corners and elevation
- App name and URL display
- **Status indicator** (colored circle + text)
- Version, timestamp, and response time sections
- Clean typography with proper color hierarchy

---

## 🛠️ Technical Implementation

### `MainActivity.kt`
- **Coroutine-based** async API calls
- **OkHttp** for network requests
- **JSON parsing** with error handling
- **Dynamic span calculation** for GridLayoutManager
- **Auto-refresh** with configurable interval
- **Configuration change handling** (orientation support)

### `GridSpacingItemDecoration.kt`
- Custom spacing for grid items
- Edge-aware padding calculation
- Consistent spacing across different screen sizes

---

## 📊 Data Model

```kotlin
data class AppData(
    val displayName: String,      // App display name
    val baseUrl: String,          // App base URL
    val isActive: Boolean,        // Active status
    var status: String = "unknown", // Health status
    var version: String = "--",   // App version
    var timestamp: String = "--", // Last check time
    var responseTime: Int? = null // Response time in ms
)
```

---

## 🔌 API Integration

### Configuration
```kotlin
private val apiBaseUrl = "http://example.com"
private val accessToken = "KEY_ACCESS"
```

### Endpoint
- `GET /api/apps` - Returns JSON array of application data

### Expected Response Format
```json
[
  {
    "displayName": "App Name",
    "baseUrl": "https://app.example.com",
    "isActive": true,
    "lastCheck": {
      "status": "healthy",
      "version": "1.2.3",
      "checkedAt": "2024-01-01T12:00:00.000Z",
      "responseTime": 245
    }
  }
]
```

---

## 🎯 Status Indicators

| Status        | Color  | Indicator | Description                    |
|---------------|--------|-----------|--------------------------------|
| **Healthy**   | Green  | ●         | Service is running normally    |
| **Unhealthy** | Red    | ●         | Service is down or critical    |
| **Degraded**  | Yellow | ●         | Service has issues but running |
| **Unknown**   | Gray   | ●         | Status cannot be determined    |

---

## 📱 Adaptive Layout

The app automatically adjusts layout based on screen size:

| Screen Width         | Portrait    | Landscape  |
|----------------------|-------------|------------|
| **Phone** (< 600dp)  | 1 column    | 2 columns  |
| **Tablet** (≥ 720dp) | 2-3 columns | 3+ columns |

---

## 🔄 Refresh Mechanism

- **Manual refresh**: Tap refresh button in header
- **Auto-refresh**: Every 2 minutes (120,000ms)
- **On resume**: Refresh when app returns to foreground
- **Visual feedback**: Refresh button rotation animation

---

## 🚀 Setup & Installation

### Prerequisites
- Android Studio
- API server running at specified URL
- Valid access token

### Configuration
1. Update API URL in `MainActivity.kt`:
   ```kotlin
   private val apiBaseUrl = "http://your-api-url:port"
   private val accessToken = "your_access_token"
   ```

2. Ensure API returns data in expected JSON format

### Building
1. Clone or import project
2. Sync Gradle dependencies
3. Build and run on device/emulator

---

## 🎨 Customization

### Colors
Edit `res/values/colors.xml`:
```xml
<color name="status_healthy">#4CAF50</color>
<color name="status_unhealthy">#F44336</color>
<color name="status_degraded">#FF9800</color>
<color name="status_unknown">#9E9E9E</color>
```

### Layout
- Adjust card width in `calculateSpanCount()` method
- Modify spacing in `GridSpacingItemDecoration`
- Update card appearance in `app_card.xml`

---

## 🔧 Troubleshooting

### Common Issues

1. **No data shown**
    - Check API connectivity
    - Verify access token
    - Ensure API returns proper JSON

2. **Layout issues**
    - Test on different screen sizes
    - Check GridLayoutManager span count

3. **Refresh not working**
    - Check auto-refresh interval
    - Verify coroutine scope is active

---

**Version:** 1.1.0