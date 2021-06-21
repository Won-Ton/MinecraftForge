package net.minecraftforge.registries;

import com.mojang.serialization.Lifecycle;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

public final class DynamicRegistriesAccess
{
    private final Map<RegistryKey<?>, RegistryAccess<?>> cache;

    public DynamicRegistriesAccess(Map<RegistryKey<?>, RegistryAccess<?>> cache)
    {
        this.cache = cache;
    }

    /**
     * Check whether this DynamicRegistriesAccess holds a Registry for the given RegistryKey.
     */
    public <E> boolean has(RegistryKey<Registry<E>> key)
    {
        return cache.containsKey(key);
    }

    /**
     * Get the Registry for the provided RegistryKey.
     */
    public <E> Optional<RegistryAccess<E>> getRegistry(RegistryKey<Registry<E>> key)
    {
        //noinspection unchecked
        return Optional.ofNullable(cache.get(key)).map(a -> (RegistryAccess<E>) a);
    }

    public static DynamicRegistriesAccess create(DynamicRegistries registries, List<ForgeDynamicRegistries.KeyHolder<?>> registryKeys)
    {
        Map<RegistryKey<?>, RegistryAccess<?>> cache = new HashMap<>();
        for (ForgeDynamicRegistries.KeyHolder<?> holder : registryKeys)
        {
            createAccess(registries, holder).ifPresent(access -> cache.put(holder.key, access));
        }
        return new DynamicRegistriesAccess(cache);
    }

    private static <E> Optional<RegistryAccess<E>> createAccess(DynamicRegistries registries, ForgeDynamicRegistries.KeyHolder<E> holder)
    {
        return registries.registry(holder.key).map(RegistryAccess::new);
    }

    public static final class RegistryAccess<E>
    {
        private final MutableRegistry<E> registry;
        private final Function<E, Lifecycle> lifecycleFunction;

        public RegistryAccess(MutableRegistry<E> registry)
        {
            this.registry = registry;
            this.lifecycleFunction = ForgeDynamicRegistries.getLifecyleFunction(registry);
        }

        /**
         * Get the entry currently registered to the given RegistryKey.
         */
        public Optional<E> get(RegistryKey<E> key)
        {
            return registry.getOptional(key);
        }

        /**
         * Get the entry currently registered to the given registry name.
         */
        public Optional<E> get(ResourceLocation registryName)
        {
            return registry.getOptional(registryName);
        }

        /**
         * Replace the entry currently registered to the RegistryKey with a new value.
         */
        public boolean override(RegistryKey<E> key, @Nonnull E value)
        {
            E current = registry.get(key);
            if (current == null) return false;

            Lifecycle lifecycle = lifecycleFunction.apply(current);
            registry.registerOrOverride(OptionalInt.empty(), key, value, lifecycle);
            return true;
        }
    }
}
