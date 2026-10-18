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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.RowScore
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.theme.Scan7Theme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreTesterScreen(onBack: () -> Unit, modifier: Modifier = Modifier, initialRow: List<Card> = emptyList()) {
    var row by rememberSaveable { mutableStateOf(initialRow) }
    DebugScaffold(title = "Score tester", onBack = onBack, modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Row (${row.size}) – tap a card to remove",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                OutlinedButton(onClick = { row = emptyList() }, enabled = row.isNotEmpty()) { Text("Clear") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.isEmpty()) Text("(empty)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.forEachIndexed { index, card ->
                    InputChip(
                        selected = false,
                        onClick = { row = row.filterIndexed { i, _ -> i != index } },
                        label = { Text(card.label) },
                    )
                }
            }
            ScoreBreakdown(scoreRow(row))
            Text("Add card", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Card.entries.forEach { card ->
                    FilledTonalButton(
                        onClick = { row = row + card },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text(card.label) }
                }
            }
        }
    }
}

@Composable
private fun ScoreBreakdown(score: RowScore) {
    if (score.bust) {
        M3Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "BUST – duplicated: ${score.duplicatedNumbers.joinToString(", ")}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(12.dp),
            )
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

@Preview(showBackground = true)
@Composable
fun ScoreTesterScreenPreview() {
    Scan7Theme {
        ScoreTesterScreen(onBack = {}, initialRow = listOf(Card.N3, Card.N7, Card.N7, Card.TIMES_2))
    }
}
