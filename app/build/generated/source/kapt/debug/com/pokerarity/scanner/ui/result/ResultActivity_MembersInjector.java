package com.pokerarity.scanner.ui.result;

import com.pokerarity.scanner.data.repository.PokemonRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class ResultActivity_MembersInjector implements MembersInjector<ResultActivity> {
  private final Provider<PokemonRepository> repositoryProvider;

  public ResultActivity_MembersInjector(Provider<PokemonRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  public static MembersInjector<ResultActivity> create(
      Provider<PokemonRepository> repositoryProvider) {
    return new ResultActivity_MembersInjector(repositoryProvider);
  }

  public static MembersInjector<ResultActivity> create(
      javax.inject.Provider<PokemonRepository> repositoryProvider) {
    return new ResultActivity_MembersInjector(Providers.asDaggerProvider(repositoryProvider));
  }

  @Override
  public void injectMembers(ResultActivity instance) {
    injectRepository(instance, repositoryProvider.get());
  }

  @InjectedFieldSignature("com.pokerarity.scanner.ui.result.ResultActivity.repository")
  public static void injectRepository(ResultActivity instance, PokemonRepository repository) {
    instance.repository = repository;
  }
}
