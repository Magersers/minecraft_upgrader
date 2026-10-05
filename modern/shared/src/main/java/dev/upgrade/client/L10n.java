package dev.upgrade.client;

import net.minecraft.network.chat.Component;

/** Resolve on the client, using Minecraft's language and English fallback. */
final class L10n {
    private L10n() {}

    static String text(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}
