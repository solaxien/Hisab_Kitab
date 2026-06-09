package com.amg.hisabkitab.screens

import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun HomeScreen (
    name : String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Hello $name",
        modifier = modifier.background(color = Color.Blue)
    )
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    HomeScreen(
        name = "Amogh"
    )
}