package com.mirzagames.dictionary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DictionaryScreen()
            }
        }
    }
}

data class Meaning(val partOfSpeech: String, val definitions: List<String>, val example: String?)
data class WordResult(val word: String, val phonetic: String, val meanings: List<Meaning>)

// Returns null if the word is not found
fun fetchWord(word: String): WordResult? {
    val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(word, "UTF-8"))
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 10000
    conn.readTimeout = 10000
    try {
        if (conn.responseCode != 200) return null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val entry = JSONArray(body).getJSONObject(0)
        val meanings = mutableListOf<Meaning>()
        val arr = entry.getJSONArray("meanings")
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val defs = m.getJSONArray("definitions")
            val list = mutableListOf<String>()
            var example: String? = null
            for (j in 0 until defs.length()) {
                val d = defs.getJSONObject(j)
                list.add(d.getString("definition"))
                if (example == null && d.has("example")) example = d.getString("example")
            }
            meanings.add(Meaning(m.getString("partOfSpeech"), list, example))
        }
        return WordResult(
            entry.getString("word"),
            entry.optString("phonetic", ""),
            meanings
        )
    } finally {
        conn.disconnect()
    }
}

@Composable
fun DictionaryScreen() {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WordResult?>(null) }
    var message by remember { mutableStateOf("Type a word and press Search.") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        val w = query.trim()
        if (w.isEmpty()) return
        loading = true
        result = null
        message = ""
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { fetchWord(w) }
                if (r == null) message = "No meaning found for \"$w\"."
                else result = r
            } catch (e: Exception) {
                message = "Could not look up the word. Check your internet connection."
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Dictionary", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Enter a word") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { search() }) { Text("Search") }
        }

        Spacer(Modifier.height(16.dp))
        if (loading) CircularProgressIndicator()
        if (message.isNotEmpty()) Text(message)

        result?.let { r ->
            LazyColumn {
                item {
                    Text(r.word, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (r.phonetic.isNotEmpty()) Text(r.phonetic, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                }
                items(r.meanings) { m ->
                    Column(Modifier.padding(bottom = 16.dp)) {
                        Text(m.partOfSpeech, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        m.definitions.forEachIndexed { i, d ->
                            Text("${i + 1}. $d", modifier = Modifier.padding(top = 4.dp))
                        }
                        m.example?.let {
                            Text("Example: \"$it\"", modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
