package com.maxcoder.lastmlkitscanner.presentation.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxcoder.lastmlkitscanner.domain.model.CardData
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

data class ResultScreenNavArgs(val card: CardData)

@Destination(navArgsDelegate = ResultScreenNavArgs::class)
@Composable
fun ResultScreen(
    navigator: DestinationsNavigator,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ResultContract.Effect.NavigateBack -> navigator.navigateUp()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F23))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Card scanned successfully",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            CardVisual(card = state.card)

            Button(
                onClick = { viewModel.onEvent(ResultContract.Event.Confirm) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { viewModel.onEvent(ResultContract.Event.Rescan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f))
            ) {
                Text("Rescan", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun CardVisual(card: CardData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF283593))
                )
            )
            .padding(24.dp)
    ) {
        // Chip placeholder
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFD600).copy(alpha = 0.85f))
                .align(Alignment.TopStart)
        )

        // Formatted card number
        Text(
            text = card.number.chunked(4).joinToString("  "),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "EXPIRES",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = card.expiry.ifEmpty { "MM/YY" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (card.holderName.isNotEmpty()) {
                Text(
                    text = card.holderName.uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}