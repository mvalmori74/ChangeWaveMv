package com.changewave.ombraparking.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.changewave.ombraparking.core.osm.Place

/**
 * Ricerca di un luogo per nome, per guardare le ombre di una zona in cui non si è.
 *
 * La ricerca parte solo alla conferma, non a ogni lettera: il geocoder di OpenStreetMap è
 * un servizio gratuito con regole d'uso strette, e cercare mentre si digita significherebbe
 * una richiesta per carattere.
 */
@Composable
fun PlaceSearchBar(
    results: List<Place>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onDismissResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Cerca una via o una piazza") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    isSearching -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )

                    query.isNotEmpty() -> IconButton(
                        onClick = {
                            query = ""
                            onDismissResults()
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancella la ricerca")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    onSearch(query)
                }
            ),
        )

        if (results.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                tonalElevation = 3.dp,
            ) {
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(results) { place ->
                        PlaceRow(
                            place = place,
                            onClick = {
                                keyboard?.hide()
                                query = place.shortName
                                onPlaceSelected(place)
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = place.shortName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = place.fullName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Riga che ricorda che si stanno guardando le ombre di un'altra zona. */
@Composable
fun ExplorationBanner(
    name: String?,
    onBackToMyPosition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🔎  " + (name ?: "Zona scelta sulla mappa"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onBackToMyPosition) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Torna alla mia posizione",
                    tint = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}
