package pt.hexa.empresarial.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.hexa.empresarial.R

/**
 * Typographic logo: "HEXA" in metallic gold, "EMPRESARIAL EDITION" smaller in silver with wide
 * tracking. No 3D effects, glows or outlines.
 */
@Composable
fun HexaLogo(modifier: Modifier = Modifier, size: TextUnit = 28.sp) {
    val colors = HexaTheme.colors
    val description = stringResource(R.string.brand_full_name)
    Column(modifier.clearAndSetSemantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.brand_word), style = HexaType.brand.merge(TextStyle(fontSize = size, brush = colors.metallic())))
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.brand_edition),
            style = HexaType.edition.merge(TextStyle(fontSize = size * 0.32f, brush = colors.silver())),
        )
    }
}
