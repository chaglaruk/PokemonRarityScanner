package com.pokerarity.scanner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pokerarity.scanner.util.ocr.TextParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.text.SimpleDateFormat;
import java.util.Locale;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\n\u001a\u00020\u000bH\u0007J\b\u0010\f\u001a\u00020\u000bH\u0007J\b\u0010\r\u001a\u00020\u000bH\u0007R\u0016\u0010\u0003\u001a\n \u0005*\u0004\u0018\u00010\u00040\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/pokerarity/scanner/TextParserRegressionTest;", "", "()V", "context", "Landroid/content/Context;", "kotlin.jvm.PlatformType", "fmt", "Ljava/text/SimpleDateFormat;", "parser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "parseCpPrefersExplicitAnchor", "", "parseDateSupportsCompactBadgeTokens", "parseHpRejectsImpossiblePairs", "app_debugAndroidTest"})
public final class TextParserRegressionTest {
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.ocr.TextParser parser = null;
    @org.jetbrains.annotations.NotNull()
    private final java.text.SimpleDateFormat fmt = null;
    
    public TextParserRegressionTest() {
        super();
    }
    
    @org.junit.Test()
    public final void parseCpPrefersExplicitAnchor() {
    }
    
    @org.junit.Test()
    public final void parseHpRejectsImpossiblePairs() {
    }
    
    @org.junit.Test()
    public final void parseDateSupportsCompactBadgeTokens() {
    }
}