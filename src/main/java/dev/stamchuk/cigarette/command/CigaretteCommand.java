package dev.stamchuk.cigarette.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.stamchuk.cigarette.effect.WithdrawalEffects;
import dev.stamchuk.cigarette.service.SmokingService;
import dev.stamchuk.cigarette.util.Msg;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class CigaretteCommand {

    private static final int MAX_GIVE = 64;

    private final SmokingService smokingService;
    private final Runnable reloadAction;

    public CigaretteCommand(SmokingService smokingService, Runnable reloadAction) {
        this.smokingService = smokingService;
        this.reloadAction = reloadAction;
    }

    public void register(Commands registrar) {
        registrar.register(build().build(), "Cigarette plugin commands", List.of("cig"));
    }

    private LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cigarette")
            .executes(this::help)
            .then(Commands.literal("help").executes(this::help))
            .then(Commands.literal("give")
                .requires(src -> src.getSender().hasPermission("cigarette.give"))
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> give(ctx, 1))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_GIVE))
                        .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
            .then(Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("cigarette.admin"))
                .executes(this::reload))
            .then(Commands.literal("addiction")
                .requires(src -> src.getSender().hasPermission("cigarette.admin"))
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(this::addiction)))
            .then(Commands.literal("reset")
                .requires(src -> src.getSender().hasPermission("cigarette.admin"))
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(this::reset)))
            .then(Commands.literal("stats")
                .requires(src -> src.getSender() instanceof Player && src.getSender().hasPermission("cigarette.use"))
                .executes(this::stats));
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage(Msg.of("<dark_gray>» <gray>сигареты"));
        if (sender.hasPermission("cigarette.use")) {
            sender.sendMessage(Msg.of("<dark_gray>  │ <white>/cigarette stats <gray>— своя статистика"));
        }
        if (sender.hasPermission("cigarette.give")) {
            sender.sendMessage(Msg.of("<dark_gray>  │ <white>/cigarette give \\<игрок> [кол-во]"));
        }
        if (sender.hasPermission("cigarette.admin")) {
            sender.sendMessage(Msg.of("<dark_gray>  │ <white>/cigarette addiction \\<игрок>"));
            sender.sendMessage(Msg.of("<dark_gray>  │ <white>/cigarette reset \\<игрок>"));
            sender.sendMessage(Msg.of("<dark_gray>  │ <white>/cigarette reload"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        var target = resolve(ctx);
        var leftovers = target.getInventory().addItem(smokingService.createCigarette(amount));
        var dropped = 0;
        for (var leftover : leftovers.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            dropped += leftover.getAmount();
        }

        var sender = ctx.getSource().getSender();
        sender.sendMessage(Msg.of("<dark_gray>» <gray>выдано <white><amount></white> шт. игроку <white><player>",
            Msg.number("amount", amount), Msg.text("player", target.getName())));
        if (dropped > 0) {
            sender.sendMessage(Msg.of("<dark_gray>  │ <gray>инвентарь полон, выброшено: <white><dropped>",
                Msg.number("dropped", dropped)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        reloadAction.run();
        ctx.getSource().getSender().sendMessage(Msg.of("<dark_gray>» <gray>конфиг перезагружен"));
        return Command.SINGLE_SUCCESS;
    }

    private int addiction(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var target = resolve(ctx);
        var uuid = target.getUniqueId();
        var data = smokingService.get(uuid);
        var sender = ctx.getSource().getSender();

        if (data == null) {
            sender.sendMessage(Msg.of("<dark_gray>» <gray>данные <white><player></white> ещё не загружены",
                Msg.text("player", target.getName())));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Msg.of("<dark_gray>» <gray>зависимость <white><player>",
            Msg.text("player", target.getName())));
        sendLine(sender, "выкурено", smokedValue(uuid, data.totalSmoked()));
        sendLine(sender, "уровень", smokingService.getAddictionLevel(uuid).displayName());
        sendLine(sender, "ломка", smokingService.isInWithdrawal(uuid) ? "да" : "нет");
        return Command.SINGLE_SUCCESS;
    }

    private int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var target = resolve(ctx);
        smokingService.resetAddiction(target.getUniqueId());
        WithdrawalEffects.clear(target);
        ctx.getSource().getSender().sendMessage(
            Msg.of("<dark_gray>» <gray>зависимость <white><player></white> сброшена",
                Msg.text("player", target.getName())));
        return Command.SINGLE_SUCCESS;
    }

    private int stats(CommandContext<CommandSourceStack> ctx) {
        var player = (Player) ctx.getSource().getSender();
        var data = smokingService.get(player.getUniqueId());

        if (data == null || data.totalSmoked() == 0) {
            player.sendMessage(Msg.of("<dark_gray>» <gray>ты ещё не курил"));
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Msg.of("<dark_gray>» <gray>статистика курения"));
        sendLine(player, "выкурено", smokedValue(player.getUniqueId(), data.totalSmoked()));
        sendLine(player, "зависимость", smokingService.getAddictionLevel(player.getUniqueId()).displayName());
        sendLine(player, "последняя затяжка", data.lastSmokeTime() > 0
            ? formatTimeAgo(System.currentTimeMillis() - data.lastSmokeTime())
            : "никогда");
        return Command.SINGLE_SUCCESS;
    }

    private Player resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
            .resolve(ctx.getSource()).getFirst();
    }

    private String smokedValue(UUID uuid, int totalSmoked) {
        var effective = smokingService.effectiveSmoked(uuid);
        if (effective == totalSmoked) return Integer.toString(totalSmoked);
        return effective + " (всего " + totalSmoked + ")";
    }

    private void sendLine(CommandSender sender, String label, String value) {
        sender.sendMessage(Msg.of("<dark_gray>  │ <gray><label>: <white><value>",
            Msg.text("label", label), Msg.text("value", value)));
    }

    private String formatTimeAgo(long millis) {
        var seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60) return seconds + " сек назад";
        var minutes = seconds / 60;
        if (minutes < 60) return minutes + " мин назад";
        var hours = minutes / 60;
        if (hours < 24) return hours + " ч назад";
        return (hours / 24) + " дн назад";
    }
}
