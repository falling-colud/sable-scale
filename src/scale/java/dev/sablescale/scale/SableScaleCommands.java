package dev.sablescale.scale;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * <pre>
 * /sablescale set   &lt;sub_level&gt; &lt;scale&gt;   - set a uniform scale (0.01 - 100) on the selected sub-levels
 * /sablescale reset &lt;sub_level&gt;           - back to scale 1
 * /sablescale get   &lt;sub_level&gt;           - print a sub-level's current scale
 * /sablescale looking &lt;scale&gt;             - scale the sub-level you are looking at
 * /sablescale looking                     - print the scale of the sub-level you are looking at
 * </pre>
 *
 * <p>{@code <sub_level>} is Sable's own selector argument, so {@code @e} (all), {@code @n} (nearest),
 * {@code @v} (viewed), {@code @i} (inside), {@code @l} (latest), name filters etc. all work here too;
 * {@code looking} is the no-typing shortcut for the {@code @v} case. The look-up mirrors Sable's own
 * {@code @v} selector: a 100-block block-pick, then mapping the hit position back to the sub-level whose
 * plot contains it.</p>
 */
public final class SableScaleCommands {

    private static final double LOOK_DISTANCE = 100.0;

    private static final SimpleCommandExceptionType ERROR_NOT_LOOKING = new SimpleCommandExceptionType(
        Component.translatable("commands.sablescale.fail.not_looking"));

    private SableScaleCommands() {}

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("sablescale")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("set")
                    .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                        .then(Commands.argument("scale", DoubleArgumentType.doubleArg(SubLevelScale.MIN_SCALE, SubLevelScale.MAX_SCALE))
                            .executes(ctx -> executeSet(ctx,
                                SubLevelArgumentType.getSubLevels(ctx, "sub_level"),
                                DoubleArgumentType.getDouble(ctx, "scale"))))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                        .executes(ctx -> executeSet(ctx, SubLevelArgumentType.getSubLevels(ctx, "sub_level"), 1.0))))
                .then(Commands.literal("get")
                    .then(Commands.argument("sub_level", SubLevelArgumentType.singleSubLevel())
                        .executes(ctx -> executeGet(ctx, SubLevelArgumentType.getSingleSubLevel(ctx, "sub_level")))))
                .then(Commands.literal("looking")
                    .executes(ctx -> executeGet(ctx, requireViewedSubLevel(ctx)))
                    .then(Commands.argument("scale", DoubleArgumentType.doubleArg(SubLevelScale.MIN_SCALE, SubLevelScale.MAX_SCALE))
                        .executes(ctx -> executeSet(ctx,
                            List.of(requireViewedSubLevel(ctx)),
                            DoubleArgumentType.getDouble(ctx, "scale"))))));
    }

    private static int executeSet(final CommandContext<CommandSourceStack> ctx,
                                  final Collection<ServerSubLevel> subLevels,
                                  final double scale) throws CommandSyntaxException {
        if (subLevels.isEmpty())
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        for (final ServerSubLevel subLevel : subLevels)
            SubLevelScale.apply(subLevel, scale);
        SableCommandHelper.sendSuccessDescribingSubLevels("commands.sablescale.set.success", ctx, subLevels, format(scale));
        return subLevels.size();
    }

    private static int executeGet(final CommandContext<CommandSourceStack> ctx, final ServerSubLevel subLevel) {
        final Vector3dc scale = subLevel.logicalPose().scale();
        final String text = SubLevelScale.isUniform(scale)
            ? format(scale.x())
            : format(scale.x()) + ", " + format(scale.y()) + ", " + format(scale.z());
        final Component message = SableCommandHelper.getResultComponentForSublevelCollection(
            "commands.sablescale.get.success", List.of(subLevel), 0, text);
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    /**
     * The sub-level the command source is looking at, resolved exactly like Sable's {@code @v} selector: pick a
     * block up to {@link #LOOK_DISTANCE} away (Sable's clip hooks make the ray hit sub-level blocks, returning
     * plot-space coordinates) and map the hit position to the owning sub-level.
     */
    private static ServerSubLevel requireViewedSubLevel(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Entity entity = ctx.getSource().getEntity();
        if (entity != null
            && entity.pick(LOOK_DISTANCE, 1.0F, true) instanceof BlockHitResult hit
            && hit.getType() == HitResult.Type.BLOCK
            && Sable.HELPER.getContaining(ctx.getSource().getLevel(), hit.getBlockPos()) instanceof ServerSubLevel subLevel
            && !subLevel.isRemoved()) {
            return subLevel;
        }
        throw ERROR_NOT_LOOKING.create();
    }

    private static String format(final double scale) {
        return new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(scale);
    }
}
