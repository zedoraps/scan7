package net.moesli.scan7.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.RowScore
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.theme.Scan7Theme

@Composable
internal fun ScoreBreakdown(score: RowScore) {
    if (score.bust) {
        M3Card(
            colors = CardDefaults.cardColors(
                containerColor = Scan7Theme.colors.bust,
                contentColor = Scan7Theme.colors.onBust,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠", style = MaterialTheme.typography.titleMedium)
                Text(
                    "BUST · duplicated: ${score.duplicatedNumbers.joinToString(", ")}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    M3Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BreakdownLine("Number sum", score.numberSum.toString())
            BreakdownLine("x2", if (score.timesTwo) "yes" else "no")
            BreakdownLine("Plus sum", score.plusSum.toString())
            BreakdownLine("Flip 7 bonus", score.flip7Bonus.toString())
            BreakdownLine("Total", score.total.toString(), bold = true)
        }
    }
}

@Composable
private fun BreakdownLine(name: String, value: String, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(name, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(value, fontWeight = weight)
    }
}
