

package dev.demonzdevelopment.creepercli.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class LineReader {
    private final InputStream in;
    private final int maxBytes;

    public LineReader(InputStream in, int maxBytes) {
        this.in = in;
        this.maxBytes = maxBytes;
    }

    public String readLine() throws IOException {
        return readLine(null, 0);
    }

    // Recompute the remaining time for partial frames as well as silent reads.
    public String readLine(Socket socket, long deadlineNanos) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        while (true) {
            if (socket != null) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException("Authentication deadline exceeded");
                int timeoutMillis = (int) Math.min(Integer.MAX_VALUE,
                        Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                if (socket.getSoTimeout() != timeoutMillis) socket.setSoTimeout(timeoutMillis);
            }
            int b = in.read();
            if (socket != null && deadlineNanos - System.nanoTime() <= 0) {
                throw new SocketTimeoutException("Authentication deadline exceeded");
            }
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
