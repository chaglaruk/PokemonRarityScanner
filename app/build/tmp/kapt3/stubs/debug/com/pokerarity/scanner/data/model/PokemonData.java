package com.pokerarity.scanner.data.model;

import java.util.Date;

/**
 * Data extracted from a Pokemon GO screenshot.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0010$\n\u0002\b@\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u00af\u0002\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\t\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u000b\u001a\u0004\u0018\u00010\f\u0012\b\u0010\r\u001a\u0004\u0018\u00010\f\u0012\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\u000f\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\f\u0012\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012\u0012\b\b\u0002\u0010\u0013\u001a\u00020\u0007\u0012\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u0015\u0012\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0018\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\f\u0012\n\b\u0002\u0010\u001d\u001a\u0004\u0018\u00010\f\u0012\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\u0007\u0012\u0014\b\u0002\u0010 \u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00070!\u00a2\u0006\u0002\u0010\"J\u0010\u0010E\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u000b\u0010F\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0010\u0010G\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u0010\u0010H\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010\'J\u000b\u0010I\u001a\u0004\u0018\u00010\u0012H\u00c6\u0003J\t\u0010J\u001a\u00020\u0007H\u00c6\u0003J\u000b\u0010K\u001a\u0004\u0018\u00010\u0015H\u00c6\u0003J\u0010\u0010L\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u000b\u0010M\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010N\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0010\u0010O\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u0010\u0010P\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u0010\u0010Q\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u0010\u0010R\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u0010\u0010S\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010\'J\u0010\u0010T\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010\'J\u000b\u0010U\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010V\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0015\u0010W\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00070!H\u00c6\u0003J\u0010\u0010X\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u000b\u0010Y\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010Z\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010[\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0010\u0010\\\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010$J\u0010\u0010]\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010\'J\u0010\u0010^\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010\'J\u00ce\u0002\u0010_\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\r\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u000f\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u00122\b\b\u0002\u0010\u0013\u001a\u00020\u00072\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u00152\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0018\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u001d\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\u00072\u0014\b\u0002\u0010 \u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00070!H\u00c6\u0001\u00a2\u0006\u0002\u0010`J\u0013\u0010a\u001a\u00020b2\b\u0010c\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010d\u001a\u00020\u0003H\u00d6\u0001J\t\u0010e\u001a\u00020\u0007H\u00d6\u0001R\u0015\u0010\u0019\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b#\u0010$R\u0015\u0010\u001c\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010(\u001a\u0004\b&\u0010\'R\u0015\u0010\u001a\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b)\u0010$R\u0015\u0010\u001b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b*\u0010$R\u0015\u0010\u001d\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010(\u001a\u0004\b+\u0010\'R\u0015\u0010\u0010\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010(\u001a\u0004\b,\u0010\'R\u0013\u0010\u001e\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010.R\u0013\u0010\t\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u0010.R\u0013\u0010\u0011\u001a\u0004\u0018\u00010\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b0\u00101R\u0015\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b2\u0010$R\u0013\u0010\u0014\u001a\u0004\u0018\u00010\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b3\u00104R\u0013\u0010\u000e\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b5\u0010.R\u0015\u0010\r\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010(\u001a\u0004\b6\u0010\'R\u0015\u0010\u0004\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b7\u0010$R\u0015\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b8\u0010$R\u0015\u0010\n\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b9\u0010$R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b:\u0010.R\u0013\u0010\u001f\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b;\u0010.R\u001d\u0010 \u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00070!\u00a2\u0006\b\n\u0000\u001a\u0004\b<\u0010=R\u0015\u0010\u0016\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\b>\u0010$R\u0013\u0010\u0017\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b?\u0010.R\u0013\u0010\u0018\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b@\u0010.R\u0011\u0010\u0013\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bA\u0010.R\u0013\u0010\b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bB\u0010.R\u0015\u0010\u000f\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010%\u001a\u0004\bC\u0010$R\u0015\u0010\u000b\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010(\u001a\u0004\bD\u0010\'\u00a8\u0006f"}, d2 = {"Lcom/pokerarity/scanner/data/model/PokemonData;", "", "cp", "", "hp", "maxHp", "name", "", "realName", "candyName", "megaEnergy", "weight", "", "height", "gender", "stardust", "arcLevel", "caughtDate", "Ljava/util/Date;", "rawOcrText", "fullVariantMatch", "Lcom/pokerarity/scanner/data/model/FullVariantMatch;", "powerUpCandyCost", "powerUpCandySource", "powerUpStardustSource", "appraisalAttack", "appraisalDefense", "appraisalStamina", "appraisalConfidence", "arcEstimatedLevel", "arcSource", "ocrDiagnosticsDir", "ocrDiagnosticsFiles", "", "(Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Float;Ljava/util/Date;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/FullVariantMatch;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V", "getAppraisalAttack", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getAppraisalConfidence", "()Ljava/lang/Float;", "Ljava/lang/Float;", "getAppraisalDefense", "getAppraisalStamina", "getArcEstimatedLevel", "getArcLevel", "getArcSource", "()Ljava/lang/String;", "getCandyName", "getCaughtDate", "()Ljava/util/Date;", "getCp", "getFullVariantMatch", "()Lcom/pokerarity/scanner/data/model/FullVariantMatch;", "getGender", "getHeight", "getHp", "getMaxHp", "getMegaEnergy", "getName", "getOcrDiagnosticsDir", "getOcrDiagnosticsFiles", "()Ljava/util/Map;", "getPowerUpCandyCost", "getPowerUpCandySource", "getPowerUpStardustSource", "getRawOcrText", "getRealName", "getStardust", "getWeight", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component20", "component21", "component22", "component23", "component24", "component25", "component26", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Float;Ljava/util/Date;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/FullVariantMatch;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)Lcom/pokerarity/scanner/data/model/PokemonData;", "equals", "", "other", "hashCode", "toString", "PokeRarityScanner-v1.8.2_debug"})
public final class PokemonData {
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer cp = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer hp = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer maxHp = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String name = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String realName = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String candyName = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer megaEnergy = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Float weight = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Float height = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String gender = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer stardust = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Float arcLevel = null;
    @org.jetbrains.annotations.Nullable()
    private final java.util.Date caughtDate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String rawOcrText = null;
    @org.jetbrains.annotations.Nullable()
    private final com.pokerarity.scanner.data.model.FullVariantMatch fullVariantMatch = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer powerUpCandyCost = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String powerUpCandySource = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String powerUpStardustSource = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer appraisalAttack = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer appraisalDefense = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer appraisalStamina = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Float appraisalConfidence = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Float arcEstimatedLevel = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String arcSource = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String ocrDiagnosticsDir = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, java.lang.String> ocrDiagnosticsFiles = null;
    
    public PokemonData(@org.jetbrains.annotations.Nullable()
    java.lang.Integer cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer maxHp, @org.jetbrains.annotations.Nullable()
    java.lang.String name, @org.jetbrains.annotations.Nullable()
    java.lang.String realName, @org.jetbrains.annotations.Nullable()
    java.lang.String candyName, @org.jetbrains.annotations.Nullable()
    java.lang.Integer megaEnergy, @org.jetbrains.annotations.Nullable()
    java.lang.Float weight, @org.jetbrains.annotations.Nullable()
    java.lang.Float height, @org.jetbrains.annotations.Nullable()
    java.lang.String gender, @org.jetbrains.annotations.Nullable()
    java.lang.Integer stardust, @org.jetbrains.annotations.Nullable()
    java.lang.Float arcLevel, @org.jetbrains.annotations.Nullable()
    java.util.Date caughtDate, @org.jetbrains.annotations.NotNull()
    java.lang.String rawOcrText, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.FullVariantMatch fullVariantMatch, @org.jetbrains.annotations.Nullable()
    java.lang.Integer powerUpCandyCost, @org.jetbrains.annotations.Nullable()
    java.lang.String powerUpCandySource, @org.jetbrains.annotations.Nullable()
    java.lang.String powerUpStardustSource, @org.jetbrains.annotations.Nullable()
    java.lang.Integer appraisalAttack, @org.jetbrains.annotations.Nullable()
    java.lang.Integer appraisalDefense, @org.jetbrains.annotations.Nullable()
    java.lang.Integer appraisalStamina, @org.jetbrains.annotations.Nullable()
    java.lang.Float appraisalConfidence, @org.jetbrains.annotations.Nullable()
    java.lang.Float arcEstimatedLevel, @org.jetbrains.annotations.Nullable()
    java.lang.String arcSource, @org.jetbrains.annotations.Nullable()
    java.lang.String ocrDiagnosticsDir, @org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, java.lang.String> ocrDiagnosticsFiles) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getCp() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getHp() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getMaxHp() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getName() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getRealName() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getCandyName() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getMegaEnergy() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float getWeight() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float getHeight() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getGender() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getStardust() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float getArcLevel() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.Date getCaughtDate() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getRawOcrText() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.FullVariantMatch getFullVariantMatch() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getPowerUpCandyCost() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getPowerUpCandySource() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getPowerUpStardustSource() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getAppraisalAttack() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getAppraisalDefense() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getAppraisalStamina() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float getAppraisalConfidence() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float getArcEstimatedLevel() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getArcSource() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getOcrDiagnosticsDir() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.lang.String, java.lang.String> getOcrDiagnosticsFiles() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component1() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component10() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component11() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float component12() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.Date component13() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component14() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.FullVariantMatch component15() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component16() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component17() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component18() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component19() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component2() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component20() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component21() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float component22() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float component23() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component24() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component25() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.lang.String, java.lang.String> component26() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component3() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component4() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component5() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component6() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component7() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float component8() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float component9() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.PokemonData copy(@org.jetbrains.annotations.Nullable()
    java.lang.Integer cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer maxHp, @org.jetbrains.annotations.Nullable()
    java.lang.String name, @org.jetbrains.annotations.Nullable()
    java.lang.String realName, @org.jetbrains.annotations.Nullable()
    java.lang.String candyName, @org.jetbrains.annotations.Nullable()
    java.lang.Integer megaEnergy, @org.jetbrains.annotations.Nullable()
    java.lang.Float weight, @org.jetbrains.annotations.Nullable()
    java.lang.Float height, @org.jetbrains.annotations.Nullable()
    java.lang.String gender, @org.jetbrains.annotations.Nullable()
    java.lang.Integer stardust, @org.jetbrains.annotations.Nullable()
    java.lang.Float arcLevel, @org.jetbrains.annotations.Nullable()
    java.util.Date caughtDate, @org.jetbrains.annotations.NotNull()
    java.lang.String rawOcrText, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.FullVariantMatch fullVariantMatch, @org.jetbrains.annotations.Nullable()
    java.lang.Integer powerUpCandyCost, @org.jetbrains.annotations.Nullable()
    java.lang.String powerUpCandySource, @org.jetbrains.annotations.Nullable()
    java.lang.String powerUpStardustSource, @org.jetbrains.annotations.Nullable()
    java.lang.Integer appraisalAttack, @org.jetbrains.annotations.Nullable()
    java.lang.Integer appraisalDefense, @org.jetbrains.annotations.Nullable()
    java.lang.Integer appraisalStamina, @org.jetbrains.annotations.Nullable()
    java.lang.Float appraisalConfidence, @org.jetbrains.annotations.Nullable()
    java.lang.Float arcEstimatedLevel, @org.jetbrains.annotations.Nullable()
    java.lang.String arcSource, @org.jetbrains.annotations.Nullable()
    java.lang.String ocrDiagnosticsDir, @org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, java.lang.String> ocrDiagnosticsFiles) {
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