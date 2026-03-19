package com.pokerarity.scanner.data.model;

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.ui.theme.PokemonType;
import com.pokerarity.scanner.ui.theme.RarityColor;
import com.pokerarity.scanner.ui.theme.TypeColors;
import java.text.SimpleDateFormat;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/pokerarity/scanner/data/model/Rarity;", "", "(Ljava/lang/String;I)V", "LEGENDARY", "RARE", "SHINY", "COMMON", "app_debug"})
public enum Rarity {
    /*public static final*/ LEGENDARY /* = new LEGENDARY() */,
    /*public static final*/ RARE /* = new RARE() */,
    /*public static final*/ SHINY /* = new SHINY() */,
    /*public static final*/ COMMON /* = new COMMON() */;
    
    Rarity() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.pokerarity.scanner.data.model.Rarity> getEntries() {
        return null;
    }
}