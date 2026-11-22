package com.marco.mlfilm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Mono(
        text,
        color = if (on) C.Amber else C.Dim,
        size = 10.sp,
        modifier = Modifier
            .background(Color(0x66000000), shape)
            .border(1.dp, if (on) C.Amber else C.Line, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun RowScope.Slot(
    label: String,
    name: String,
    active: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    glyph: @Composable (Modifier) -> Unit,
) {
    Column(
        Modifier.width(98.dp).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Mono(label, size = 10.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            color = if (active || accent) C.Amber else C.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        glyph(Modifier.size(44.dp))
    }
}
