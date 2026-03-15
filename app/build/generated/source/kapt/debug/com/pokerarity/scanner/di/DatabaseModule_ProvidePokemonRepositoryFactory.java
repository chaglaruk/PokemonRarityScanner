package com.pokerarity.scanner.di;

import com.pokerarity.scanner.data.local.db.AppDatabase;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DatabaseModule_ProvidePokemonRepositoryFactory implements Factory<PokemonRepository> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvidePokemonRepositoryFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public PokemonRepository get() {
    return providePokemonRepository(databaseProvider.get());
  }

  public static DatabaseModule_ProvidePokemonRepositoryFactory create(
      javax.inject.Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvidePokemonRepositoryFactory(Providers.asDaggerProvider(databaseProvider));
  }

  public static DatabaseModule_ProvidePokemonRepositoryFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvidePokemonRepositoryFactory(databaseProvider);
  }

  public static PokemonRepository providePokemonRepository(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.providePokemonRepository(database));
  }
}
