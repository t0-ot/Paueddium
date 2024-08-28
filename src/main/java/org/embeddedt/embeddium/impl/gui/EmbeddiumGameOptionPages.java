package org.embeddedt.embeddium.impl.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.level.ParticleStatus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForgeConfig;
import org.embeddedt.embeddium.api.options.structure.OptionFlag;
import org.embeddedt.embeddium.api.options.structure.OptionGroup;
import org.embeddedt.embeddium.api.options.structure.OptionImpact;
import org.embeddedt.embeddium.api.options.structure.OptionImpl;
import org.embeddedt.embeddium.api.options.structure.OptionPage;
import org.embeddedt.embeddium.impl.compat.modernui.MuiGuiScaleHook;
import org.embeddedt.embeddium.impl.compatibility.workarounds.Workarounds;
import org.embeddedt.embeddium.impl.gl.arena.staging.MappedStagingBuffer;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.api.options.binding.compat.VanillaBooleanOptionBinding;
import org.embeddedt.embeddium.api.options.control.ControlValueFormatter;
import org.embeddedt.embeddium.api.options.control.CyclingControl;
import org.embeddedt.embeddium.api.options.control.SliderControl;
import org.embeddedt.embeddium.api.options.control.TickBoxControl;
import org.embeddedt.embeddium.api.options.storage.MinecraftOptionsStorage;
import org.embeddedt.embeddium.api.options.structure.OptionStorage;
import org.embeddedt.embeddium.impl.gui.options.FullscreenResolutionHelper;
import org.embeddedt.embeddium.impl.gui.options.storage.EmbeddiumOptionsStorage;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.api.options.structure.StandardOptions;
import org.embeddedt.embeddium.impl.render.ShaderModBridge;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmbeddiumGameOptionPages {
    private static final EmbeddiumOptionsStorage sodiumOpts = new EmbeddiumOptionsStorage();
    private static final MinecraftOptionsStorage vanillaOpts = MinecraftOptionsStorage.INSTANCE;

    private static int computeMaxRangeForRenderDistance(@SuppressWarnings("SameParameterValue") int injectedRenderDistance) {
        if(vanillaOpts.getData().renderDistance().values() instanceof OptionInstance.IntRange range) {
            injectedRenderDistance = Math.max(injectedRenderDistance, range.maxInclusive());
        }
        return injectedRenderDistance;
    }

    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.RENDERING)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.RENDER_DISTANCE)
                        .setName(Component.translatable("options.renderDistance"))
                        .setTooltip(Component.translatable("sodium.options.view_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, computeMaxRangeForRenderDistance(32), 1, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding((options, value) -> options.renderDistance().set(value), options -> options.renderDistance().get())
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.SIMULATION_DISTANCE)
                        .setName(Component.translatable("options.simulationDistance"))
                        .setTooltip(Component.translatable("sodium.options.simulation_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 5, 32, 1, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding((options, value) -> options.simulationDistance().set(value), options -> options.simulationDistance().get())
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.BRIGHTNESS)
                        .setName(Component.translatable("options.gamma"))
                        .setTooltip(Component.translatable("sodium.options.brightness.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding((opts, value) -> opts.gamma().set(value * 0.01D), (opts) -> (int) (opts.gamma().get() / 0.01D))
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.WINDOW)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.GUI_SCALE)
                        .setName(Component.translatable("options.guiScale"))
                        .setTooltip(Component.translatable("sodium.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, MuiGuiScaleHook.getMaxGuiScale(), 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> {
                            opts.guiScale().set(value);

                            Minecraft client = Minecraft.getInstance();
                            client.resizeDisplay();
                        }, opts -> opts.guiScale().get())
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.FULLSCREEN)
                        .setName(Component.translatable("options.fullscreen"))
                        .setTooltip(Component.translatable("sodium.options.fullscreen.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.fullscreen().set(value);

                            Minecraft client = Minecraft.getInstance();
                            Window window = client.getWindow();

                            if (window != null && window.isFullscreen() != opts.fullscreen().get()) {
                                window.toggleFullScreen();

                                // The client might not be able to enter full-screen mode
                                opts.fullscreen().set(window.isFullscreen());
                            }
                        }, (opts) -> opts.fullscreen().get())
                        .build())
                .addConditionally(!FullscreenResolutionHelper.isFullscreenResAlreadyAdded(), FullscreenResolutionHelper::createFullScreenResolutionOption)
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.VSYNC)
                        .setName(Component.translatable("options.vsync"))
                        .setTooltip(Component.translatable("sodium.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(Minecraft.getInstance().options.enableVsync()))
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MAX_FRAMERATE)
                        .setName(Component.translatable("options.framerateLimit"))
                        .setTooltip(Component.translatable("sodium.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> {
                            opts.framerateLimit().set(value);
                            Minecraft.getInstance().getFramerateLimitTracker().setFramerateLimit(value);
                        }, opts -> opts.framerateLimit().get())
                        .build())
                .add(OptionImpl.createBuilder(InactivityFpsLimit.class, vanillaOpts)
                        .setId(StandardOptions.Option.INACTIVITY_FPS_LIMIT)
                        .setName(Component.translatable("options.inactivityFpsLimit"))
                        .setTooltip(Component.translatable("embeddium.options.inactivity_fps_limit.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, InactivityFpsLimit.class, Arrays.stream(InactivityFpsLimit.values()).map(InactivityFpsLimit::getKey).map(Component::translatable).toArray(Component[]::new)))
                        .setBinding((opts, value) -> {
                            opts.inactivityFpsLimit().set(value);
                        }, opts -> opts.inactivityFpsLimit().get())
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.INDICATORS)
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.VIEW_BOBBING)
                        .setName(Component.translatable("options.viewBobbing"))
                        .setTooltip(Component.translatable("sodium.options.view_bobbing.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(Minecraft.getInstance().options.bobView()))
                        .build())
                .add(OptionImpl.createBuilder(AttackIndicatorStatus.class, vanillaOpts)
                        .setId(StandardOptions.Option.ATTACK_INDICATOR)
                        .setName(Component.translatable("options.attackIndicator"))
                        .setTooltip(Component.translatable("sodium.options.attack_indicator.tooltip"))
                        .setControl(opts -> new CyclingControl<>(opts, AttackIndicatorStatus.class, new Component[] { Component.translatable("options.off"), Component.translatable("options.attack.crosshair"), Component.translatable("options.attack.hotbar") }))
                        .setBinding((opts, value) -> opts.attackIndicator().set(value), (opts) -> opts.attackIndicator().get())
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.AUTOSAVE_INDICATOR)
                        .setName(Component.translatable("options.autosaveIndicator"))
                        .setTooltip(Component.translatable("sodium.options.autosave_indicator.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.showAutosaveIndicator().set(value), opts -> opts.showAutosaveIndicator().get())
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.GENERAL, Component.translatable("stat.generalButton"), ImmutableList.copyOf(groups));
    }

    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.GRAPHICS)
                .add(OptionImpl.createBuilder(GraphicsStatus.class, vanillaOpts)
                        .setId(StandardOptions.Option.GRAPHICS_MODE)
                        .setName(Component.translatable("options.graphics"))
                        .setTooltip(Component.translatable("sodium.options.graphics_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsStatus.class, new Component[] { Component.translatable("options.graphics.fast"), Component.translatable("options.graphics.fancy"), Component.translatable("options.graphics.fabulous") }))
                        .setBinding(
                                (opts, value) -> opts.graphicsMode().set(value),
                                opts -> opts.graphicsMode().get())
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.DETAILS)
                .add(OptionImpl.createBuilder(CloudStatus.class, vanillaOpts)
                        .setId(StandardOptions.Option.CLOUDS)
                        .setName(Component.translatable("options.renderClouds"))
                        .setTooltip(Component.translatable("sodium.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, CloudStatus.class, new Component[] { Component.translatable("options.off"), Component.translatable("options.graphics.fast"), Component.translatable("options.graphics.fancy") }))
                        .setBinding((opts, value) -> {
                            opts.cloudStatus().set(value);

                            if (Minecraft.useShaderTransparency()) {
                                RenderTarget framebuffer = Minecraft.getInstance().levelRenderer.getCloudsTarget();
                                if (framebuffer != null) {
                                    framebuffer.clear();
                                }
                            }
                        }, opts -> opts.cloudStatus().get())
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(EmbeddiumOptions.GraphicsQuality.class, sodiumOpts)
                        .setId(StandardOptions.Option.WEATHER)
                        .setName(Component.translatable("soundCategory.weather"))
                        .setTooltip(Component.translatable("sodium.options.weather_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, EmbeddiumOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.weatherQuality = value, opts -> opts.quality.weatherQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(EmbeddiumOptions.GraphicsQuality.class, sodiumOpts)
                        .setId(StandardOptions.Option.LEAVES)
                        .setName(Component.translatable("sodium.options.leaves_quality.name"))
                        .setTooltip(Component.translatable("sodium.options.leaves_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, EmbeddiumOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.leavesQuality = value, opts -> opts.quality.leavesQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(ParticleStatus.class, vanillaOpts)
                        .setId(StandardOptions.Option.PARTICLES)
                        .setName(Component.translatable("options.particles"))
                        .setTooltip(Component.translatable("sodium.options.particle_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, ParticleStatus.class, new Component[] { Component.translatable("options.particles.all"), Component.translatable("options.particles.decreased"), Component.translatable("options.particles.minimal") }))
                        .setBinding((opts, value) -> opts.particles().set(value), (opts) -> opts.particles().get())
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.SMOOTH_LIGHT)
                        .setName(Component.translatable("options.ao"))
                        .setTooltip(Component.translatable("sodium.options.smooth_lighting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.ambientOcclusion().set(value), opts -> opts.ambientOcclusion().get())
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.BIOME_BLEND)
                        .setName(Component.translatable("options.biomeBlendRadius"))
                        .setTooltip(Component.translatable("sodium.options.biome_blend.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 7, 1, ControlValueFormatter.biomeBlend()))
                        .setBinding((opts, value) -> opts.biomeBlendRadius().set(value), opts -> opts.biomeBlendRadius().get())
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.ENTITY_DISTANCE)
                        .setName(Component.translatable("options.entityDistanceScaling"))
                        .setTooltip(Component.translatable("sodium.options.entity_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 50, 500, 25, ControlValueFormatter.percentage()))
                        .setBinding((opts, value) -> opts.entityDistanceScaling().set(value / 100.0), opts -> Math.round(opts.entityDistanceScaling().get().floatValue() * 100.0F))
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.ENTITY_SHADOWS)
                        .setName(Component.translatable("options.entityShadows"))
                        .setTooltip(Component.translatable("sodium.options.entity_shadows.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.entityShadows().set(value), opts -> opts.entityShadows().get())
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.VIGNETTE)
                        .setName(Component.translatable("sodium.options.vignette.name"))
                        .setTooltip(Component.translatable("sodium.options.vignette.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());


        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.MIPMAPS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MIPMAP_LEVEL)
                        .setName(Component.translatable("options.mipmapLevels"))
                        .setTooltip(Component.translatable("sodium.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding((opts, value) -> opts.mipmapLevels().set(value), opts -> opts.mipmapLevels().get())
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.SORTING)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.TRANSLUCENT_FACE_SORTING)
                        .setName(Component.translatable("sodium.options.translucent_face_sorting.name"))
                        .setTooltip(Component.translatable("sodium.options.translucent_face_sorting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.VARIES)
                        .setBinding((opts, value) -> opts.performance.useTranslucentFaceSorting = value, opts -> opts.performance.useTranslucentFaceSorting)
                        .setEnabled(!ShaderModBridge.isNvidiumEnabled())
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.LIGHTING)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.USE_QUAD_NORMALS_FOR_LIGHTING)
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.LOW)
                        .setBinding((opts, value) -> opts.quality.useQuadNormalsForShading = value, opts -> opts.quality.useQuadNormalsForShading)
                        .setEnabled(!NeoForgeConfig.CLIENT.experimentalForgeLightPipelineEnabled.get())
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.QUALITY, Component.translatable("sodium.options.pages.quality"), ImmutableList.copyOf(groups));
    }

    public static OptionPage performance() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.CHUNK_UPDATES)
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CHUNK_UPDATE_THREADS)
                        .setName(Component.translatable("sodium.options.chunk_update_threads.name"))
                        .setTooltip(Component.translatable("sodium.options.chunk_update_threads.tooltip"))
                        .setControl(o -> new SliderControl(o, 0, ChunkBuilder.getMaxThreadCount(), 1, ControlValueFormatter.quantityOrDisabled("threads", "Default")))
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.chunkBuilderThreads = value, opts -> opts.performance.chunkBuilderThreads)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.DEFFER_CHUNK_UPDATES)
                        .setName(Component.translatable("sodium.options.always_defer_chunk_updates.name"))
                        .setTooltip(Component.translatable("sodium.options.always_defer_chunk_updates.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.alwaysDeferChunkUpdates = value, opts -> opts.performance.alwaysDeferChunkUpdates)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build())
                .build()
        );

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.RENDERING_CULLING)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.BLOCK_FACE_CULLING)
                        .setName(Component.translatable("sodium.options.use_block_face_culling.name"))
                        .setTooltip(Component.translatable("sodium.options.use_block_face_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.performance.useBlockFaceCulling = value, opts -> opts.performance.useBlockFaceCulling)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.COMPACT_VERTEX_FORMAT)
                        .setName(Component.translatable("sodium.options.use_compact_vertex_format.name"))
                        .setTooltip(Component.translatable("sodium.options.use_compact_vertex_format.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setEnabled(!ShaderModBridge.areShadersEnabled())
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> {
                            opts.performance.useCompactVertexFormat = value;
                        }, opts -> opts.performance.useCompactVertexFormat)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.FOG_OCCLUSION)
                        .setName(Component.translatable("sodium.options.use_fog_occlusion.name"))
                        .setTooltip(Component.translatable("sodium.options.use_fog_occlusion.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.performance.useFogOcclusion = value, opts -> opts.performance.useFogOcclusion)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.ENTITY_CULLING)
                        .setName(Component.translatable("sodium.options.use_entity_culling.name"))
                        .setTooltip(Component.translatable("sodium.options.use_entity_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.performance.useEntityCulling = value, opts -> opts.performance.useEntityCulling)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.ANIMATE_VISIBLE_TEXTURES)
                        .setName(Component.translatable("sodium.options.animate_only_visible_textures.name"))
                        .setTooltip(Component.translatable("sodium.options.animate_only_visible_textures.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.animateOnlyVisibleTextures = value, opts -> opts.performance.animateOnlyVisibleTextures)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.NO_ERROR_CONTEXT)
                        .setName(Component.translatable("sodium.options.use_no_error_context.name"))
                        .setTooltip(Component.translatable("sodium.options.use_no_error_context.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.LOW)
                        .setBinding((opts, value) -> opts.performance.useNoErrorGLContext = value, opts -> opts.performance.useNoErrorGLContext)
                        .setEnabled(supportsNoErrorContext())
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.PERFORMANCE, Component.translatable("sodium.options.pages.performance"), ImmutableList.copyOf(groups));
    }

    private static boolean supportsNoErrorContext() {
        GLCapabilities capabilities = GL.getCapabilities();
        return (capabilities.OpenGL46 || capabilities.GL_KHR_no_error)
                && !Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED);
    }

    public static OptionPage advanced() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.CPU_SAVING)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.PERSISTENT_MAPPING)
                        .setName(Component.translatable("sodium.options.use_persistent_mapping.name"))
                        .setTooltip(Component.translatable("sodium.options.use_persistent_mapping.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setEnabled(MappedStagingBuffer.isSupported(RenderDevice.INSTANCE))
                        .setBinding((opts, value) -> opts.advanced.useAdvancedStagingBuffers = value, opts -> opts.advanced.useAdvancedStagingBuffers)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CPU_FRAMES_AHEAD)
                        .setName(Component.translatable("sodium.options.cpu_render_ahead_limit.name"))
                        .setTooltip(Component.translatable("sodium.options.cpu_render_ahead_limit.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 9, 1, ControlValueFormatter.translateVariable("sodium.options.cpu_render_ahead_limit.value")))
                        .setBinding((opts, value) -> opts.advanced.cpuRenderAheadLimit = value, opts -> opts.advanced.cpuRenderAheadLimit)
                        .build()
                )
                .build());

        return new OptionPage(StandardOptions.Pages.ADVANCED, Component.translatable("sodium.options.pages.advanced"), ImmutableList.copyOf(groups));
    }

    public static OptionStorage<Options> getVanillaOpts() {
        return vanillaOpts;
    }

    public static OptionStorage<EmbeddiumOptions> getSodiumOpts() {
        return sodiumOpts;
    }
}