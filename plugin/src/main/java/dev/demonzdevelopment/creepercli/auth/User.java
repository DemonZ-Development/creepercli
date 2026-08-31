

package dev.demonzdevelopment.creepercli.auth;

public record User(String username, String passwordHash, String totpSecret) {
}
