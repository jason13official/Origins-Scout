package io.github.jason13official.originsscout;

import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import io.github.edwinmindcraft.origins.api.registry.OriginsDynamicRegistries;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class OriginsScoutForge {

  // Advancement uses object identity for map keys in PlayerAdvancements — must reuse same instances
  private static final Map<ResourceLocation, Advancement> advancementCache = new ConcurrentHashMap<>();
  private static final Map<ResourceLocation, Integer> originCounts = new ConcurrentHashMap<>();

  private static final ResourceKey<OriginLayer> ORIGIN_LAYER_KEY =
      ResourceKey.create(OriginsDynamicRegistries.LAYERS_REGISTRY, ResourceLocation.fromNamespaceAndPath("origins", "origin"));

  public OriginsScoutForge(FMLJavaModLoadingContext context) {
    OriginsScout.preInit();

    MinecraftForge.EVENT_BUS.addListener((Consumer<PlayerLoggedInEvent>) event -> {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      MinecraftServer server = player.getServer();
      if (server == null) return;

      getPlayerOrigin(player).ifPresent(origin -> {
        ResourceLocation originLoc = origin.location();
        int newCount = originCounts.merge(originLoc, 1, Integer::sum);
        for (ServerPlayer peer : getPlayersWithOrigin(server, origin, null)) {
          grant(peer, originLoc.getPath(), newCount);
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
        for (ServerPlayer peer : getPlayersWithOrigin(server, origin, player)) {
          revoke(peer, originLoc.getPath(), oldCount);
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

  private Advancement buildAdvancement(String originPath, int n) {
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, originPath + "_" + n);
    return advancementCache.computeIfAbsent(id, loc ->
        Advancement.Builder.advancement()
            .addCriterion("complete", new ImpossibleTrigger.TriggerInstance())
            .build(loc)
    );
  }

  private void grant(ServerPlayer player, String originPath, int n) {
    player.getAdvancements().award(buildAdvancement(originPath, n), "complete");
  }

  private void revoke(ServerPlayer player, String originPath, int n) {
    player.getAdvancements().revoke(buildAdvancement(originPath, n), "complete");
  }

  @Deprecated @SuppressWarnings("all")
  public OriginsScoutForge() {
    this(FMLJavaModLoadingContext.get());
  }
}
