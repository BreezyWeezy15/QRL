package com.app.lockcomposeLock.screens

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.google.firebase.database.FirebaseDatabase

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildScreen(navController: NavController) {
    val context = LocalContext.current
    var profileType by remember { mutableStateOf("No Selected Profile") } // Default to "No Selected Profile"
    var installedApps by remember { mutableStateOf(listOf<InstalledApp>()) }
    val showQRCode = remember { mutableStateOf(false) }


    // Fetch Profile Type and Apps on Launch
    LaunchedEffect(Unit) {
        // Fetch Profile Type from Firebase
        val firebaseDatabase = FirebaseDatabase.getInstance().getReference().child("Apps")
            .child(generateDeviceID(context))

        firebaseDatabase.child("type").get()
            .addOnSuccessListener { snapshot ->
                profileType = snapshot.getValue(String::class.java) ?: "No Selected Profile"
                installedApps = getAppsForProfile(context, profileType)  // Get apps for the selected profile
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to fetch profile", Toast.LENGTH_SHORT).show()
            }
    }



    // Main UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TopAppBar with QR Code Button
        TopAppBar(
            title = { Text(profileType, color = Color.Black) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.LightGray)
        )

        // App Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(installedApps) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(8.dp), // Elevation for shadow effect
                    shape = MaterialTheme.shapes.medium // Rounded corners
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp) // Padding inside the card
                    ) {
                        // App Icon
                        Image(
                            painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
                            contentDescription = app.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape) // Make the icon circular
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), // Add a border to the icon
                            contentScale = ContentScale.Crop
                        )

                        // Spacing
                        Spacer(modifier = Modifier.height(8.dp))

                        // App Name
                        Text(
                            text = app.name,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface, // Use theme color for text
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
          }
        }
}

@SuppressLint("HardwareIds")
fun generateDeviceID(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

// 2. List of target apps for each profile type (Child, Pre-K, Teen)
// Updated list of target apps for each profile type
val profileApps = mapOf(
    "Child" to listOf(
        "com.google.android.youtube", // YouTube
        "com.android.vending",        // Play Store
        "com.hilokal"                 // Hilokal
    ),
    "Teen" to listOf(
        "com.android.chrome",         // Chrome
        "com.facebook.katana",        // Facebook
        "com.instagram.android",      // Instagram
        "com.google.android.youtube"  // YouTube
    ),
    "Pre-K" to listOf(
        "com.android.chrome",         // Chrome
        "com.google.android.apps.youtube.kids", // YouTube Kids
        "com.facebook.katana"         // Facebook
    ),
    "No Selected Profile" to listOf() // Default empty list for no selected profile
)

// 3. Function to fetch installed apps for the selected profile
fun getAppsForProfile(context: Context, profile: String): List<InstalledApp> {
    val packageManager = context.packageManager
    val apps = mutableListOf<InstalledApp>()
    val targetPackages = profileApps[profile] ?: emptyList()

    val installedPackages = packageManager.getInstalledPackages(0)
    for (packageInfo in installedPackages) {
        // Match installed apps against the target packages
        if (targetPackages.any { it.equals(packageInfo.packageName, ignoreCase = true) }) {
            val appName = packageInfo.applicationInfo.loadLabel(packageManager).toString()
            val appIcon = packageInfo.applicationInfo.loadIcon(packageManager)
            apps.add(InstalledApp(appName, packageInfo.packageName, appIcon))
        }
    }

    // Log or notify if no apps were matched
    if (apps.isEmpty()) {
        Log.w("ChildScreen", "No matching apps found for profile: $profile")
    }
    return apps
}

