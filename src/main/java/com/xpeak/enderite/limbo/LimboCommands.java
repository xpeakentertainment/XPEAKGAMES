package com.xpeak.enderite.limbo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.nightmare.NightmareData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /limbo entrar [jugador]   - entra al Limbo ahora (como si hubieras dormido)
 * /limbo salir [jugador]    - sale del Limbo (como si hubiera amanecido)
 * /limbo forzar [jugador]   - la proxima vez que duermas entraras seguro
 * /limbo crawler            - hace aparecer al Crawler ya
 * /limbo pesadilla [nivel]  - consulta o cambia el nivel de Pesadilla
 */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class LimboCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("limbo")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("entrar")
                        .executes(c -> enter(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(c -> enter(c, EntityArgument.getPlayer(c, "jugador")))))
                .then(Commands.literal("salir")
                        .executes(c -> leave(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(c -> leave(c, EntityArgument.getPlayer(c, "jugador")))))
                .then(Commands.literal("forzar")
                        .executes(c -> force(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(c -> force(c, EntityArgument.getPlayer(c, "jugador")))))
                .then(Commands.literal("crawler")
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            if (!LimboManager.isInLimbo(p)) {
                                c.getSource().sendFailure(Component.translatable("enderite.command.not_in_limbo"));
                                return 0;
                            }
                            LimboManager.spawnCrawlerNow(p, 12.0D, 20.0D);
                            return 1;
                        }))
                .then(Commands.literal("pesadilla")
                        .executes(c -> {
                            int lvl = NightmareData.get(c.getSource().getServer()).getLevel();
                            c.getSource().sendSuccess(() -> Component.translatable("enderite.command.nightmare", lvl), false);
                            return lvl;
                        })
                        .then(Commands.argument("nivel", IntegerArgumentType.integer(0, 10))
                                .executes(c -> {
                                    int lvl = IntegerArgumentType.getInteger(c, "nivel");
                                    NightmareData.get(c.getSource().getServer()).setLevel(lvl);
                                    int now = NightmareData.get(c.getSource().getServer()).getLevel();
                                    c.getSource().sendSuccess(() -> Component.translatable("enderite.command.nightmare_set", now), true);
                                    return now;
                                }))));
    }

    private static int enter(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException {
        if (LimboManager.isInLimbo(p)) {
            c.getSource().sendFailure(Component.translatable("enderite.command.already_in_limbo"));
            return 0;
        }
        BlockPos bed;
        boolean onBed = false;
        if (p.isSleeping() && p.getSleepingPos().isPresent()) {
            bed = p.getSleepingPos().get();
            onBed = true;
        } else if (p.level().dimension() == Level.OVERWORLD) {
            bed = p.blockPosition();
        } else if (p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD) {
            bed = p.getRespawnPosition();
            onBed = p.server.overworld().getBlockState(bed).getBlock() instanceof BedBlock;
        } else {
            bed = p.server.overworld().getSharedSpawnPos();
        }
        boolean ok = LimboManager.enterLimbo(p, bed, onBed);
        if (ok) c.getSource().sendSuccess(() -> Component.translatable("enderite.command.entered", p.getDisplayName()), true);
        return ok ? 1 : 0;
    }

    private static int leave(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        if (!LimboManager.isInLimbo(p)) {
            c.getSource().sendFailure(Component.translatable("enderite.command.not_in_limbo"));
            return 0;
        }
        LimboManager.leaveLimbo(p, true);
        return 1;
    }

    private static int force(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        LimboManager.forceNextSleep(p);
        c.getSource().sendSuccess(() -> Component.translatable("enderite.command.forced", p.getDisplayName()), true);
        return 1;
    }

    private LimboCommands() {}
}
