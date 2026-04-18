# Project Memory (Extracted from Codex History)

**Source File**: rollout-2026-03-15T16-35-52-019cf25a-a2c0-7013-af53-11c02e70e23c.md
**Original Size**: 45.25 MB

## Technical Stack
- Python (Scripts)
- OCR (Tesseract/Vision)
- Android (Kotlin)

## Recent Tasks (Extracted)

## Notable Decisions/Logic

## Core Context Summary (Last conversation snippet)
hell_command
- Call ID: `call_JBI7dM6lgwBDfF7SKNgU7Rcy`
- Timestamp: `2026-04-15 16:05:47`

### Arguments
```json
{
  "command": "Remove-Item -Force .\\artifacts\\assemble_release.log, .\\artifacts\\full_test_run.log, .\\artifacts\\targeted_event_fix.log",
  "workdir": "<PATH>\Users\\Caglar\\Desktop\\PokeRarityScanner",
  "timeout_ms": 20000
}
```

### Output
```
Exit code: 0
Wall time: 0.3 seconds
Output:

```

## [tool] shell_command
- Call ID: `call_FjsN8srDScVMQYYgCRCoHSaU`
- Timestamp: `2026-04-15 16:05:53`

### Arguments
```json
{
  "command": "git status --short -- app/src/main/java app/src/test/java app/src/main/assets/data/master_pokedex.json gradle.properties scripts artifacts",
  "workdir": "<PATH>\Users\\Caglar\\Desktop\\PokeRarityScanner",
  "timeout_ms": 20000
}
```

### Output
```
Exit code: 0
Wall time: 0.3 seconds
Output:
 M app/src/main/assets/data/master_pokedex.json
 M app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeHistoricalEventResolver.kt
 M app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantDbLoader.kt
 M app/src/main/java/com/pokerarity/scanner/data/repository/MasterPokedexLoader.kt
 M app/src/main/java/com/pokerarity/scanner/data/repository/PokemonRepository.kt
 M app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt
 M app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt
 M app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationSanity.kt
 M app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt
 M app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt
 M app/src/main/java/com/pokerarity/scanner/util/vision/CostumeSignatureStore.kt
 M app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt
 M app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt
 M app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantScoring.kt
 M app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt
 M app/src/test/java/com/pokerarity/scanner/AuthoritativeHistoricalEventResolverTest.kt
 M app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt
 M app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt
 M app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt
 M app/src/test/java/com/pokerarity/scanner/LegacyVariantPathRemovalTest.kt
 M app/src/test/java/com/pokerarity/scanner/MasterPokedexLoaderTest.kt
 M app/src/test/java/com/pokerarity/scanner/TextParserNameRecoveryTest.kt
 M app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt
 M app/src/test/java/com/pokerarity/scanner/VariantExplanationSanityTest.kt
 M artifacts/agent_worklog.md
 M artifacts/rollback_notes.md
 M gradle.properties
 M scripts/generate_master_pokedex.py
?? app/src/main/java/com/pokerarity/scanner/data/repository/EventMetadataEvidence.kt

```

## [#3380] Assistant
- Timestamp: `2026-04-15 16:06:11`

Kod, test, build ve cihaz doğrulaması tamam. Şimdi kaynak değişikliklerini commit/push edip `v1.8.1` release asset’ini yayınlıyorum.

## [tool] shell_command
- Call ID: `call_9rkWytP3KeBe9TU7oUrttLYV`
- Timestamp: `2026-04-15 16:06:20`

### Arguments
```json
{
  "command": "git add app/src/main/assets/data/master_pokedex.json app/src/main/java/com/pokerarity/scanner/data/repository/EventMetadataEvidence.kt app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeHistoricalEventResolver.kt app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantDbLoader.kt app/src/main/java/com/pokerarity/scanner/data/repository/MasterPokedexLoader.kt app/src/main/java/com/pokerarity/scanner/data/repository/PokemonRepository.kt app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationSanity.kt app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt app/src/main/java/com/pokerarity/scanner/util/vision/CostumeSignatureStore.kt app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantScoring.kt app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt app/src/test/java/com/pokerarity/scanner/AuthoritativeHistoricalEventResolverTest.kt app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt app/src/test/java/com/pokerarity/scanner/LegacyVariantPathRemovalTest.kt app/src/test/java/com/pokerarity/scanner/MasterPokedexLoaderTest.kt app/src/test/java/com/pokerarity/scanner/TextParserNameRecoveryTest.kt app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt app/src/test/java/com/pokerarity/scanner/VariantExplanationSanityTest.kt artifacts/agent_worklog.md artifacts/rollback_notes.md gradle.properties scripts/generate_master_pokedex.py",
  "workdir": "<PATH>\Users\\Caglar\\Desktop\\PokeRarityScanner",
  "timeout_ms": 120000
}
```

### Output
```
Exit code: 0
Wall time: 0.5 seconds
Output:
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeHistoricalEventResolver.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantDbLoader.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/MasterPokedexLoader.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/PokemonRepository.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationSanity.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/util/vision/CostumeSignatureStore.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantScoring.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/AuthoritativeHistoricalEventResolverTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/LegacyVariantPathRemovalTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/MasterPokedexLoaderTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/TextParserNameRecoveryTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/test/java/com/pokerarity/scanner/VariantExplanationSanityTest.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'artifacts/agent_worklog.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'artifacts/rollback_notes.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'gradle.properties', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'scripts/generate_master_pokedex.py', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'app/src/main/java/com/pokerarity/scanner/data/repository/EventMetadataEvidence.kt', LF will be replaced by CRLF the next time Git touches it

```
