package com.utilityhub.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utilityhub.app.tools.ToolRegistry
import com.utilityhub.app.ui.components.AllowanceCard
import com.utilityhub.app.ui.components.CategoryCard
import com.utilityhub.app.ui.components.EmptyState
import com.utilityhub.app.ui.components.GlassCard
import com.utilityhub.app.ui.components.IconBadge
import com.utilityhub.app.ui.components.SectionTitle
import com.utilityhub.app.ui.components.ToolListItem
import com.utilityhub.app.ui.toolIcon
import com.utilityhub.app.ui.viewmodel.MainViewModel
import com.utilityhub.logic.catalog.ToolCatalog
import com.utilityhub.logic.catalog.ToolCategory
import com.utilityhub.logic.catalog.ToolSearch

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onChange, singleLine = true,
        placeholder = { Text("Search tools (e.g. resize, pdf, hash)") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun HomeScreen(vm: MainViewModel, onOpenTool: (String) -> Unit, onOpenCategory: (ToolCategory) -> Unit, onPaywall: () -> Unit, onSettings: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.entitlement.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { ToolSearch.search(ToolCatalog.all, query) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("UtilityHub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Everyday file tools. Private and offline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
            }
        }
        item { SearchField(query) { query = it } }

        if (query.isNotBlank()) {
            if (results.isEmpty()) item { EmptyState("No tools found", "Try a different word, like \"pdf\" or \"compress\".") }
            items(results, key = { it.id }) { meta ->
                ToolListItem(meta, ToolRegistry.isAvailable(meta), meta.id in settings.favorites, { onOpenTool(meta.id) }, { vm.toggleFavorite(meta.id) })
            }
            return@LazyColumn
        }

        item { AllowanceCard(snapshot, onPaywall) }

        item { SectionTitle("Favorites") }
        val favorites = ToolCatalog.all.filter { it.id in settings.favorites && ToolRegistry.isAvailable(it) }
        if (favorites.isEmpty()) {
            item { Text("Tap the star on any tool to pin it here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favorites, key = { it.id }) { meta ->
                        GlassCard(
                            onClick = { onOpenTool(meta.id) },
                            modifier = Modifier.width(148.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                IconBadge(toolIcon(meta.id, meta.category), size = 42.dp)
                                Spacer(Modifier.height(10.dp))
                                Text(meta.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Categories") }
        items(ToolCategory.entries.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category ->
                    val count = ToolCatalog.byCategory(category).count { ToolRegistry.isAvailable(it) }
                    CategoryCard(category, count, { onOpenCategory(category) }, Modifier.weight(1f))
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }

        if (history.isNotEmpty()) {
            item { SectionTitle("Recent") }
            items(history.take(3)) { entry ->
                val meta = ToolCatalog.byId(entry.toolId)
                if (meta != null && ToolRegistry.isAvailable(meta)) {
                    ToolListItem(meta, true, meta.id in settings.favorites, { onOpenTool(meta.id) }, { vm.toggleFavorite(meta.id) })
                }
            }
        }
    }
}

@Composable
fun ExploreScreen(vm: MainViewModel, onOpenTool: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val category by vm.exploreCategory.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, category) {
        ToolSearch.search(ToolCatalog.all, query).filter { category == null || it.category == category }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Explore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { SearchField(query) { query = it } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == null, onClick = { vm.selectCategory(null) }, label = { Text("All") }, shape = RoundedCornerShape(12.dp)) }
                items(ToolCategory.entries) { c -> FilterChip(selected = category == c, onClick = { vm.selectCategory(c) }, label = { Text(c.title) }, shape = RoundedCornerShape(12.dp)) }
            }
        }
        if (filtered.isEmpty()) item { EmptyState("No tools found", "Try a different word or category.") }
        val grouped = filtered.groupBy { it.category }
        ToolCategory.entries.forEach { c ->
            val list = grouped[c] ?: return@forEach
            val ordered = list.sortedBy { if (ToolRegistry.isAvailable(it)) 0 else 1 }
            if (category == null) item(key = "h_${c.name}") { SectionTitle(c.title, Modifier.padding(top = 8.dp)) }
            items(ordered, key = { it.id }) { meta ->
                ToolListItem(meta, ToolRegistry.isAvailable(meta), meta.id in settings.favorites, { onOpenTool(meta.id) }, { vm.toggleFavorite(meta.id) })
            }
        }
    }
}
