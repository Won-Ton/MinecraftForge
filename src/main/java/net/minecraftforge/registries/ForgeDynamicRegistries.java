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

import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldSettingsImport;
import net.minecraft.world.DimensionType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.DimensionSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public abstract class ForgeDynamicRegistries 
{
    private static final Logger LOGGER = LogManager.getLogger();

    private boolean event;
    private boolean rollback;
    private WorldSettingsImport.IResourceAccess.RegistryAccess snapshot;

    public boolean postEvent()
    {
        return event;
    }

    /**
     * @param rollback Marks whether the registry needs to be rolled back before events are fired.
     * @param event Marks whether events should be fired for this DynamicRegistries instance.
     * @return The current DynamicRegistries.Impl instance.
     */
    public DynamicRegistries.Impl prime(boolean rollback, boolean event)
    {
        this.event = event;
        this.rollback = rollback;

        // Note: the snapshot should be taken during same generation of ResourceManager that
        // this DynamicRegistries instance was created on! This is so that the block tags used
        // in features/carvers/structures serialize correctly.
        createSnapshot();

        return self();
    }

    public void rollback()
    {
        if (!rollback || snapshot == null) return;
        LOGGER.info("Restoring dynamic registries snapshot...");

        boolean post = this.event;
        // Prevent the dynamic registry load event from firing for this load.
        this.event = false;

        // Decode the snapshot data back into the dynamic registries.
        WorldSettingsImport.create(JsonOps.INSTANCE, snapshot, self());

        this.event = post;
    }

    private void createSnapshot()
    {
        // Note: We only need to snapshot once. Data is stored as json so we don't need
        // to worry about instances de-syncing.
        if (snapshot != null) return;

        LOGGER.info("Creating snapshot for dynamic registries...");
        snapshot = new WorldSettingsImport.IResourceAccess.RegistryAccess();

        DynamicRegistries.Impl dynamicRegistries = self();
        addToSnapshot(dynamicRegistries, snapshot, Registry.NOISE_GENERATOR_SETTINGS_REGISTRY, DimensionSettings.DIRECT_CODEC);
        addToSnapshot(dynamicRegistries, snapshot, Registry.DIMENSION_TYPE_REGISTRY, DimensionType.DIRECT_CODEC);
        addToSnapshot(dynamicRegistries, snapshot, Registry.BIOME_REGISTRY, Biome.DIRECT_CODEC);
    }

    private DynamicRegistries.Impl self()
    {
        return (DynamicRegistries.Impl) this;
    }

    private static <E> void addToSnapshot(DynamicRegistries.Impl registries,
                                          WorldSettingsImport.IResourceAccess.RegistryAccess access,
                                          RegistryKey<? extends Registry<E>> registryKey,
                                          Encoder<E> encoder)
    {
        MutableRegistry<E> registry = registries.registryOrThrow(registryKey);
        for (Map.Entry<RegistryKey<E>, E> entry : registry.entrySet()) {
            int id = registry.getId(entry.getValue());
            access.add(registries, entry.getKey(), encoder, id, entry.getValue(), Lifecycle.stable());
        }
    }
}
