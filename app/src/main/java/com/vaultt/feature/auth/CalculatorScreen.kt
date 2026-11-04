package com.vaultt.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@Composable
fun CalculatorScreen(
    onUnlock: (String) -> Boolean,
    onFailedAttempt: () -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val formatter = remember { DecimalFormat("#,###.########") }

    fun onDigit(d: String) {
        if (display == "0" || resetOnNextDigit) {
            display = d
            resetOnNextDigit = false
        } else if (display.length < 12) {
            display += d
        }
    }

    fun onDot() {
        if (resetOnNextDigit) {
            display = "0."
            resetOnNextDigit = false
        } else if (!display.contains(".")) {
            display += "."
        }
    }

    fun onBackspace() {
        if (!resetOnNextDigit) {
            display = if (display.length > 1) display.dropLast(1) else "0"
        }
    }

    fun onClear() {
        display = "0"
        expression = ""
        operand1 = null
        pendingOp = null
        resetOnNextDigit = false
    }

    fun onOperator(op: String) {
        val currentVal = display.toDoubleOrNull() ?: 0.0
        if (operand1 == null) {
            operand1 = currentVal
        } else if (pendingOp != null && !resetOnNextDigit) {
            val result = when (pendingOp) {
                "+" -> operand1!! + currentVal
                "-" -> operand1!! - currentVal
                "×" -> operand1!! * currentVal
                "÷" -> if (currentVal != 0.0) operand1!! / currentVal else Double.NaN
                else -> currentVal
            }
            operand1 = result
            display = if (result.isNaN()) "Error" else formatter.format(result)
        }
        pendingOp = op
        expression = "${formatter.format(operand1)} $op"
        resetOnNextDigit = true
    }

    fun onPercent() {
        val current = display.toDoubleOrNull() ?: 0.0
        val res = current / 100.0
        display = formatter.format(res)
    }

    fun onNegate() {
        if (display != "0" && display != "Error") {
            display = if (display.startsWith("-")) display.drop(1) else "-$display"
        }
    }

    fun onEquals() {
        // First check if the raw entered digits or full display matches the secret Vault PIN!
        val secretCandidate = display.replace(",", "").replace(" ", "").trim()
        val unlocked = onUnlock(secretCandidate)
        if (unlocked) {
            onClear()
            return
        }

        // If not unlocked and we have an active calculation, evaluate math normally
        val currentVal = display.toDoubleOrNull()
        if (operand1 != null && pendingOp != null && currentVal != null) {
            val result = when (pendingOp) {
                "+" -> operand1!! + currentVal
                "-" -> operand1!! - currentVal
                "×" -> operand1!! * currentVal
                "÷" -> if (currentVal != 0.0) operand1!! / currentVal else Double.NaN
                else -> currentVal
            }
            expression = "${formatter.format(operand1)} $pendingOp ${formatter.format(currentVal)} ="
            display = if (result.isNaN()) "Error" else formatter.format(result)
            operand1 = null
            pendingOp = null
            resetOnNextDigit = true
        } else {
            // Normal number entered followed by =: check failed attempt
            onFailedAttempt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF17171C))
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Display area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = expression,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray,
                maxLines = 1,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = display,
                fontSize = if (display.length > 8) 42.sp else 54.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons Grid
        val buttonSpacing = 12.dp

        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcButton("C", Color(0xFFA5A5A5), Color.Black, Modifier.weight(1f)) { onClear() }
            CalcButton("±", Color(0xFFA5A5A5), Color.Black, Modifier.weight(1f)) { onNegate() }
            CalcButton("%", Color(0xFFA5A5A5), Color.Black, Modifier.weight(1f)) { onPercent() }
            CalcButton("÷", Color(0xFFFF9F0A), Color.White, Modifier.weight(1f)) { onOperator("÷") }
        }

        Spacer(modifier = Modifier.height(buttonSpacing))

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcButton("7", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("7") }
            CalcButton("8", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("8") }
            CalcButton("9", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("9") }
            CalcButton("×", Color(0xFFFF9F0A), Color.White, Modifier.weight(1f)) { onOperator("×") }
        }

        Spacer(modifier = Modifier.height(buttonSpacing))

        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcButton("4", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("4") }
            CalcButton("5", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("5") }
            CalcButton("6", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("6") }
            CalcButton("-", Color(0xFFFF9F0A), Color.White, Modifier.weight(1f)) { onOperator("-") }
        }

        Spacer(modifier = Modifier.height(buttonSpacing))

        // Row 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcButton("1", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("1") }
            CalcButton("2", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("2") }
            CalcButton("3", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("3") }
            CalcButton("+", Color(0xFFFF9F0A), Color.White, Modifier.weight(1f)) { onOperator("+") }
        }

        Spacer(modifier = Modifier.height(buttonSpacing))

        // Row 5
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcButton("0", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDigit("0") }
            CalcButton(".", Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onDot() }
            CalcIconButton(Icons.AutoMirrored.Filled.Backspace, Color(0xFF2E2F38), Color.White, Modifier.weight(1f)) { onBackspace() }
            CalcButton("=", Color(0xFFFF9F0A), Color.White, Modifier.weight(1f)) { onEquals() }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CalcButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun CalcIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
    }
}

