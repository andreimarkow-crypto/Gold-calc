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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DateFormat
import java.util.Date

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
    val errorMessage: String? = null,
    val lastUpdated: Long? = null
)

class GoldViewModel(private val repository: GoldPriceRepository = GoldPriceRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(GoldUiState())
    val uiState: StateFlow<GoldUiState> = _uiState

    init {
        fetchPrice()
    }

    fun fetchPrice() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val price = repository.fetchPrice()
                _uiState.value = GoldUiState(pricePerGram = price, lastUpdated = System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep the last successful quote and its timestamp when an update fails.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = priceErrorMessage(error)
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
                    text = "Последняя загруженная цена за 1 грамм",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFFD4AF37)
                    )
                }
                if (state.pricePerGram != null) {
                    Text(
                        text = "${df.format(state.pricePerGram)} ₽",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFB45309)
                    )
                    state.lastUpdated?.let { timestamp ->
                        Text(
                            text = "Загружено: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestamp))}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = if (state.pricePerGram != null) "$message Показан последний загруженный курс." else message,
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
                    text = if (state.pricePerGram != null) "${df.format(totalCost)} ₽" else "—",
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
