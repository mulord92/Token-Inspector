awk '
/^import / {
    if (!imported) {
        print "import androidx.compose.ui.platform.LocalClipboardManager"
        print "import androidx.compose.ui.text.AnnotatedString"
        print "import androidx.compose.material.icons.outlined.ContentCopy"
        print "import androidx.compose.material.icons.outlined.Check"
        print "import androidx.compose.material.icons.outlined.Refresh"
        print "import kotlinx.coroutines.delay"
        print "import kotlinx.coroutines.launch"
        print "import androidx.compose.ui.graphics.graphicsLayer"
        imported = 1
    }
}
{ print }
' app/src/main/java/com/example/MainActivity.kt > app/src/main/java/com/example/MainActivity.tmp
mv app/src/main/java/com/example/MainActivity.tmp app/src/main/java/com/example/MainActivity.kt
