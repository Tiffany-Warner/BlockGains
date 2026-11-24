package com.tiffles.blockgains.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tiffles.blockgains.R

/** Tax-year dropdown for the top app bar. Lists every year with activity (ui-guidelines → Summary). */
@Composable
fun YearPicker(year: Int, years: List<Int>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.summary_year_picker_cd, year)
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            enabled = years.size > 1,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.summary_year, year))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.summary_year, option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
