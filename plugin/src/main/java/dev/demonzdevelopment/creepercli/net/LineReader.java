package dev.demonzdevelopment.creepercli.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class LineReader {
    private final InputStream in;
    private final int maxBytes;

    public LineReader(InputStream in, int maxBytes) {
        this.in = in;
        this.maxBytes = maxBytes;
    }

    public String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        while (true) {
            int b = in.read();
            if (b == -1) {
                return buf.size() == 0 ? null : buf.toString(StandardCharsets.UTF_8);
            }
            if (b == '\n') {
                return buf.toString(StandardCharsets.UTF_8);
            }
            if (b == '\r') {
                continue;
            }
            buf.write(b);
            if (buf.size() > maxBytes) {
                throw new IOException("Frame exceeds max payload of " + maxBytes + " bytes");
            }
        }
    }
}
