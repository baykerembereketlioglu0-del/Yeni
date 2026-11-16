package app.karala

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.karala.overlay.OverlayService
import app.karala.ui.*

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        setContent {
            var theme by remember { mutableIntStateOf(App.instance.prefs.theme) }
            KaralaTheme(theme) {
                KaralaApp(pendingRoute.value, { pendingRoute.value = null }) { theme = it; App.instance.prefs.theme = it }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        fun overlayPermissionIntent(ctx: Context) =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Bugün", Icons.Rounded.Home),
    Tab("notes", "Defter", Icons.Rounded.MenuBook),
    Tab("review", "Tekrar", Icons.Rounded.Replay),
    Tab("plan", "Plan", Icons.Rounded.EventNote),
    Tab("tools", "Araçlar", Icons.Rounded.Apps),
)

private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true; restoreState = true
}

@Composable
private fun KaralaApp(pendingRoute: String?, consumeRoute: () -> Unit, onTheme: (Int) -> Unit) {
    val nav = rememberNavController()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pen = rememberPenLauncher()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    // Bildirim izni (Android 13+): zamanlayıcı ve hatırlatmalar için ilk açılışta bir kez sorulur.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !App.instance.prefs.onboarded &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        App.instance.prefs.onboarded = true
    }

    LaunchedEffect(pendingRoute) {
        when (pendingRoute) {
            null -> return@LaunchedEffect
            "pen" -> pen.launch()
            "focus" -> nav.navigate("focus") { launchSingleTop = true }
            "plan" -> nav.tab("plan")
        }
        consumeRoute()
    }

    Scaffold(
        bottomBar = { if (TABS.any { it.route == current }) BottomBar(current) { nav.tab(it) } },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        NavHost(nav, "home", Modifier.padding(pad)) {
            composable("home") { HomeScreen(nav, pen) }
            composable("notes") { NotesScreen(nav) }
            composable("review") { ReviewScreen(nav) }
            composable("plan") { PlanScreen() }
            composable("tools") { ToolsScreen(nav) }
            composable("settings") { SettingsScreen({ nav.popBackStack() }, onTheme, pen) }
            composable("note/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                NoteEditorScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
            composable("mistake/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                MistakeDetailScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
            composable("mistakeReview") { MistakeReviewScreen { nav.popBackStack() } }
            composable("deck/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                DeckScreen(it.arguments!!.getLong("id"), nav)
            }
            composable("cards/{id}/{all}", listOf(navArgument("id") { type = NavType.LongType }, navArgument("all") { type = NavType.BoolType })) {
                CardReviewScreen(it.arguments!!.getLong("id"), it.arguments!!.getBoolean("all")) { nav.popBackStack() }
            }
            composable("focus") { FocusScreen { nav.popBackStack() } }
            composable("calc") { CalculatorScreen { nav.popBackStack() } }
            composable("formulas") { FormulasScreen { nav.popBackStack() } }
            composable("periodic") { PeriodicScreen { nav.popBackStack() } }
            composable("convert") { ConverterScreen { nav.popBackStack() } }
            composable("stats") { StatsScreen { nav.popBackStack() } }
            composable("exams") { ExamsScreen { nav.popBackStack() } }
        }
    }
    PenLauncherHost(pen)
}

/** Yüzen, hap biçimli alt menü: seçili sekme marka rengiyle genişler ve adını gösterir. */
@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp, top = 6.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TABS.forEach { t ->
                val selected = current == t.route
                val weight by androidx.compose.animation.core.animateFloatAsState(if (selected) 2.1f else 1f, label = "tab")
                val k = K
                androidx.compose.foundation.layout.Row(
                    Modifier.weight(weight).height(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .then(if (selected) Modifier.background(k.brand) else Modifier)
                        .selectable(selected, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(t.route) },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(t.icon, if (selected) null else t.label, tint = if (selected) k.onBrand else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                        Text(t.label, style = MaterialTheme.typography.labelLarge, color = k.onBrand, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
