package io.github.jason13official.originsscout;

import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import io.github.edwinmindcraft.origins.api.registry.OriginsDynamicRegistries;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class OriginsScoutForge {

  // Advancement uses object identity for map keys in PlayerAdvancements — must reuse same instances
  private static final Map<ResourceLocation, Advancement> advancementCache = new ConcurrentHashMap<>();
  private static final Map<ResourceLocation, Integer> originCounts = new ConcurrentHashMap<>();

  private static final ResourceKey<OriginLayer> ORIGIN_LAYER_KEY =
      ResourceKey.create(OriginsDynamicRegistries.LAYERS_REGISTRY,
          ResourceLocation.fromNamespaceAndPath("origins", "origin"));

  // Resolved once by type — avoids SRG/Mojmap name fragility across environments
  @Nullable
  private static final Field ADVANCEMENT_LIST_FIELD = findAdvancementListField();

  private static Field findAdvancementListField() {
    for (Field field : ServerAdvancementManager.class.getDeclaredFields()) {
      if (field.getType() == AdvancementList.class) {
        field.setAccessible(true);
        return field;
      }
    }
    Constants.LOG.warn("Could not find AdvancementList field in ServerAdvancementManager — origins:advancement conditions will not see scout advancements");
    return null;
  }

  public OriginsScoutForge(FMLJavaModLoadingContext context) {
    OriginsScout.preInit();

    // Caches must be cleared each server start — ServerAdvancementManager rebuilds its AdvancementList
    // from datapacks, so our injected advancements are gone and cached Advancement objects become stale
    MinecraftForge.EVENT_BUS.addListener((Consumer<ServerStartingEvent>) event -> {
      advancementCache.clear();
      originCounts.clear();
    });

    MinecraftForge.EVENT_BUS.addListener((Consumer<PlayerLoggedInEvent>) event -> {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      MinecraftServer server = player.getServer();
      if (server == null) return;

      getPlayerOrigin(player).ifPresent(origin -> {
        ResourceLocation originLoc = origin.location();
        int newCount = originCounts.merge(originLoc, 1, Integer::sum);
        for (ServerPlayer peer : getPlayersWithOrigin(server, origin, null)) {
          grant(peer, server, originLoc.getPath(), newCount);
        }
      });
    });

    MinecraftForge.EVENT_BUS.addListener((Consumer<PlayerLoggedOutEvent>) event -> {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      MinecraftServer server = player.getServer();
      if (server == null) return;

      getPlayerOrigin(player).ifPresent(origin -> {
        ResourceLocation originLoc = origin.location();
        int oldCount = originCounts.getOrDefault(originLoc, 1);
        int newCount = Math.max(0, oldCount - 1);
        if (newCount == 0) {
          originCounts.remove(originLoc);
        } else {
          originCounts.put(originLoc, newCount);
        }

        for (int i = 1; i <= oldCount; i++) {
          revoke(player, server, originLoc.getPath(), i);
        }

        for (ServerPlayer peer : getPlayersWithOrigin(server, origin, player)) {
          revoke(peer, server, originLoc.getPath(), oldCount);
        }
      });
    });
  }

  private Optional<ResourceKey<Origin>> getPlayerOrigin(ServerPlayer player) {
    return IOriginContainer.get(player)
        .map(c -> c.getOrigin(ORIGIN_LAYER_KEY))
        .filter(Objects::nonNull);
  }

  private List<ServerPlayer> getPlayersWithOrigin(MinecraftServer server, ResourceKey<Origin> target, ServerPlayer exclude) {
    return server.getPlayerList().getPlayers().stream()
        .filter(p -> !p.equals(exclude))
        .filter(p -> IOriginContainer.get(p)
            .map(c -> Objects.equals(c.getOrigin(ORIGIN_LAYER_KEY), target))
            .orElse(false))
        .collect(Collectors.toList());
  }

  private Advancement getOrCreateAdvancement(MinecraftServer server, String originPath, int n) {
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, Constants.MOD_ID + "/" + originPath + "_" + n);
    return advancementCache.computeIfAbsent(id, loc -> {
      Advancement.Builder builder = Advancement.Builder.advancement()
          .addCriterion("trigger", new ImpossibleTrigger.TriggerInstance());

      // Inject into ServerAdvancementManager so origins:advancement condition can look it up
      AdvancementList list = getAdvancementList(server.getAdvancements());
      if (list != null) {
        Advancement existing = list.get(loc);
        if (existing != null) return existing;
        list.add(Collections.singletonMap(loc, builder));
        Advancement registered = list.get(loc);
        if (registered != null) return registered;
      }

      // Fallback: award/revoke still work within a session but origins:advancement can't see it
      return builder.build(loc);
    });
  }

  @Nullable
  private static AdvancementList getAdvancementList(ServerAdvancementManager manager) {
    if (ADVANCEMENT_LIST_FIELD == null) return null;
    try {
      return (AdvancementList) ADVANCEMENT_LIST_FIELD.get(manager);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  private void grant(ServerPlayer player, MinecraftServer server, String originPath, int n) {
    player.getAdvancements().award(getOrCreateAdvancement(server, originPath, n), "trigger");
  }

  private void revoke(ServerPlayer player, MinecraftServer server, String originPath, int n) {
    player.getAdvancements().revoke(getOrCreateAdvancement(server, originPath, n), "trigger");
  }

  @Deprecated @SuppressWarnings("all")
  public OriginsScoutForge() {
    this(FMLJavaModLoadingContext.get());
  }
}
