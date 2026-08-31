

package dev.demonzdevelopment.creepercli.platform;

import java.nio.file.Path;
import java.util.logging.Logger;


public interface Platform {

    
    String family();

    
    String software();

    
    String serverVersion();

    
    Logger logger();

    
    Path dataFolder();

    
    boolean tickLoopSupported();

    
    AutoCloseable startTickSampler(Runnable onTick);

    
    dev.demonzdevelopment.creepercli.platform.ConsoleBridge consoleBridge(dev.demonzdevelopment.creepercli.CreeperCLIPlugin core);
}
