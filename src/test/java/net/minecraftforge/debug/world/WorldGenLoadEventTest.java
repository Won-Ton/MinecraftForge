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

package net.minecraftforge.debug.world;

import com.mojang.serialization.JsonOps;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.DimensionSettings;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.BlockStateFeatureConfig;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.DimensionStructuresSettings;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.event.world.DynamicRegistryLoadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

@Mod(WorldGenLoadEventTest.MODID)
public class WorldGenLoadEventTest {
    public static final String MODID = "worldgen_load_test";
    public static final Logger LOG = LogManager.getLogger(MODID);

    /**
     * This feature is only defined in json at:
     * data/worldgen_load_test/worldgen/configured_feature/data_feature.json
     */
    private static final ResourceLocation DATA_FEATURE = new ResourceLocation(MODID, "data_feature");

    /**
     * This feature is only defined in the world-gen registry although it can be overridden by json.
     */
    private static final ResourceLocation REGISTERED_FEATURE = new ResourceLocation(MODID, "registered_feature");

    /**
     * The stage that we're adding features to.
     */
    private static final GenerationStage.Decoration TARGET_STAGE = GenerationStage.Decoration.TOP_LAYER_MODIFICATION;

    /**
     * A custom feature that sets the surface layer to a single block type.
     */
    private static final DeferredRegister<Feature<?>> FEATURE_REGISTRAR = DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
    private static final Supplier<SurfaceFeature> COVER_SURFACE = FEATURE_REGISTRAR.register("cover_surface", SurfaceFeature::new);

    public WorldGenLoadEventTest() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);
        FEATURE_REGISTRAR.register(modBus);

        MinecraftForge.EVENT_BUS.addListener(this::modifyStructuresSettings);
        MinecraftForge.EVENT_BUS.addListener(this::modifyPlains);
        MinecraftForge.EVENT_BUS.addListener(this::modifyDesert);
        MinecraftForge.EVENT_BUS.addListener(this::modifyForest);
        MinecraftForge.EVENT_BUS.addListener(this::inspectBiome);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOG.info("Registering configured feature...");
            ConfiguredFeature<?, ?> feature = COVER_SURFACE.get().with(Blocks.GOLD_BLOCK);
            WorldGenRegistries.register(WorldGenRegistries.CONFIGURED_FEATURE, REGISTERED_FEATURE, feature);
        });
    }

    /**
     * Demonstrates how StructureSeparationSettings can be modified after datapacks have loaded.
     */
    private void modifyStructuresSettings(DynamicRegistryLoadEvent event) {
        event.getRegistryAccess().getRegistry(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY)
                .ifPresent(access -> access.get(DimensionSettings.OVERWORLD)
                        .flatMap(WorldGenLoadEventTest::copyDimensionSettings)
                        .ifPresent(settings -> {
                            StructureSeparationSettings portal = new StructureSeparationSettings(2, 1, 0);
                            settings.structureSettings().structureConfig().put(Structure.RUINED_PORTAL, portal);
                            LOG.info("Modifying overworld structure settings");
                            access.override(DimensionSettings.OVERWORLD, settings);
                        }));
    }

    /**
     * Demonstrates how a ConfiguredFeature defined only in json can be added to a biome.
     */
    private void modifyPlains(BiomeLoadingEvent event) {
        if (!Objects.equals(event.getName(), Biomes.PLAINS.location())) return;

        LOG.info("Modifying plains with json configured feature");
        event.getRegistryAccess().getRegistry(Registry.CONFIGURED_FEATURE_REGISTRY)
                .flatMap(r -> r.get(DATA_FEATURE))
                .ifPresent(f -> event.getGeneration().addFeature(TARGET_STAGE, f));
    }

    /**
     * Demonstrates that ConfiguredFeature registered to the WorldGenRegistries can still
     * be added to biomes.
     */
    private void modifyDesert(BiomeLoadingEvent event) {
        if (!Objects.equals(event.getName(), Biomes.DESERT.location())) return;

        LOG.info("Modifying desert with registered configured feature");
        event.getRegistryAccess().getRegistry(Registry.CONFIGURED_FEATURE_REGISTRY)
                .flatMap(r -> r.get(REGISTERED_FEATURE))
                .ifPresent(f -> event.getGeneration().addFeature(TARGET_STAGE, f));
    }

    /**
     * Demonstrates that ConfiguredFeatures can be dynamically constructed and added to biomes.
     */
    private void modifyForest(BiomeLoadingEvent event) {
        if (!Objects.equals(event.getName(), Biomes.FOREST.location())) return;

        LOG.info("Modifying forest with dynamic configured feature");
        ConfiguredFeature<?, ?> feature = COVER_SURFACE.get().with(Blocks.DIAMOND_BLOCK);
        event.getGeneration().addFeature(TARGET_STAGE, feature);
    }

    /**
     * Prints the TOP_LAYER_MODIFICATION ConfiguredFeature json for each biome that we modified
     * to demonstrate that the feature has successfully been added to each biome, and that it
     * exists only once.
     * <p>
     * Plains: minecraft:emerald_block
     * Desert: minecraft:gold_block
     * Forest: minecraft:diamond_block
     */
    private void inspectBiome(FMLServerAboutToStartEvent event) {
        LOG.info("Inspecting biome features...");
        Registry<Biome> registry = event.getServer().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        printFeatures(registry, Biomes.PLAINS);
        printFeatures(registry, Biomes.DESERT);
        printFeatures(registry, Biomes.FOREST);
    }

    private void printFeatures(Registry<Biome> registry, RegistryKey<Biome> key) {
        LOG.info("Biome: {}, Stage: {}, Features:", key.location(), TARGET_STAGE);
        registry.getOrThrow(key).getGenerationSettings().features().get(TARGET_STAGE.ordinal()).stream()
                .map(Supplier::get)
                .forEach(feature -> ConfiguredFeature.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, feature)
                        .resultOrPartial(LOG::error)
                        .ifPresent(json -> LOG.info(" - {}", json)));
    }

    private static class SurfaceFeature extends Feature<BlockStateFeatureConfig> {
        public SurfaceFeature() {
            super(BlockStateFeatureConfig.CODEC);
        }

        public ConfiguredFeature<?, ?> with(Block block) {
            return configured(new BlockStateFeatureConfig(block.defaultBlockState()));
        }

        @Override
        public boolean place(ISeedReader world, ChunkGenerator generator, Random rng, BlockPos pos, BlockStateFeatureConfig config) {
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    mutable.set(pos).move(dx, 0, dz);

                    int y = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, mutable.getX(), mutable.getZ());
                    mutable.setY(y + 1);

                    world.setBlock(mutable, config.state, 2);
                }
            }
            return true;
        }
    }

    private static Optional<DimensionSettings> copyDimensionSettings(DimensionSettings settings) {
        Constructor<?> constructor = DimensionSettings.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);

        try {
            return Optional.of((DimensionSettings) constructor.newInstance(
                    new DimensionStructuresSettings(
                            Optional.ofNullable(settings.structureSettings().stronghold()),
                            new HashMap<>(settings.structureSettings().structureConfig())
                    ),
                    settings.noiseSettings(),
                    settings.getDefaultBlock(),
                    settings.getDefaultFluid(),
                    settings.getBedrockRoofPosition(),
                    settings.getBedrockFloorPosition(),
                    settings.seaLevel(),
                    false
            ));
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return Optional.empty();
        } finally {
            constructor.setAccessible(false);
        }
    }
}
