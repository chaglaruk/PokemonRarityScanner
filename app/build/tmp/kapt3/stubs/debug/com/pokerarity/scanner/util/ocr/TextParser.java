package com.pokerarity.scanner.util.ocr;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.util.Date;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000p\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\b\n\u0000\n\u0002\u0010\"\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u000b\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\r\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0011\n\u0002\b\n\n\u0002\u0010\u0007\n\u0002\b\u0013\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u001e\n\u0002\b\t\u0018\u00002\u00020\u0001:\u0002[\\B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\r0\u00062\u0006\u0010\u0011\u001a\u00020\r2\u0006\u0010\u0012\u001a\u00020\rH\u0002J\u0016\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\r0\u00062\u0006\u0010\u0014\u001a\u00020\rH\u0002J\u0018\u0010\u0015\u001a\u00020\u00072\u0006\u0010\u0016\u001a\u00020\r2\u0006\u0010\u0017\u001a\u00020\rH\u0002J\u0010\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\rH\u0002J\u0016\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u001c0\u00062\u0006\u0010\u001a\u001a\u00020\rH\u0002J\u001e\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\r0\u00062\u0006\u0010\u001e\u001a\u00020\r2\b\b\u0002\u0010\u001f\u001a\u00020\u0007J\u0010\u0010 \u001a\u00020\u00192\u0006\u0010!\u001a\u00020\u0007H\u0002J\u0018\u0010\"\u001a\u00020\u00072\u0006\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020$H\u0002J\u0016\u0010&\u001a\b\u0012\u0004\u0012\u00020\r0\u00062\u0006\u0010\u0002\u001a\u00020\u0003H\u0002J\u0012\u0010\'\u001a\u0004\u0018\u00010\r2\u0006\u0010\u0012\u001a\u00020\rH\u0002J\u001f\u0010(\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u0016\u001a\u00020\r2\u0006\u0010\u0017\u001a\u00020\rH\u0002\u00a2\u0006\u0002\u0010)J\u0012\u0010*\u001a\u0004\u0018\u00010\r2\u0006\u0010\u001a\u001a\u00020\rH\u0002J\u0012\u0010+\u001a\u0004\u0018\u00010\r2\u0006\u0010,\u001a\u00020\rH\u0002J\u0010\u0010-\u001a\u0004\u0018\u00010.2\u0006\u0010\u001a\u001a\u00020\rJ\u0015\u0010/\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u001a\u001a\u00020\r\u00a2\u0006\u0002\u00100J!\u00101\u001a\u0004\u0018\u00010\r2\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u00104J\u0010\u00101\u001a\u0004\u0018\u00010\r2\u0006\u0010\u001a\u001a\u00020\rJ \u00101\u001a\u0004\u0018\u00010\r2\f\u00102\u001a\b\u0012\u0004\u0012\u00020\r0\u00062\u0006\u00105\u001a\u00020\u0019H\u0002J!\u00106\u001a\u0004\u0018\u00010\r2\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u00104J!\u00107\u001a\u0004\u0018\u00010.2\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u00108J\u0010\u00107\u001a\u0004\u0018\u00010.2\u0006\u00109\u001a\u00020\rJ\u0010\u0010:\u001a\u0004\u0018\u00010\r2\u0006\u0010\u001a\u001a\u00020\rJ-\u0010;\u001a\u0010\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u0007\u0018\u00010\u000b2\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u0010<J\u0015\u0010=\u001a\u0004\u0018\u00010>2\u0006\u0010\u001a\u001a\u00020\r\u00a2\u0006\u0002\u0010?J\u001f\u0010@\u001a\u00020\u00192\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u0010AJ\u0015\u0010B\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u001a\u001a\u00020\r\u00a2\u0006\u0002\u00100J\u0010\u0010C\u001a\u0004\u0018\u00010\r2\u0006\u0010,\u001a\u00020\rJ\u0010\u0010D\u001a\u0004\u0018\u00010\r2\u0006\u0010E\u001a\u00020\rJ\u0017\u0010F\u001a\u0004\u0018\u00010\u00072\u0006\u0010G\u001a\u00020\rH\u0002\u00a2\u0006\u0002\u00100J!\u0010H\u001a\u0004\u0018\u00010\u00072\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u0010IJ\u0015\u0010H\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u001a\u001a\u00020\r\u00a2\u0006\u0002\u00100J/\u0010J\u001a\u0012\u0012\u0004\u0012\u00020\u0007\u0012\u0006\u0012\u0004\u0018\u00010\u0007\u0018\u00010\u000b2\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u0010<J(\u0010J\u001a\u0012\u0012\u0004\u0012\u00020\u0007\u0012\u0006\u0012\u0004\u0018\u00010\u0007\u0018\u00010\u000b2\u0006\u0010\u001a\u001a\u00020\r2\u0006\u0010K\u001a\u00020\u0019H\u0002J/\u0010L\u001a\u0012\u0012\u0004\u0012\u00020\u0007\u0012\u0006\u0012\u0004\u0018\u00010\u0007\u0018\u00010\u000b2\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u0010<J!\u0010M\u001a\u0004\u0018\u00010\u00072\u0012\u00102\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r03\"\u00020\r\u00a2\u0006\u0002\u0010IJ\u0010\u0010N\u001a\u0004\u0018\u00010\r2\u0006\u0010\u001a\u001a\u00020\rJ\u0015\u0010O\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u001a\u001a\u00020\r\u00a2\u0006\u0002\u00100J\u0015\u0010P\u001a\u0004\u0018\u00010>2\u0006\u0010\u001a\u001a\u00020\r\u00a2\u0006\u0002\u0010?J0\u0010Q\u001a\b\u0012\u0004\u0012\u00020R0\u00062\u0006\u0010,\u001a\u00020\r2\b\b\u0002\u0010\u001f\u001a\u00020\u00072\u0010\b\u0002\u0010S\u001a\n\u0012\u0004\u0012\u00020\r\u0018\u00010TJ \u0010U\u001a\u0004\u0018\u00010R2\u0006\u0010V\u001a\u00020\r2\f\u0010W\u001a\b\u0012\u0004\u0012\u00020\r0\u0006H\u0002J\u0018\u0010X\u001a\u00020\u00192\u0006\u0010G\u001a\u00020\r2\u0006\u0010Y\u001a\u00020\rH\u0002J\u0018\u0010Z\u001a\u00020\u00072\u0006\u0010\u001a\u001a\u00020\r2\u0006\u0010V\u001a\u00020\u001cH\u0002R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R \u0010\n\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\f\u0012\u0004\u0012\u00020\r0\u000b0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\r0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006]"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/TextParser;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "REGULAR_STARDUST_COSTS", "", "", "VALID_CANDY_COSTS", "", "ocrAliasPatterns", "Lkotlin/Pair;", "Lkotlin/text/Regex;", "", "pokemonNames", "validListDescending", "buildObservations", "clean", "compact", "buildOcrConfusionVariants", "observation", "commonPrefixLength", "first", "second", "containsCandyTokenHint", "", "text", "extractNumericCandidates", "Lcom/pokerarity/scanner/util/ocr/TextParser$NumericCandidate;", "findNamesWithPrefix", "prefix", "limit", "isValidStardust", "v", "levenshtein", "lhs", "", "rhs", "loadPokemonNames", "matchOcrAlias", "mergeThousandsTokens", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Integer;", "normalizeCandyInput", "normalizeNameInput", "ocrText", "parseBottomDate", "Ljava/util/Date;", "parseCP", "(Ljava/lang/String;)Ljava/lang/Integer;", "parseCandyName", "texts", "", "([Ljava/lang/String;)Ljava/lang/String;", "allowLoose", "parseCandyNameLoose", "parseDate", "([Ljava/lang/String;)Ljava/util/Date;", "allText", "parseGender", "parseHPPair", "([Ljava/lang/String;)Lkotlin/Pair;", "parseHeight", "", "(Ljava/lang/String;)Ljava/lang/Float;", "parseLuckyLabel", "([Ljava/lang/String;)Z", "parseMegaEnergy", "parseName", "parseNameFromFullText", "fullText", "parseNumericToken", "token", "parsePowerUpCandyCost", "([Ljava/lang/String;)Ljava/lang/Integer;", "parsePowerUpCostPair", "strict", "parsePowerUpCostPairStrict", "parsePowerUpStardust", "parseSizeTag", "parseStardust", "parseWeight", "rankNameCandidates", "Lcom/pokerarity/scanner/util/ocr/TextParser$NameCandidate;", "restrictTo", "", "scoreCandidate", "candidate", "observations", "sharesStrongTokenPrefix", "name", "surroundingDigitNoise", "NameCandidate", "NumericCandidate", "app_debug"})
public final class TextParser {
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> pokemonNames = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.Integer> REGULAR_STARDUST_COSTS = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.Integer> validListDescending = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Set<java.lang.Integer> VALID_CANDY_COSTS = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<kotlin.Pair<kotlin.text.Regex, java.lang.String>> ocrAliasPatterns = null;
    
    public TextParser(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parseCP(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Pair<java.lang.Integer, java.lang.Integer> parseHPPair(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.Date parseDate(@org.jetbrains.annotations.NotNull()
    java.lang.String allText) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.Date parseDate(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.Date parseBottomDate(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseCandyName(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseCandyName(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseCandyNameLoose(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    private final java.lang.String parseCandyName(java.util.List<java.lang.String> texts, boolean allowLoose) {
        return null;
    }
    
    private final java.lang.String normalizeCandyInput(java.lang.String text) {
        return null;
    }
    
    private final boolean containsCandyTokenHint(java.lang.String text) {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseNameFromFullText(@org.jetbrains.annotations.NotNull()
    java.lang.String fullText) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseName(@org.jetbrains.annotations.NotNull()
    java.lang.String ocrText) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.util.ocr.TextParser.NameCandidate> rankNameCandidates(@org.jetbrains.annotations.NotNull()
    java.lang.String ocrText, int limit, @org.jetbrains.annotations.Nullable()
    java.util.Collection<java.lang.String> restrictTo) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> findNamesWithPrefix(@org.jetbrains.annotations.NotNull()
    java.lang.String prefix, int limit) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parseMegaEnergy(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float parseWeight(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float parseHeight(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    public final boolean parseLuckyLabel(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseGender(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseSizeTag(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    /**
     * Power Up (GÃ¼Ã§lendirme) maliyetini ayrÄ±ÅŸtÄ±rÄ±r.
     * OCR gÃ¼rÃ¼ltÃ¼sÃ¼nÃ¼ temizlemek iÃ§in virgÃ¼lleri ve rakam dÄ±ÅŸÄ± karakterleri atar.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parseStardust(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parsePowerUpCandyCost(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parsePowerUpCandyCost(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parsePowerUpStardust(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Pair<java.lang.Integer, java.lang.Integer> parsePowerUpCostPair(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Pair<java.lang.Integer, java.lang.Integer> parsePowerUpCostPairStrict(@org.jetbrains.annotations.NotNull()
    java.lang.String... texts) {
        return null;
    }
    
    private final kotlin.Pair<java.lang.Integer, java.lang.Integer> parsePowerUpCostPair(java.lang.String text, boolean strict) {
        return null;
    }
    
    private final boolean isValidStardust(int v) {
        return false;
    }
    
    private final java.util.List<com.pokerarity.scanner.util.ocr.TextParser.NumericCandidate> extractNumericCandidates(java.lang.String text) {
        return null;
    }
    
    private final java.lang.Integer parseNumericToken(java.lang.String token) {
        return null;
    }
    
    private final java.lang.Integer mergeThousandsTokens(java.lang.String first, java.lang.String second) {
        return null;
    }
    
    private final int surroundingDigitNoise(java.lang.String text, com.pokerarity.scanner.util.ocr.TextParser.NumericCandidate candidate) {
        return 0;
    }
    
    private final java.util.List<java.lang.String> loadPokemonNames(android.content.Context context) {
        return null;
    }
    
    private final java.lang.String normalizeNameInput(java.lang.String ocrText) {
        return null;
    }
    
    private final java.util.List<java.lang.String> buildObservations(java.lang.String clean, java.lang.String compact) {
        return null;
    }
    
    private final java.util.List<java.lang.String> buildOcrConfusionVariants(java.lang.String observation) {
        return null;
    }
    
    private final com.pokerarity.scanner.util.ocr.TextParser.NameCandidate scoreCandidate(java.lang.String candidate, java.util.List<java.lang.String> observations) {
        return null;
    }
    
    private final java.lang.String matchOcrAlias(java.lang.String compact) {
        return null;
    }
    
    private final boolean sharesStrongTokenPrefix(java.lang.String token, java.lang.String name) {
        return false;
    }
    
    private final int commonPrefixLength(java.lang.String first, java.lang.String second) {
        return 0;
    }
    
    private final int levenshtein(java.lang.CharSequence lhs, java.lang.CharSequence rhs) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\b\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0007H\u00c6\u0003J\'\u0010\u0012\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u0013\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0007H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0018"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/TextParser$NameCandidate;", "", "name", "", "score", "", "distance", "", "(Ljava/lang/String;DI)V", "getDistance", "()I", "getName", "()Ljava/lang/String;", "getScore", "()D", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class NameCandidate {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String name = null;
        private final double score = 0.0;
        private final int distance = 0;
        
        public NameCandidate(@org.jetbrains.annotations.NotNull()
        java.lang.String name, double score, int distance) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getName() {
            return null;
        }
        
        public final double getScore() {
            return 0.0;
        }
        
        public final int getDistance() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        public final double component2() {
            return 0.0;
        }
        
        public final int component3() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.ocr.TextParser.NameCandidate copy(@org.jetbrains.annotations.NotNull()
        java.lang.String name, double score, int distance) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0082\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\'\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001R\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\b\u00a8\u0006\u0015"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/TextParser$NumericCandidate;", "", "value", "", "start", "end", "(III)V", "getEnd", "()I", "getStart", "getValue", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    static final class NumericCandidate {
        private final int value = 0;
        private final int start = 0;
        private final int end = 0;
        
        public NumericCandidate(int value, int start, int end) {
            super();
        }
        
        public final int getValue() {
            return 0;
        }
        
        public final int getStart() {
            return 0;
        }
        
        public final int getEnd() {
            return 0;
        }
        
        public final int component1() {
            return 0;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final int component3() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.ocr.TextParser.NumericCandidate copy(int value, int start, int end) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}