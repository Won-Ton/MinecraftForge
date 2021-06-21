/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.registries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Lifecycle;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class ForgeDynamicRegistries 
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static final List<KeyHolder<?>> REGISTRIES = ImmutableList.of(
            new KeyHolder<>(Registry.DIMENSION_TYPE_REGISTRY),
            new KeyHolder<>(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY),
            new KeyHolder<>(Registry.BIOME_REGISTRY),
            new KeyHolder<>(Registry.CONFIGURED_CARVER_REGISTRY),
            new KeyHolder<>(Registry.CONFIGURED_FEATURE_REGISTRY),
            new KeyHolder<>(Registry.CONFIGURED_SURFACE_BUILDER_REGISTRY),
            new KeyHolder<>(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY),
            new KeyHolder<>(Registry.TEMPLATE_POOL_REGISTRY),
            new KeyHolder<>(Registry.PROCESSOR_LIST_REGISTRY)
    );

    private boolean event;
    private List<RegistrySnapshot<?>> snapshots;

    /**
     * Get whether the DynamicRegistries have been marked ready for load events to be fired.
     */
    public boolean isMarkedForLoadEvent()
    {
        return event;
    }

    /**
     * Clears all registry snapshots currently held by the DynamicRegistries.
     */
    public DynamicRegistries.Impl disposeSnapshots()
    {
        this.snapshots.clear();
        this.snapshots = null;
        return self();
    }

    /**
     * Mark the DynamicRegistries as ready for the load events to be fired.
     */
    public DynamicRegistries.Impl markForLoadEvent(boolean ready)
    {
        this.event = ready;
        return self();
    }

    /**
     * Create a snapshot of the current contents of the dynamic registries.
     */
    public void createSnapshot()
    {
        if (snapshots != null) return;

        LOGGER.info("Creating snapshot for dynamic registries...");
        DynamicRegistries.Impl dynamicRegistries = self();

        snapshots = new ArrayList<>();
        REGISTRIES.forEach(holder -> snapshots.add(createSnapshot(dynamicRegistries, holder)));
    }

    /**
     * Rollback the dynamic registries contents to the current snapshot.
     */
    public DynamicRegistries.Impl rollback()
    {
        event = false;

        DynamicRegistries.Impl dynamicRegistries = self();
        if (snapshots == null) return dynamicRegistries;

        LOGGER.info("Restoring dynamic registries from snapshot...");
        snapshots.forEach(snapshot -> restoreSnapshot(dynamicRegistries, snapshot));
        disposeSnapshots();

        return dynamicRegistries;
    }

    private DynamicRegistries.Impl self()
    {
        return (DynamicRegistries.Impl) this;
    }

    private static <E> RegistrySnapshot<E> createSnapshot(DynamicRegistries.Impl registries, KeyHolder<E> holder)
    {
        Registry<E> registry = registries.registryOrThrow(holder.key);
        Function<E, Lifecycle> lifecycleFunction = getLifecyleFunction(registry);

        List<EntrySnapshot<E>> snapshots = new ArrayList<>();
        for (Map.Entry<RegistryKey<E>, E> entry : registry.entrySet())
        {
            Lifecycle lifecycle = lifecycleFunction.apply(entry.getValue());
            snapshots.add(new EntrySnapshot<>(entry.getKey(), entry.getValue(), lifecycle));
        }

        return new RegistrySnapshot<>(holder.key, snapshots);
    }

    private static <E> void restoreSnapshot(DynamicRegistries.Impl registries, RegistrySnapshot<E> snapshot)
    {
        MutableRegistry<E> registry = registries.registryOrThrow(snapshot.registryKey);
        for (EntrySnapshot<E> entry : snapshot.snapshots)
        {
            E e = registry.get(entry.key);
            if (e == null || e == entry.value) continue;
            registry.registerOrOverride(OptionalInt.empty(), entry.key, entry.value, entry.lifecycle);
        }
    }

    public static <E> Function<E, Lifecycle> getLifecyleFunction(Registry<E> registry)
    {
        if (registry instanceof SimpleRegistry)
        {
            SimpleRegistry<E> simpleRegistry = (SimpleRegistry<E>) registry;
            return simpleRegistry::lifecycle;
        }
        return e -> Lifecycle.stable(); // Shouldn't happen
    }

    public DynamicRegistriesAccess accessExcluding(Predicate<RegistryKey<?>> predicate)
    {
        List<KeyHolder<?>> keys = REGISTRIES.stream()
                .filter(holder -> !predicate.test(holder.key))
                .collect(Collectors.toList());
        return DynamicRegistriesAccess.create(self(), keys);
    }

    public static class KeyHolder<E>
    {
        public final RegistryKey<? extends Registry<E>> key;

        private KeyHolder(RegistryKey<? extends Registry<E>> key)
        {
            this.key = key;
        }
    }

    private static class RegistrySnapshot<E>
    {
        private final RegistryKey<? extends Registry<E>> registryKey;
        private final List<EntrySnapshot<E>> snapshots;

        private RegistrySnapshot(RegistryKey<? extends Registry<E>> registryKey, List<EntrySnapshot<E>> snapshots)
        {
            this.registryKey = registryKey;
            this.snapshots = snapshots;
        }
    }

    private static class EntrySnapshot<E>
    {
        private final RegistryKey<E> key;
        private final E value;
        private final Lifecycle lifecycle;

        private EntrySnapshot(RegistryKey<E> key, E value, Lifecycle lifecycle)
        {
            this.key = key;
            this.value = value;
            this.lifecycle = lifecycle;
        }
    }
}
