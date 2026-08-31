

package dev.demonzdevelopment.creepercli.platform.velocity;

import dev.demonzdevelopment.creepercli.monitor.LogStreamer;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;


final class Log4jBridge extends AbstractAppender {
    private final LogStreamer streamer;

    Log4jBridge(LogStreamer streamer) {
        super("CreeperCLIStream", null, null, true, Property.EMPTY_ARRAY);
        this.streamer = streamer;
    }

    @Override
    public void append(LogEvent event) {
        if (event.getMessage() == null) return;
        streamer.feed(event.getLevel().name(),
                event.getMessage().getFormattedMessage(),
                event.getTimeMillis());
    }
}
