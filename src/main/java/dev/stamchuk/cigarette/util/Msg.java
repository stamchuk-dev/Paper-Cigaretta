package dev.stamchuk.cigarette.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component of(String template) {
        return MM.deserialize(template);
    }

    public static Component of(String template, TagResolver... resolvers) {
        return MM.deserialize(template, resolvers);
    }

    public static TagResolver text(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    public static TagResolver number(String key, long value) {
        return Placeholder.unparsed(key, Long.toString(value));
    }
}
