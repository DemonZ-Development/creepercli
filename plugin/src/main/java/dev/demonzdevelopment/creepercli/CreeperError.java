

package dev.demonzdevelopment.creepercli;

public final class CreeperError extends RuntimeException {
    private final String code;

    public CreeperError(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
