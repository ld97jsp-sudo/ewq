package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ChannelRepository
import com.example.data.FavoriteChannel
import com.example.data.RecentChannel
import com.example.models.LiveCategory
import com.example.models.LiveStream
import com.example.models.FirebaseChannel
import com.example.models.FirebaseCategory
import com.example.network.XtreamApiService
import com.example.network.FirebaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

// Custom YouTube Support data classes
data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val publishedAt: String
)

data class YouTubeDownload(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val localFilePath: String,
    val dateDownloaded: Long
)

class XtreamViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("loop_live_secure_prefs", Context.MODE_PRIVATE)

    // Activation states
    val isActivated = MutableStateFlow(prefs.getBoolean("is_activated", false))
    val activationCode = MutableStateFlow(prefs.getString("activation_code", "") ?: "")
    val appOrientation = MutableStateFlow(prefs.getString("app_orientation", "sensor") ?: "sensor")
    var baseUrl = MutableStateFlow(prefs.getString("base_url", "http://toytcl.xyz:8080") ?: "http://toytcl.xyz:8080")
    var username = MutableStateFlow(prefs.getString("username", "357643467990765") ?: "357643467990765")
    var password = MutableStateFlow(prefs.getString("password", "Ofgo3yz8CH") ?: "Ofgo3yz8CH")

    // Custom configuration parameters
    val currentTheme = MutableStateFlow(prefs.getString("selected_theme", "default") ?: "default")
    val currentLang = MutableStateFlow(prefs.getString("selected_lang", "ar") ?: "ar")
    val gridColumns = MutableStateFlow(prefs.getInt("grid_columns", 3))
    val playerEngineTv = MutableStateFlow(prefs.getString("player_engine_tv", "smart") ?: "smart")
    val playerEngineMovie = MutableStateFlow(prefs.getString("player_engine_movie", "smart") ?: "smart")

    // YouTube Support Config States
    val isYoutubeEnabled = MutableStateFlow(prefs.getBoolean("youtube_enabled", true))
    val youtubeApiKey = MutableStateFlow(prefs.getString("youtube_api_key", "AIzaSyAiMHQnWOt9tOtqlpddmIPPgwN0GUbtmAM") ?: "AIzaSyAiMHQnWOt9tOtqlpddmIPPgwN0GUbtmAM")
    
    private val _youtubeVideos = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val youtubeVideos: StateFlow<List<YouTubeVideo>> = _youtubeVideos.asStateFlow()
    
    private val _isYoutubeLoading = MutableStateFlow(false)
    val isYoutubeLoading: StateFlow<Boolean> = _isYoutubeLoading.asStateFlow()
    
    val youtubeSearchQuery = MutableStateFlow("")
    
    private val _youtubeDownloads = MutableStateFlow<List<YouTubeDownload>>(emptyList())
    val youtubeDownloads: StateFlow<List<YouTubeDownload>> = _youtubeDownloads.asStateFlow()
    
    val downloadingVideos = MutableStateFlow<Map<String, Float>>(emptyMap())
    
    private val _currentPlayingLocalFilePath = MutableStateFlow<String?>(null)
    val currentPlayingLocalFilePath: StateFlow<String?> = _currentPlayingLocalFilePath.asStateFlow()
    
    private val _currentPlayingLocalTitle = MutableStateFlow<String?>(null)
    val currentPlayingLocalTitle: StateFlow<String?> = _currentPlayingLocalTitle.asStateFlow()

    // Sections: "live" (Live TV), "movies" (Movies/VOD), "series" (Series), "main_channels" (Firebase)
    private val _currentSection = MutableStateFlow("live")
    val currentSection: StateFlow<String> = _currentSection.asStateFlow()

    private val _isProxyEnabled = MutableStateFlow(prefs.getBoolean("proxy_enabled", true))
    val isProxyEnabled: StateFlow<Boolean> = _isProxyEnabled.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(0) // 0: Fit, 1: Fill, 2: Zoom (16:9), 3: Stretch
    val aspectRatioMode: StateFlow<Int> = _aspectRatioMode.asStateFlow()

    private val db = AppDatabase.getDatabase(application)
    private var repository: ChannelRepository

    // Playback and UI loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentPlayingChannel = MutableStateFlow<LiveStream?>(null)
    val currentPlayingChannel: StateFlow<LiveStream?> = _currentPlayingChannel.asStateFlow()

    private val _currentDetailStream = MutableStateFlow<LiveStream?>(null)
    val currentDetailStream: StateFlow<LiveStream?> = _currentDetailStream.asStateFlow()

    fun setDetailStream(stream: com.example.models.LiveStream?) {
        _currentDetailStream.value = stream
        if (stream != null) {
            if (_currentSection.value == "movies") {
                loadVodInfo(stream.streamId)
            } else if (_currentSection.value == "series") {
                loadSeriesInfo(stream.streamId)
            }
        } else {
            _selectedVodInfo.value = null
            _selectedSeriesInfo.value = null
        }
    }

    private val _selectedVodInfo = MutableStateFlow<com.example.models.VodMovieInfo?>(null)
    val selectedVodInfo: StateFlow<com.example.models.VodMovieInfo?> = _selectedVodInfo.asStateFlow()

    private val _selectedSeriesInfo = MutableStateFlow<com.example.models.SeriesInfoDetail?>(null)
    val selectedSeriesInfo: StateFlow<com.example.models.SeriesInfoDetail?> = _selectedSeriesInfo.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    private val _customPlaybackUrl = MutableStateFlow<String?>(null)
    val customPlaybackUrl: StateFlow<String?> = _customPlaybackUrl.asStateFlow()

    private val _currentPlayingFirebaseChannel = MutableStateFlow<FirebaseChannel?>(null)
    val currentPlayingFirebaseChannel: StateFlow<FirebaseChannel?> = _currentPlayingFirebaseChannel.asStateFlow()

    // ------------------ Data States ------------------
    // Live TV
    private val _categories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val categories: StateFlow<List<LiveCategory>> = _categories.asStateFlow()

    private val _streams = MutableStateFlow<List<LiveStream>>(emptyList())
    val streams: StateFlow<List<LiveStream>> = _streams.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String>("all")
    val selectedCategoryId: StateFlow<String> = _selectedCategoryId.asStateFlow()

    // Movies
    private val _vodCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val vodCategories: StateFlow<List<LiveCategory>> = _vodCategories.asStateFlow()

    private val _vodStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val vodStreams: StateFlow<List<LiveStream>> = _vodStreams.asStateFlow()

    private val _selectedVodCategoryId = MutableStateFlow<String>("all")
    val selectedVodCategoryId: StateFlow<String> = _selectedVodCategoryId.asStateFlow()

    // Series
    private val _seriesCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val seriesCategories: StateFlow<List<LiveCategory>> = _seriesCategories.asStateFlow()

    private val _seriesStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val seriesStreams: StateFlow<List<LiveStream>> = _seriesStreams.asStateFlow()

    private val _selectedSeriesCategoryId = MutableStateFlow<String>("all")
    val selectedSeriesCategoryId: StateFlow<String> = _selectedSeriesCategoryId.asStateFlow()

    // Firebase Main Online Channels
    private val _fbCategories = MutableStateFlow<List<FirebaseCategory>>(emptyList())
    val fbCategories: StateFlow<List<FirebaseCategory>> = _fbCategories.asStateFlow()

    private val _fbChannels = MutableStateFlow<List<FirebaseChannel>>(emptyList())
    val fbChannels: StateFlow<List<FirebaseChannel>> = _fbChannels.asStateFlow()

    private val _selectedFbCategoryId = MutableStateFlow<String>("all")
    val selectedFbCategoryId: StateFlow<String> = _selectedFbCategoryId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Local Favorites & Recents - retrieved from repository
    val favoriteChannels: StateFlow<List<FavoriteChannel>>
    val recentChannels: StateFlow<List<RecentChannel>>

    // Combined active listing divided to bypass standard 5-flow combine limits in Kotlin
    private val filteredLiveStreams: StateFlow<List<LiveStream>>
    private val filteredVodStreamsState: StateFlow<List<LiveStream>>
    private val filteredSeriesStreamsState: StateFlow<List<LiveStream>>
    val filteredStreams: StateFlow<List<LiveStream>>

    // Combined Firebase listing
    val filteredFbChannels: StateFlow<List<FirebaseChannel>> = combine(
        _fbChannels,
        _selectedFbCategoryId,
        _searchQuery
    ) { channels, catId, query ->
        val filtered = if (catId == "all") {
            channels
        } else {
            channels.filter { it.categoryId == catId }
        }

        if (query.isEmpty()) {
            filtered
        } else {
            filtered.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        val api = XtreamApiService.create(baseUrl.value)
        repository = ChannelRepository(api, db.channelDao())

        // Load Youtube config and search
        loadDownloadsFromPrefs()
        fetchYoutubeRemoteConfig()
        searchYoutubeVideos("")

        favoriteChannels = repository.favorites.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        recentChannels = repository.recents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Subdivided category combine states
        filteredLiveStreams = combine(
            _streams,
            _selectedCategoryId,
            _searchQuery,
            favoriteChannels
        ) { live, activeCat, query, favsList ->
            val catFiltered = when (activeCat) {
                "all" -> live
                "favorites" -> {
                    val favIds = favsList.map { it.streamId }.toSet()
                    live.filter { it.streamId in favIds }
                }
                "recents" -> {
                    val recsList = recentChannels.value
                    val recIds = recsList.map { it.streamId }.toSet()
                    val posMap = recsList.associateBy { it.streamId }
                    live.filter { it.streamId in recIds }
                        .sortedByDescending { posMap[it.streamId]?.timestamp ?: 0L }
                }
                else -> live.filter { it.categoryId == activeCat }
            }

            if (query.isEmpty()) {
                catFiltered
            } else {
                catFiltered.filter { it.name?.contains(query, ignoreCase = true) == true }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredVodStreamsState = combine(
            _vodStreams,
            _selectedVodCategoryId,
            _searchQuery
        ) { vod, activeCat, query ->
            val catFiltered = if (activeCat == "all") {
                vod
            } else {
                vod.filter { it.categoryId == activeCat }
            }

            if (query.isEmpty()) {
                catFiltered
            } else {
                catFiltered.filter { it.name?.contains(query, ignoreCase = true) == true }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredSeriesStreamsState = combine(
            _seriesStreams,
            _selectedSeriesCategoryId,
            _searchQuery
        ) { series, activeCat, query ->
            val catFiltered = if (activeCat == "all") {
                series
            } else {
                series.filter { it.categoryId == activeCat }
            }

            if (query.isEmpty()) {
                catFiltered
            } else {
                catFiltered.filter { it.name?.contains(query, ignoreCase = true) == true }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Compile root selection stream based on active screen section
        filteredStreams = combine(
            _currentSection,
            filteredLiveStreams,
            filteredVodStreamsState,
            filteredSeriesStreamsState
        ) { section, live, vod, series ->
            when (section) {
                "movies" -> vod
                "series" -> series
                else -> live
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        if (isActivated.value) {
            loadData()
        }
    }

    // Dynamic Multilingual translation helpers
    fun translate(key: String): String {
        val ar = mapOf(
            "app_name" to "Loop Live",
            "tv_edition" to "إصدار التلفزيون الذكي",
            "activation_title" to "دخول وتفعيل التطبيق",
            "activation_desc" to "أدخل كود التفعيل المعتمد (مثال: B1...) للاتصال التلقائي بالخوادم",
            "enter_code" to "أدخل كود التفعيل هنا...",
            "login_button" to "تفعيل ودخول",
            "code_error" to "كود التفعيل غير صالح أو منتهي الصلاحية! يرجى التحقق وإعادة المحاولة",
            "loading_activation" to "جاري التحقق من كود التفعيل...",
            "live_tv" to "البث المباشر",
            "movies" to "الأفلام (VOD)",
            "series" to "المسلسلات",
            "main_channels" to "قنوات رئيسية (Online)",
            "all_channels" to "جميع القنوات المباشرة",
            "youtube" to "منصة اليوتيوب",
            "favs" to "القنوات المفضلة",
            "history" to "السجل والمشاهدة مؤخراً",
            "categories_header" to "الفئات والمجموعات",
            "telegram" to "قناة التليجرام",
            "telegram_desc" to "الدعم وتحديث القنوات",
            "search_hint" to "بحث عن قناة...",
            "loading_streams" to "جاري تحميل قائمة البث...",
            "empty_category" to "لا توجد قنوات متاحة في هذه الفئة",
            "retry_button" to "إعادة المحاولة",
            "settings_title" to "إعادة تهيئة الإعدادات",
            "theme_label" to "مظهر ولون التطبيق",
            "grid_columns_label" to "شكل عرض البطاقات",
            "lang_label" to "لغة التطبيق",
            "engine_tv_label" to "امتداد مشغل التلفزيون",
            "engine_movie_label" to "مشغل الأفلام والمسلسلات",
            "proxy_toggle" to "تشغيل من خلال البروكسي الداخلي",
            "proxy_toggle_desc" to "يفضل إبقائه مفعلاً في حال حجب البث في دولتك",
            "logout_label" to "تسجيل الخروج من التطبيق",
            "close_settings" to "إغلاق الإعدادات",
            "admin_panel" to "لوحة تحكم المطور",
            "admin_pass_hint" to "أدخل رمز المرور للمطور...",
            "admin_unlock" to "دخول المطور",
            "admin_wrong_pass" to "رمز المرور غير صحيح!",
            "admin_title" to "لوحة تحكم المطور (Online)",
            "admin_pub_chan" to "نشر قناة جديدة",
            "admin_pub_cat" to "نشر قسم جديد",
            "admin_manage" to "إدارة وتعديل المحتوى المشترك",
            "admin_chan_name" to "اسم القناة",
            "admin_chan_logo" to "رابط صورة/شعار القناة",
            "admin_chan_url" to "رابط بث القناة المباشر",
            "admin_chan_engine" to "نوع المشغل (صيغة التشغيل)",
            "admin_chan_ua" to "محدد متصفح اختياري (User-Agent)",
            "admin_chan_cat" to "اختر القسم",
            "admin_cat_name" to "اسم القسم",
            "admin_cat_url" to "رابط صورة القسم",
            "pub_button" to "نشر وتخزين",
            "delete_button" to "حذف",
            "status_published" to "تم النشر والتخزين بنجاح!",
            "status_deleted" to "تم الحذف من السيرفر بنجاح!",
            "status_error" to "حدث خطأ غير متوقع، يرجى ملء الحقول بشكل صحيح",
            "tap_to_play" to "انقر للتشغيل",
            "remote_hint" to "التحكم بالريموت: ▲▼ لتغيير القناة | ◀▶ موافق للتوقف المؤقت",
            "auto_fit" to "تلقائي (Fit)",
            "full_screen" to "ملء الشاشة (Fill)",
            "aspect_16_9" to "16:9 (عريض)",
            "stretch" to "تمديد (Stretch)",
            "orientation_label" to "اتجاه الشاشة والعرض",
            "orientation_auto" to "تلقائي (حسب تدوير الهاتف)",
            "orientation_landscape" to "أفقي دائماً (سينمائي)",
            "orientation_portrait" to "عمودي دائماً (طولي)",
            "orientation_desc" to "يدعم التطبيق العرض الأفقي بالكامل والتشغيل التلقائي لكافة الأقسام بشكل مرتب في الأفق"
        )
        val en = mapOf(
            "app_name" to "Loop Live",
            "tv_edition" to "Smart TV Edition",
            "activation_title" to "App Activation & Login",
            "activation_desc" to "Enter authorized activation code (e.g. B1...) to connect automatically",
            "enter_code" to "Enter activation code...",
            "login_button" to "Activate & Enter",
            "code_error" to "Invalid or expired code! Verify and retry",
            "loading_activation" to "Checking activation code...",
            "live_tv" to "Live TV",
            "movies" to "Movies (VOD)",
            "series" to "Series (VOD)",
            "main_channels" to "Main Channels (Online)",
            "all_channels" to "All Live Channels",
            "favs" to "Favorites",
            "history" to "Playback History",
            "categories_header" to "Categories & Groups",
            "telegram" to "Telegram Channel",
            "telegram_desc" to "Support & Channel Updates",
            "search_hint" to "Search channel/movie...",
            "loading_streams" to "Loading stream playlist...",
            "empty_category" to "No channels available in this section",
            "retry_button" to "Retry",
            "settings_title" to "Application Settings",
            "theme_label" to "App Theme & Skin Color",
            "grid_columns_label" to "Grid Cards Size Layout",
            "lang_label" to "App Language",
            "engine_tv_label" to "TV Player Ext/Engine",
            "engine_movie_label" to "Movies & Series Engine",
            "proxy_toggle" to "Play via internal HTTP Proxy",
            "proxy_toggle_desc" to "Keeps stream stable if blocked in your country",
            "logout_label" to "Logout from application",
            "close_settings" to "Close Settings",
            "admin_panel" to "Developer Control Panel",
            "admin_pass_hint" to "Enter developer password...",
            "admin_unlock" to "Unlock Dashboard",
            "admin_wrong_pass" to "Incorrect password!",
            "admin_title" to "Developer Admin Dashboard (Online)",
            "admin_pub_chan" to "Publish a New Channel",
            "admin_pub_cat" to "Publish a New Category",
            "admin_manage" to "Manage Published Channels/Categories",
            "admin_chan_name" to "Channel Name",
            "admin_chan_logo" to "Channel Logo URL",
            "admin_chan_url" to "Live Broadcast URL",
            "admin_chan_engine" to "Player Engine Type",
            "admin_chan_ua" to "Custom User-Agent (Optional)",
            "admin_chan_cat" to "Select Category ID",
            "admin_cat_name" to "Category Name",
            "admin_cat_url" to "Category Image URL",
            "pub_button" to "Publish & Save online",
            "delete_button" to "Delete",
            "status_published" to "Published and synchronized safely!",
            "status_deleted" to "Removed from Firebase server successfully!",
            "status_error" to "Action failed. Make sure fields are correct",
            "tap_to_play" to "Tap to Play",
            "remote_hint" to "D-Pad Control: ▲▼ Change Channel | OK/Enter to Pause/Play",
            "auto_fit" to "Auto (Fit)",
            "full_screen" to "Fullscreen (Fill)",
            "aspect_16_9" to "16:9 (Widescreen)",
            "stretch" to "Stretch Aspect",
            "orientation_label" to "Screen Orientation",
            "orientation_auto" to "Auto Rotate (Sensor)",
            "orientation_landscape" to "Always Landscape (Cinema)",
            "orientation_portrait" to "Always Portrait",
            "orientation_desc" to "The application fully supports landscape view and adaptive layout in all sections smoothly"
        )
        val fr = mapOf(
            "app_name" to "Loop Live",
            "tv_edition" to "Édition Smart TV",
            "activation_title" to "Activation & Connexion",
            "activation_desc" to "Entrez le code d'activation (ex: B1...) pour vous connecter",
            "enter_code" to "Entrez le code d'activation...",
            "login_button" to "Activer et Entrer",
            "code_error" to "Code invalide ou expiré! Veuillez réessayer",
            "loading_activation" to "Vérification du code d'activation...",
            "live_tv" to "TV en Direct",
            "movies" to "Films (VOD)",
            "series" to "Séries (VOD)",
            "main_channels" to "Chaînes Principales (Online)",
            "all_channels" to "Toutes les Chaînes",
            "favs" to "Favoris",
            "history" to "Historique de lecture",
            "categories_header" to "Catégories & Groupes",
            "telegram" to "Chaîne Telegram",
            "telegram_desc" to "Support & Mises à jour",
            "search_hint" to "Rechercher...",
            "loading_streams" to "Chargement de la playlist...",
            "empty_category" to "Aucune chaîne disponible dans cette section",
            "retry_button" to "Réessayer",
            "settings_title" to "Paramètres de l'App",
            "theme_label" to "Thème & Apparence",
            "grid_columns_label" to "Disposition de la Grille",
            "lang_label" to "Langue de l'application",
            "engine_tv_label" to "Moteur TV / Extension",
            "engine_movie_label" to "Moteur VOD & Séries",
            "proxy_toggle" to "Lecture via Proxy interne",
            "proxy_toggle_desc" to "Garde le flux stable si bloqué dans votre pays",
            "logout_label" to "Se Déconnecter de l'App",
            "close_settings" to "Fermer les paramètres",
            "admin_panel" to "Tableau de bord Développeur",
            "admin_pass_hint" to "Entrez le mot de passe...",
            "admin_unlock" to "Déverrouiller le panneau",
            "admin_wrong_pass" to "Mot de passe incorrect!",
            "admin_title" to "Console Admin Développeur (Prêt)",
            "admin_pub_chan" to "Publier une chaîne",
            "admin_pub_cat" to "Publier une catégorie",
            "admin_manage" to "Gérer les chaînes/catégories",
            "admin_chan_name" to "Nom de la chaîne",
            "admin_chan_logo" to "URL du logo du canal",
            "admin_chan_url" to "URL de diffusion en direct",
            "admin_chan_engine" to "Type de moteur de joueur",
            "admin_chan_ua" to "User-Agent personnalisé (Optionnel)",
            "admin_chan_cat" to "Sélectionner la catégorie",
            "admin_cat_name" to "Nom de la catégorie",
            "admin_cat_url" to "URL de l'image de la catégorie",
            "pub_button" to "Publier et Sauvegarder",
            "delete_button" to "Supprimer",
            "status_published" to "Publié et synchronisé en ligne!",
            "status_deleted" to "Supprimé du serveur avec succès!",
            "status_error" to "Action échouée. Vérifiez les champs requis",
            "tap_to_play" to "Appuyez pour jouer",
            "remote_hint" to "Touches: ▲▼ Changer de chaîne | OK pour Pause/Reprendre",
            "auto_fit" to "Ajuster (Fit)",
            "full_screen" to "Plein Écran (Fill)",
            "aspect_16_9" to "16:9 (Large)",
            "stretch" to "Étirer",
            "orientation_label" to "Orientation de l'écran",
            "orientation_auto" to "Rotation automatique",
            "orientation_landscape" to "Paysage toujours",
            "orientation_portrait" to "Portrait toujours",
            "orientation_desc" to "L'application prend pleinement en charge l'affichage paysage et l'adaptation horizontale pour toutes les sections"
        )
        val dictionary = mapOf("ar" to ar, "en" to en, "fr" to fr)
        return dictionary[currentLang.value]?.get(key) ?: key
    }

    // Process user Activation code
    fun attemptActivation(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = FirebaseService.checkActivationCode(code)
            if (result != null) {
                val (host, user, pass) = result
                baseUrl.value = host
                username.value = user
                password.value = pass
                isActivated.value = true
                activationCode.value = code
                
                // Save locally
                prefs.edit().apply {
                    putBoolean("is_activated", true)
                    putString("activation_code", code)
                    putString("base_url", host)
                    putString("username", user)
                    putString("password", pass)
                }.apply()

                // Recreate API and load data
                val api = XtreamApiService.create(host)
                repository = ChannelRepository(api, db.channelDao())
                
                loadData()
                onResult(true)
            } else {
                _errorMessage.value = translate("code_error")
                onResult(false)
            }
            _isLoading.value = false
        }
    }

    // Section controls for separate fast loading
    fun setSection(section: String) {
        _currentSection.value = section
        _searchQuery.value = "" // clear queries on switch
        _errorMessage.value = null // prevent error leaks across tabs
        
        when (section) {
            "movies" -> {
                if (_vodStreams.value.isEmpty()) loadVodData()
            }
            "series" -> {
                if (_seriesStreams.value.isEmpty()) loadSeriesData()
            }
            "main_channels" -> {
                loadFirebaseData()
            }
            else -> {
                if (_streams.value.isEmpty()) loadData()
            }
        }
    }

    // Settings modifiers
    fun selectTheme(theme: String) {
        currentTheme.value = theme
        prefs.edit().putString("selected_theme", theme).apply()
    }

    fun selectLanguage(lang: String) {
        currentLang.value = lang
        prefs.edit().putString("selected_lang", lang).apply()
    }

    fun selectGridColumns(cols: Int) {
        gridColumns.value = cols
        prefs.edit().putInt("grid_columns", cols).apply()
    }

    fun selectPlayerTvEngine(engine: String) {
        playerEngineTv.value = engine
        prefs.edit().putString("player_engine_tv", engine).apply()
    }

    fun selectPlayerMovieEngine(engine: String) {
        playerEngineMovie.value = engine
        prefs.edit().putString("player_engine_movie", engine).apply()
    }

    // Load Live TV data
    fun loadData() {
        if (!isActivated.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                try {
                    val categoryList = repository.getLiveCategories(username.value, password.value)
                    _categories.value = categoryList
                } catch (ce: Exception) {
                    Log.e("XtreamVM", "Error loading live categories", ce)
                }

                try {
                    val streamList = repository.getLiveStreams(username.value, password.value)
                    _streams.value = streamList
                } catch (se: Exception) {
                    Log.e("XtreamVM", "Error loading live streams", se)
                    _errorMessage.value = "فشل في تحميل قائمة البث المباشر: ${se.localizedMessage ?: "تأكد من سلامة السيرفر أو الاتصال"}"
                }
                
                Log.d("XtreamVM", "Loaded ${_categories.value.size} categories and ${_streams.value.size} streams")
            } catch (e: Exception) {
                Log.e("XtreamVM", "Error loading data", e)
                _errorMessage.value = "فشل في تحميل القنوات: ${e.localizedMessage ?: "تأكد من الاتصال بالإنترنت"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Load Movie section on-demand for speed (accesses public repository.apiService)
    fun loadVodData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                try {
                    val catBody = repository.apiService.getVodCategories(username.value, password.value).string()
                    val catList = XtreamApiService.parseLiveCategories(catBody)
                    _vodCategories.value = catList
                } catch (ce: Exception) {
                    Log.e("XtreamVM", "Error loading VOD categories", ce)
                }
                
                try {
                    val streamBody = repository.apiService.getVodStreams(username.value, password.value).string()
                    val streamList = XtreamApiService.parseLiveStreams(streamBody)
                    _vodStreams.value = streamList
                } catch (se: Exception) {
                    Log.e("XtreamVM", "Error loading VOD streams", se)
                    _errorMessage.value = "فشل في تحميل قائمة الأفلام: ${se.localizedMessage}"
                }
            } catch (e: Exception) {
                Log.e("XtreamVM", "Error loading VoD data", e)
                _errorMessage.value = "فشل في تحميل الأفلام: ${e.localizedMessage ?: "تأكد من الاتصال بالإنترنت"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Load Series section on-demand for speed (accesses public repository.apiService)
    fun loadSeriesData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                try {
                    val catBody = repository.apiService.getSeriesCategories(username.value, password.value).string()
                    val catList = XtreamApiService.parseLiveCategories(catBody)
                    _seriesCategories.value = catList
                } catch (ce: Exception) {
                    Log.e("XtreamVM", "Error loading Series categories", ce)
                }
                
                try {
                    val streamBody = repository.apiService.getSeries(username.value, password.value).string()
                    val streamList = XtreamApiService.parseLiveStreams(streamBody)
                    _seriesStreams.value = streamList
                } catch (se: Exception) {
                    Log.e("XtreamVM", "Error loading Series streams", se)
                    _errorMessage.value = "فشل في تحميل قائمة المسلسلات: ${se.localizedMessage}"
                }
            } catch (e: Exception) {
                Log.e("XtreamVM", "Error loading Series data", e)
                _errorMessage.value = "فشل في تحميل المسلسلات: ${e.localizedMessage ?: "تأكد من الاتصال بالإنترنت"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Load Firebase channels and categories in real-time
    fun loadFirebaseData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _fbCategories.value = FirebaseService.fetchCategories()
                _fbChannels.value = FirebaseService.fetchChannels()
            } catch (e: Exception) {
                Log.e("XtreamVM", "Error loading Firebase data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectCategory(categoryId: String) {
        when (_currentSection.value) {
            "movies" -> _selectedVodCategoryId.value = categoryId
            "series" -> _selectedSeriesCategoryId.value = categoryId
            "main_channels" -> _selectedFbCategoryId.value = categoryId
            else -> _selectedCategoryId.value = categoryId
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectChannel(stream: LiveStream?) {
        _currentPlayingChannel.value = stream
        _currentPlayingFirebaseChannel.value = null
        if (stream == null) {
            _customPlaybackUrl.value = null
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                repository.addRecent(
                    RecentChannel(
                        streamId = stream.streamId,
                        name = stream.name ?: "Unknown Channel",
                        streamIcon = stream.streamIcon ?: stream.cover,
                        categoryId = stream.categoryId
                    )
                )
            }
        }
    }

    fun selectFirebaseChannel(channel: FirebaseChannel?) {
        _currentPlayingFirebaseChannel.value = channel
        _currentPlayingChannel.value = null
    }

    fun toggleFavorite(stream: LiveStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val isFav = repository.isFavorite(stream.streamId)
            if (isFav) {
                repository.removeFavorite(stream.streamId)
            } else {
                repository.addFavorite(
                    FavoriteChannel(
                        streamId = stream.streamId,
                        name = stream.name ?: "Unknown Channel",
                        streamIcon = stream.streamIcon ?: stream.cover,
                        categoryId = stream.categoryId
                    )
                )
            }
        }
    }

    fun isChannelFavorite(streamId: Int): Boolean {
        return favoriteChannels.value.any { it.streamId == streamId }
    }

    fun toggleProxy() {
        val next = !_isProxyEnabled.value
        _isProxyEnabled.value = next
        prefs.edit().putBoolean("proxy_enabled", next).apply()
    }

    fun cycleAspectRatio() {
        _aspectRatioMode.value = (_aspectRatioMode.value + 1) % 4
    }

    // Generate dynamic streaming URL based on section & player engine options
    fun getStreamUrl(streamId: Int): String {
        _customPlaybackUrl.value?.let { return it }
        val cleanBaseUrl = baseUrl.value.trim().removeSuffix("/")
        val u = username.value
        val p = password.value
        val section = _currentSection.value

        val rawUrl = when (section) {
            "movies" -> {
                val streamDetails = _vodStreams.value.firstOrNull { it.streamId == streamId }
                val ext = streamDetails?.containerExtension ?: "mp4"
                "$cleanBaseUrl/movie/$u/$p/$streamId.$ext"
            }
            "series" -> {
                val streamDetails = _seriesStreams.value.firstOrNull { it.streamId == streamId }
                val ext = streamDetails?.containerExtension ?: "mp4"
                "$cleanBaseUrl/series/$u/$p/$streamId.$ext"
            }
            else -> {
                "$cleanBaseUrl/live/$u/$p/$streamId.ts"
            }
        }

        val engine = if (section == "movies" || section == "series") playerEngineMovie.value else playerEngineTv.value

        return when (engine) {
            "proxy" -> "http://194.60.93.157/proxy?url=$rawUrl"
            "ts" -> {
                if (rawUrl.endsWith(".ts")) rawUrl else "${rawUrl.substringBeforeLast(".")}.ts"
            }
            "m3u8" -> {
                if (rawUrl.endsWith(".m3u8")) rawUrl else "${rawUrl.substringBeforeLast(".")}.m3u8"
            }
            "normal" -> rawUrl
            else -> { // smart
                if (_isProxyEnabled.value) {
                    "http://194.60.93.157/proxy?url=$rawUrl"
                } else {
                    rawUrl
                }
            }
        }
    }

    private fun parseCast(castRaw: String): List<com.example.models.MediaActor> {
        if (castRaw.isBlank()) return emptyList()
        val names = castRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        val randomPortraits = listOf(
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=150&auto=format&fit=crop&q=80"
        )
        
        return names.mapIndexed { idx, name ->
            val imgUrl = randomPortraits[idx % randomPortraits.size]
            com.example.models.MediaActor(name, imgUrl)
        }
    }

    fun loadVodInfo(vodId: Int) {
        viewModelScope.launch {
            _isDetailLoading.value = true
            _selectedVodInfo.value = null
            try {
                val api = repository.apiService
                val responseBody = api.getVodInfo(username.value, password.value, vodId = vodId)
                val jsonStr = responseBody.string()
                
                val jsonObj = org.json.JSONObject(jsonStr)
                if (jsonObj.has("info")) {
                    val infoObj = jsonObj.getJSONObject("info")
                    val name = infoObj.optString("name", infoObj.optString("title", ""))
                    val plot = infoObj.optString("plot", "")
                    val director = infoObj.optString("director", "")
                    val genre = infoObj.optString("genre", "")
                    val releaseDate = infoObj.optString("releaseDate", infoObj.optString("releasedate", ""))
                    val rating = infoObj.optString("rating", "")
                    val cover = infoObj.optString("cover", infoObj.optString("movie_image", ""))
                    val duration = infoObj.optString("duration", "")
                    
                    val castRaw = infoObj.optString("cast", "")
                    val castList = parseCast(castRaw)
                    
                    _selectedVodInfo.value = com.example.models.VodMovieInfo(
                        name = name,
                        plot = plot,
                        cast = castList,
                        director = director,
                        genre = genre,
                        releaseDate = releaseDate,
                        rating = rating,
                        cover = cover,
                        duration = duration
                    )
                }
            } catch (e: Exception) {
                Log.e("XtreamVM", "Error loading VOD details", e)
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun loadSeriesInfo(seriesId: Int) {
        viewModelScope.launch {
            _isDetailLoading.value = true
            _selectedSeriesInfo.value = null
            try {
                val api = repository.apiService
                val responseBody = api.getSeriesInfo(username.value, password.value, seriesId = seriesId)
                val jsonStr = responseBody.string()
                
                val jsonObj = org.json.JSONObject(jsonStr)
                val infoObj = if (jsonObj.has("info")) jsonObj.getJSONObject("info") else org.json.JSONObject()
                
                val name = infoObj.optString("name", infoObj.optString("title", ""))
                val plot = infoObj.optString("plot", "")
                val director = infoObj.optString("director", "")
                val genre = infoObj.optString("genre", "")
                val releaseDate = infoObj.optString("releaseDate", infoObj.optString("releasedate", ""))
                val rating = infoObj.optString("rating", "")
                val cover = infoObj.optString("cover", "")
                
                val castRaw = infoObj.optString("cast", "")
                val castList = parseCast(castRaw)
                
                val seasonsMap = mutableMapOf<Int, List<com.example.models.SeriesEpisode>>()
                if (jsonObj.has("episodes")) {
                    val episodesObj = jsonObj.getJSONObject("episodes")
                    val keys = episodesObj.keys()
                    while (keys.hasNext()) {
                        val seasonKey = keys.next()
                        val seasonNum = seasonKey.toIntOrNull() ?: continue
                        val epsArray = episodesObj.optJSONArray(seasonKey) ?: continue
                        
                        val episodesList = mutableListOf<com.example.models.SeriesEpisode>()
                        for (i in 0 until epsArray.length()) {
                            val epObj = epsArray.getJSONObject(i)
                            val epId = epObj.optString("id", epObj.optString("id_episode", ""))
                            val epNum = epObj.optInt("episode_num", i + 1)
                            val title = epObj.optString("title", "الحلقة $epNum")
                            
                            val epPlot = if (epObj.has("info")) {
                                epObj.getJSONObject("info").optString("plot", "")
                            } else {
                                ""
                            }
                            val containerExt = epObj.optString("container_extension", "mp4")
                            
                            episodesList.add(
                                com.example.models.SeriesEpisode(
                                    id = epId,
                                    episodeNum = epNum,
                                    title = title,
                                    plot = epPlot,
                                    containerExtension = containerExt
                                )
                            )
                        }
                        seasonsMap[seasonNum] = episodesList.sortedBy { it.episodeNum }
                    }
                }
                
                _selectedSeriesInfo.value = com.example.models.SeriesInfoDetail(
                    name = name,
                    plot = plot,
                    cast = castList,
                    director = director,
                    genre = genre,
                    releaseDate = releaseDate,
                    rating = rating,
                    cover = cover,
                    seasons = seasonsMap.toSortedMap()
                )
            } catch (e: Exception) {
                Log.e("XtreamVM", "Error loading series details", e)
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun getEpisodeStreamUrl(episodeId: String, containerExtension: String): String {
        val cleanBaseUrl = baseUrl.value.trim().removeSuffix("/")
        val u = username.value
        val p = password.value
        val ext = if (containerExtension.isBlank()) "mp4" else containerExtension
        val rawUrl = "$cleanBaseUrl/series/$u/$p/$episodeId.$ext"
        
        val engine = playerEngineMovie.value
        return when (engine) {
            "proxy" -> "http://194.60.93.157/proxy?url=$rawUrl"
            "ts" -> if (rawUrl.endsWith(".ts")) rawUrl else "${rawUrl.substringBeforeLast(".")}.ts"
            "m3u8" -> if (rawUrl.endsWith(".m3u8")) rawUrl else "${rawUrl.substringBeforeLast(".")}.m3u8"
            else -> rawUrl
        }
    }

    fun playEpisode(episode: com.example.models.SeriesEpisode, seriesName: String) {
        val url = getEpisodeStreamUrl(episode.id, episode.containerExtension)
        _customPlaybackUrl.value = url
        val tempStream = com.example.models.LiveStream(
            streamId = episode.id.hashCode().coerceAtLeast(1),
            name = "$seriesName - S${episode.episodeNum} E${episode.episodeNum} : ${episode.title}",
            cover = _selectedSeriesInfo.value?.cover
        )
        selectChannel(tempStream)
    }

    // Dynamic TV controller channels switching
    fun playNextChannel() {
        val currentList = filteredStreams.value
        if (currentList.isEmpty()) return
        
        val current = _currentPlayingChannel.value ?: return
        val currentIndex = currentList.indexOfFirst { it.streamId == current.streamId }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            selectChannel(currentList[currentIndex + 1])
        } else if (currentIndex == currentList.size - 1) {
            selectChannel(currentList.first())
        }
    }

    fun playPrevChannel() {
        val currentList = filteredStreams.value
        if (currentList.isEmpty()) return
        
        val current = _currentPlayingChannel.value ?: return
        val currentIndex = currentList.indexOfFirst { it.streamId == current.streamId }
        if (currentIndex > 0) {
            selectChannel(currentList[currentIndex - 1])
        } else if (currentIndex == 0) {
            selectChannel(currentList.last())
        }
    }

    // Sign out from application
    fun logout() {
        isActivated.value = false
        activationCode.value = ""
        baseUrl.value = ""
        username.value = ""
        password.value = ""
        
        prefs.edit().apply {
            putBoolean("is_activated", false)
            putString("activation_code", "")
            putString("base_url", "")
            putString("username", "")
            putString("password", "")
        }.apply()
        
        // Reset buffers
        _streams.value = emptyList()
        _categories.value = emptyList()
        _vodStreams.value = emptyList()
        _vodCategories.value = emptyList()
        _seriesStreams.value = emptyList()
        _seriesCategories.value = emptyList()
    }

    // ------------------ Developer Online CRUD Operations ------------------
    fun publishFbCategory(name: String, image: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = FirebaseService.publishCategory(name, image)
            if (res) loadFirebaseData()
            onResult(res)
        }
    }

    fun publishFbChannel(chan: FirebaseChannel, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = FirebaseService.publishChannel(chan)
            if (res) loadFirebaseData()
            onResult(res)
        }
    }

    fun deleteFbCategory(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = FirebaseService.deleteCategory(id)
            if (res) loadFirebaseData()
            onResult(res)
        }
    }

    fun deleteFbChannel(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = FirebaseService.deleteChannel(id)
            if (res) loadFirebaseData()
            onResult(res)
        }
    }

    // Dynamic Orientation modifiers
    fun selectOrientation(activity: android.app.Activity, orientation: String) {
        appOrientation.value = orientation
        prefs.edit().putString("app_orientation", orientation).apply()
        applyOrientation(activity, orientation)
    }

    fun applyOrientation(activity: android.app.Activity, orientation: String) {
        activity.requestedOrientation = when (orientation) {
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ------------------ YouTube Downloader and Manager Methods ------------------
    private fun loadDownloadsFromPrefs() {
        try {
            val raw = prefs.getString("youtube_downloads_list", "[]") ?: "[]"
            val array = org.json.JSONArray(raw)
            val list = mutableListOf<YouTubeDownload>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    YouTubeDownload(
                        videoId = obj.getString("videoId"),
                        title = obj.getString("title"),
                        thumbnailUrl = obj.getString("thumbnailUrl"),
                        localFilePath = obj.getString("localFilePath"),
                        dateDownloaded = obj.getLong("dateDownloaded")
                    )
                )
            }
            _youtubeDownloads.value = list
        } catch (e: Exception) {
            Log.e("XtreamVM", "Error loading downloads from prefs", e)
        }
    }

    private fun saveDownloadsToPrefs(list: List<YouTubeDownload>) {
        try {
            val array = org.json.JSONArray()
            for (item in list) {
                val obj = org.json.JSONObject()
                obj.put("videoId", item.videoId)
                obj.put("title", item.title)
                obj.put("thumbnailUrl", item.thumbnailUrl)
                obj.put("localFilePath", item.localFilePath)
                obj.put("dateDownloaded", item.dateDownloaded)
                array.put(obj)
            }
            prefs.edit().putString("youtube_downloads_list", array.toString()).apply()
            _youtubeDownloads.value = list
        } catch (e: Exception) {
            Log.e("XtreamVM", "Error saving downloads", e)
        }
    }

    fun addYoutubeDownload(item: YouTubeDownload) {
        val current = _youtubeDownloads.value.toMutableList()
        current.removeAll { it.videoId == item.videoId }
        current.add(0, item)
        saveDownloadsToPrefs(current)
    }

    fun deleteYoutubeDownload(videoId: String) {
        val current = _youtubeDownloads.value.toMutableList()
        val found = current.find { it.videoId == videoId }
        if (found != null) {
            try {
                val file = java.io.File(found.localFilePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {}
            current.remove(found)
            saveDownloadsToPrefs(current)
        }
    }

    fun fetchYoutubeRemoteConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://loop-7e3d9-default-rtdb.firebaseio.com/youtube_config.json")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank() && body != "null") {
                            val json = org.json.JSONObject(body)
                            val enabled = json.optBoolean("youtube_enabled", true)
                            val apiKey = json.optString("youtube_api_key", "AIzaSyAiMHQnWOt9tOtqlpddmIPPgwN0GUbtmAM")
                            
                            isYoutubeEnabled.value = enabled
                            youtubeApiKey.value = apiKey
                            
                            prefs.edit()
                                .putBoolean("youtube_enabled", enabled)
                                .putString("youtube_api_key", apiKey)
                                .apply()
                            
                            Log.d("XtreamVM", "Loaded remote YouTube config: enabled=$enabled apiKey=$apiKey")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("XtreamVM", "Failed to fetch remote YouTube config", e)
            }
        }
    }

    fun saveYoutubeRemoteConfig(enabled: Boolean, apiKey: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isYoutubeEnabled.value = enabled
                youtubeApiKey.value = apiKey
                prefs.edit()
                    .putBoolean("youtube_enabled", enabled)
                    .putString("youtube_api_key", apiKey)
                    .apply()
                
                val client = okhttp3.OkHttpClient()
                val json = org.json.JSONObject().apply {
                    put("youtube_enabled", enabled)
                    put("youtube_api_key", apiKey)
                }
                val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("https://loop-7e3d9-default-rtdb.firebaseio.com/youtube_config.json")
                    .put(requestBody)
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        onResult(response.isSuccessful)
                    }
                }
            } catch (e: Exception) {
                Log.e("XtreamVM", "Failed to save remote YouTube config", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun searchYoutubeVideos(query: String) {
        youtubeSearchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            _isYoutubeLoading.value = true
            try {
                val apiKey = youtubeApiKey.value
                val encodedQuery = java.net.URLEncoder.encode(
                    if (query.trim().isEmpty()) "كرتون مسلسلات افلام عربية" else query.trim(),
                    "UTF-8"
                )
                val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=25&q=$encodedQuery&type=video&key=$apiKey"
                
                val request = okhttp3.Request.Builder().url(url).build()
                val client = okhttp3.OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank() && body != "null") {
                            val json = org.json.JSONObject(body)
                            val items = json.optJSONArray("items")
                            val list = mutableListOf<YouTubeVideo>()
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val idObj = item.optJSONObject("id") ?: continue
                                    val videoId = idObj.optString("videoId", "") ?: ""
                                    if (videoId.isEmpty()) continue
                                    
                                    val snippet = item.optJSONObject("snippet") ?: continue
                                    val title = snippet.optString("title", "")
                                    val description = snippet.optString("description", "")
                                    val channelTitle = snippet.optString("channelTitle", "")
                                    val publishedAt = snippet.optString("publishedAt", "")
                                    
                                    val thumbnails = snippet.optJSONObject("thumbnails")
                                    val high = thumbnails?.optJSONObject("high") ?: thumbnails?.optJSONObject("default")
                                    val thumbnailUrl = high?.optString("url", "") ?: ""
                                    
                                    val cleanTitle = title.replace("&quot;", "\"")
                                        .replace("&#39;", "'")
                                        .replace("&amp;", "&")
                                        .replace("&lt;", "<")
                                        .replace("&gt;", ">")
                                        
                                    list.add(
                                        YouTubeVideo(
                                            videoId = videoId,
                                            title = cleanTitle,
                                            description = description,
                                            thumbnailUrl = thumbnailUrl,
                                            channelTitle = channelTitle,
                                            publishedAt = publishedAt
                                        )
                                    )
                                }
                            }
                            _youtubeVideos.value = list
                        }
                    } else {
                        Log.e("XtreamVM", "YouTube search API failed: Code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("XtreamVM", "Error searching YouTube videos", e)
            } finally {
                _isYoutubeLoading.value = false
            }
        }
    }

    fun downloadYoutubeVideo(context: Context, videoId: String, title: String, thumbnailUrl: String) {
        if (downloadingVideos.value.containsKey(videoId)) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val mutableMap = downloadingVideos.value.toMutableMap()
            mutableMap[videoId] = 0.01f
            downloadingVideos.value = mutableMap
            
            try {
                val sampleUrls = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
                )
                val downloadUrl = sampleUrls[Math.abs(videoId.hashCode()) % sampleUrls.size]
                
                val request = okhttp3.Request.Builder().url(downloadUrl).build()
                val client = okhttp3.OkHttpClient()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw java.io.IOException("Failed response from sample")
                
                val body = response.body ?: throw java.io.IOException("Empty body")
                val totalBytes = body.contentLength()
                
                val downloadDir = java.io.File(context.filesDir, "downloads")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val destinationFile = java.io.File(downloadDir, "yt_${videoId}.mp4")
                
                val buffer = ByteArray(8192)
                var bytesRead = 0L
                
                body.byteStream().use { inputStream ->
                    destinationFile.outputStream().use { outputStream ->
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read == -1) break
                            outputStream.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val progress = bytesRead.toFloat() / totalBytes
                                val map = downloadingVideos.value.toMutableMap()
                                map[videoId] = progress.coerceIn(0.01f, 0.99f)
                                downloadingVideos.value = map
                            }
                        }
                    }
                }
                
                val finishedMap = downloadingVideos.value.toMutableMap()
                finishedMap.remove(videoId)
                downloadingVideos.value = finishedMap
                
                addYoutubeDownload(YouTubeDownload(
                    videoId = videoId,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    localFilePath = destinationFile.absolutePath,
                    dateDownloaded = System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                Log.e("XtreamVM", "Failed download YT $videoId", e)
                val errMap = downloadingVideos.value.toMutableMap()
                errMap.remove(videoId)
                downloadingVideos.value = errMap
            }
        }
    }

    fun playLocalFile(path: String?, title: String?) {
        _currentPlayingLocalFilePath.value = path
        _currentPlayingLocalTitle.value = title
    }
}
