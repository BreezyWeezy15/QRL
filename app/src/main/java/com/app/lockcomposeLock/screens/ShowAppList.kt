import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.app.lockcomposeLock.R
import com.app.lockcomposeLock.services.AppLockService
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ShowAppList() {
    val context = LocalContext.current.applicationContext as Application
    val selectedApps = remember { mutableStateListOf<InstalledApps>() }
    val isLoading = remember { mutableStateOf(true) }

    // Start the AppLockService
    LaunchedEffect(Unit) {
        val serviceIntent = Intent(context, AppLockService::class.java)
        context.startService(serviceIntent)
    }

    // Fetch apps from Firebase
    LaunchedEffect(Unit) {
        fetchAppsFromFirebase(context) { apps, type ->
            selectedApps.clear()
            selectedApps.addAll(apps)
            isLoading.value = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Lock Apps", color = Color.Black)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.LightGray
                )
            )
        }
    ) { innerPadding ->
        if (isLoading.value) {
            // Show loading indicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.Gray
                )
            }
        } else if (selectedApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.no_device),
                    contentDescription = "No Devices",
                    modifier = Modifier.size(70.dp) 
                )
            }
        } else {
            // Show the list of apps
            LazyColumn(
                modifier = Modifier.padding(innerPadding)
            ) {
                items(selectedApps) { app ->
                    AppListItem(
                        app = app
                    )
                }
            }
        }
    }
}


@Composable
fun AppListItem(app: InstalledApps) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                if (app.packageName == "com.android.chrome") {
                    checkForAppUpdate(context)
                }
            },
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = app.icon!!.toBitmap().asImageBitmap(),
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


fun checkForAppUpdate(context: Context) {
    val appUpdateManager = AppUpdateManagerFactory.create(context)
    val appUpdateInfoTask: com.google.android.play.core.tasks.Task<AppUpdateInfo> = appUpdateManager.appUpdateInfo

    appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
        if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            Toast.makeText(context, "Update available for Chrome!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Chrome is up-to-date.", Toast.LENGTH_LONG).show()
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Failed to check for updates.", Toast.LENGTH_LONG).show()
    }
}

@SuppressLint("HardwareIds")
fun generateDeviceID(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

private fun fetchAppsFromFirebase(context: Context,onAppsFetched: (List<InstalledApps>, String) -> Unit) {

    val database = FirebaseDatabase.getInstance().reference
    val appsListRef = database.child("childApp").child(generateDeviceID(context = context))

    appsListRef.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            val updatedList = mutableListOf<InstalledApps>()

            for (childSnapshot in dataSnapshot.children) {
                val packageName = childSnapshot.child("package_name").getValue(String::class.java) ?: ""
                val name = childSnapshot.child("name").getValue(String::class.java) ?: ""
                val base64Icon = childSnapshot.child("icon").getValue(String::class.java) ?: ""
                val type = childSnapshot.child("profile_type").getValue(String::class.java) ?: "Child"
                val interval = childSnapshot.child("interval").getValue(String::class.java) ?: ""
                val pinCode = childSnapshot.child("pin_code").getValue(String::class.java) ?: ""

                if (type == "custom") {
                    val iconBitmap = base64ToBitmap(base64Icon)

                    val installedApp = InstalledApps(
                        packageName = packageName,
                        name = name,
                        icon = iconBitmap,
                        interval = interval,
                        pinCode = pinCode,
                        profileType = type
                    )
                    updatedList.add(installedApp)
                }
            }
            onAppsFetched(updatedList, "Custom")
        }

        override fun onCancelled(databaseError: DatabaseError) {
            Log.e("FirebaseError", "Error fetching data: ${databaseError.message}")
        }
    })
}

fun base64ToBitmap(base64String: String): Drawable? {
    return try {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.let {
            BitmapDrawable(Resources.getSystem(), it)
        }
    } catch (e: Exception) {
        null
    }
}

data class InstalledApps(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val interval: String = "",
    val pinCode: String = "",
    val profileType: String = ""
)