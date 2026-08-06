package com.github.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.viewer.ui.theme.GitHubRepoViewerTheme
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GitHubRepoViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GitHubApp()
                }
            }
        }
    }
}

// --- Data Models ---
data class GitHubUser(
    val login: String,
    val avatar_url: String,
    val public_repos: Int,
    val followers: Int,
    val following: Int
)

data class GitHubRepo(
    val name: String,
    val description: String?,
    val stargazers_count: Int,
    val language: String?,
    val html_url: String
)

// --- API Interface ---
interface GitHubApi {
    @GET("users/{username}")
    suspend fun getUser(@Path("username") username: String): GitHubUser

    @GET("users/{username}/repos")
    suspend fun getRepos(@Path("username") username: String): List<GitHubRepo>
}

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val api = retrofit.create(GitHubApi::class.java)

// --- UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubApp() {
    var username by remember { mutableStateOf("") }
    var user by remember { mutableStateOf<GitHubUser?>(null) }
    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GitHub Repo Viewer",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF58A6FF),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("اسم المستخدم على GitHub") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (username.isNotBlank()) {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            user = api.getUser(username)
                            repos = api.getRepos(username)
                        } catch (e: Exception) {
                            error = "خطأ: ${e.message}"
                            user = null
                            repos = emptyList()
                        }
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
        ) {
            Text("بحث", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator(color = Color(0xFF58A6FF))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        user?.let { u ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = u.login,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("مستودعات", u.public_repos)
                        StatItem("متابعون", u.followers)
                        StatItem("يتابع", u.following)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "المستودعات (${repos.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(repos) { repo ->
                    RepoCard(repo)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF58A6FF)
        )
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun RepoCard(repo: GitHubRepo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = repo.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF58A6FF)
            )
            repo.description?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                repo.language?.let {
                    Text(
                        text = "● $it",
                        fontSize = 12.sp,
                        color = Color(0xFFFFD700)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = "⭐ ${repo.stargazers_count}",
                    fontSize = 12.sp,
                    color = Color.Yellow
                )
            }
        }
    }
}
