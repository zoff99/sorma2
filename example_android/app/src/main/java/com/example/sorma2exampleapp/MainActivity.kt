package com.example.sorma2exampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.sorma2exampleapp.ui.theme.Sorma2ExampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow

val messages = MutableStateFlow("running tests ...")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Sorma2ExampleAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting()
                }
            }
        }
        val result = sorma2example().testme(this)
        messages.value = messages.value + result
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    val txt by messages.collectAsState()
    Text(
        text = "" + txt,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp, lineHeight = 25.0.sp)
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Sorma2ExampleAppTheme {
        Greeting()
    }
}