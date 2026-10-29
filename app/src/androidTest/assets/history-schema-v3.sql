-- Version 3 Room schema, before offline_telemetry (v4) and collection_entries (v5).
-- Based on the v3 AppDatabase entity list at commit 276e6758; synthetic migration data only.
CREATE TABLE IF NOT EXISTS `pokemon` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `baseRarity` INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `pokemonId` INTEGER NOT NULL, `rarityWeight` INTEGER NOT NULL, `isOneDayEvent` INTEGER NOT NULL, FOREIGN KEY(`pokemonId`) REFERENCES `pokemon`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_events_pokemonId` ON `events` (`pokemonId`);
CREATE TABLE IF NOT EXISTS `event_pokemon` (`id` TEXT NOT NULL, `baseName` TEXT NOT NULL, `eventName` TEXT NOT NULL, `eventBonusScore` INTEGER NOT NULL, `spriteKey` TEXT, `variantToken` TEXT, `eventStart` INTEGER, `eventEnd` INTEGER, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_event_pokemon_baseName` ON `event_pokemon` (`baseName`);
CREATE INDEX IF NOT EXISTS `index_event_pokemon_eventName` ON `event_pokemon` (`eventName`);
CREATE INDEX IF NOT EXISTS `index_event_pokemon_spriteKey` ON `event_pokemon` (`spriteKey`);
CREATE INDEX IF NOT EXISTS `index_event_pokemon_variantToken` ON `event_pokemon` (`variantToken`);
CREATE TABLE IF NOT EXISTS `scan_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `pokemonName` TEXT, `cp` INTEGER, `hp` INTEGER, `caughtDate` INTEGER, `rawOcrText` TEXT NOT NULL, `isShiny` INTEGER NOT NULL, `isShadow` INTEGER NOT NULL, `isLucky` INTEGER NOT NULL, `hasCostume` INTEGER NOT NULL, `rarityScore` INTEGER NOT NULL, `rarityTier` TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS `telemetry_uploads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uploadId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, `uploadedAt` INTEGER, `payloadJson` TEXT NOT NULL, `screenshotPath` TEXT);
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'synthetic-v3-fixture');
