package io.github.jason13official.originsscout;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class OriginsScoutForge {

  private static IEventBus EVENT_BUS;

  public OriginsScoutForge(FMLJavaModLoadingContext context) {

    EVENT_BUS = context.getModEventBus();
    OriginsScout.preInit();
  }

  @Deprecated @SuppressWarnings("all")
  public OriginsScoutForge() {
    this(FMLJavaModLoadingContext.get());
  }
}