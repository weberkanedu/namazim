package com.namazim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tasarım jetonları: bütün ekranlar bu ölçekten beslenir.
 * Yeni bir ekran yazarken elle 13.sp / 18.dp yazmak yerine buradan seç.
 */
object Type {
    val display = 44.sp   // sayfa başlığı
    val title = 34.sp     // büyük sayı / ekran içi başlık
    val h2 = 22.sp        // kart başlığı
    val num = 28.sp       // kart içi sayı (istatistik, sayaç)
    val body = 15.sp      // ana metin
    val body2 = 13.sp     // ikincil metin
    val label = 12.sp     // etiket, açıklama (en küçük)
    val micro = 11.sp     // yalnız grafik ekseni gibi zorunlu yerlerde
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val s = 12.dp
    val m = 18.dp
    val l = 22.dp
}

/** Hiçbir dokunma hedefi bundan küçük olmamalı. */
val TouchMin = 48.dp

/**
 * Kart: uygulamanın tek yüzey dili. Gölge + cam kenar + üstten hafif parıltı.
 * Tıklanabilir olacaksa [onClick] ver; erişilebilirlik etiketi [label] ile geçer.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.l)
    var m = modifier
        .shadow(14.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .45f))
        .clip(shape)
        .background(Pal.Card)
        .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .05f), Color.White.copy(alpha = .012f), Color.Transparent),
            ),
        )
        .border(1.dp, Color.White.copy(alpha = .055f), shape)
    if (label != null) m = m.semantics { contentDescription = label }
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(horizontal = Space.l, vertical = Space.l), content = content)
}

/** Kart içi küçük bölüm başlığı: "HATIM TAKİBİ", "BUGÜNE KADAR"… */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, Modifier.weight(1f), color = Pal.Muted, fontSize = Type.label,
            letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
        )
        trailing?.invoke()
    }
}

/**
 * Boş durum: her ekran aynı dili konuşsun.
 * Tek cümle + tek eylem; "—" gibi yarım kalan yer tutucular yerine bunu kullan.
 */
@Composable
fun EmptyState(title: String, text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Pal.Gold.copy(alpha = .55f)))
        Spacer(Modifier.height(Space.m))
        Text(title, color = Pal.Text, fontSize = Type.body, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(text, color = Pal.Muted, fontSize = Type.body2, lineHeight = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            Box(
                Modifier.heightIn(min = TouchMin).clip(CircleShape).border(1.dp, Pal.Gold.copy(alpha = .55f), CircleShape)
                    .clickable(onClick = onAction).padding(horizontal = Space.l),
                contentAlignment = Alignment.Center,
            ) { Text(actionLabel, color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Metin yerine geçen ikon: ekran okuyucuya anlamlı tek etiket verir. */
fun Modifier.decorative() = this.clearAndSetSemantics { }

/** Küçük yuvarlak buton (48 dp hedef, 36 dp görünür daire). */
@Composable
fun RoundAction(
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(TouchMin).semantics { contentDescription = label }
            .clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, Pal.Border, CircleShape), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                icon, label, Modifier.size(18.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (enabled) Pal.Text else Pal.Muted),
            )
        }
    }
}
