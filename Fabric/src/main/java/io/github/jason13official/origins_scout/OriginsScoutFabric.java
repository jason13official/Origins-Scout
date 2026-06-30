package io.github.jason13official.origins_scout;

import net.fabricmc.api.ModInitializer;

public class OriginsScoutFabric implements ModInitializer {

  @Override
  public void onInitialize() {

    OriginsScout.preInit();
  }
}
