package com.example.goldcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8F9FA)
                ) {
                    GoldCalculatorScreen()
                }
            }
        }
    }
}

data class GoldUiState(
    val pricePerGram: Double? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class GoldViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GoldUiState())
    val uiState: StateFlow<GoldUiState> = _uiState

    init {
        fetchPrice()
    }

    fun fetchPrice() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val price = withContext(Dispatchers.IO) {
                    val doc = Jsoup.connect("https://www.profinance.ru/commodities/gold")
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(12000)
                        .get()

                    var targetRow = doc.select("tr:has(td:contains(XAURUB_FX))").first()
                    if (targetRow == null) {
                        val rows = doc.select("tr")
                        for (r in rows) {
                            if (r.text().contains("XAURUB_FX")) {
                                targetRow = r
                                break
                            }
                        }
                    }

                    if (targetRow == null) {
                        throw Exception("Инструмент XAURUB_FX не найден на profinance.ru")
                    }

                    val tds = targetRow.select("td")
                    val rawText = if (tds.size > 3) tds[3].text() else tds.last()?.text() ?: ""
                    val cleaned = rawText.replace(" ", "").replace(" ", "").replace(",", ".")
                    cleaned.toDouble()
                }
                _uiState.value = GoldUiState(pricePerGram = price, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = GoldUiState(
                    isLoading = false,
                    errorMessage = "Ошибка обновления: ${e.localizedMessage ?: "Сбой соединения"}"
                )
            }
        }
    }
}

@Composable
fun GoldCalculatorScreen(viewModel: GoldViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var gramsInput by remember { mutableStateOf("1") }

    val grams = gramsInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val totalCost = (state.pricePerGram ?: 0.0) * grams
    val df = DecimalFormat("#,##0.00")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Калькулятор золота",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        Text(
            text = "Курс ProFinance (XAURUB_FX)",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Текущая цена за 1 грамм",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFFD4AF37)
                    )
                } else if (state.pricePerGram != null) {
                    Text(
                        text = "${df.format(state.pricePerGram)} ₽",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFB45309)
                    )
                } else {
                    Text(
                        text = state.errorMessage ?: "Курс не загружен",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = gramsInput,
            onValueChange = { gramsInput = it },
            label = { Text("Вес золота (граммы)") },
            placeholder = { Text("Например: 10 или 31.1") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ИТОГО К ОПЛАТЕ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF92400E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${df.format(totalCost)} ₽",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF78350F)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { viewModel.fetchPrice() },
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
        ) {
            Text(
                text = if (state.isLoading) "Обновление котировки..." else "Обновить курс ProFinance",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
