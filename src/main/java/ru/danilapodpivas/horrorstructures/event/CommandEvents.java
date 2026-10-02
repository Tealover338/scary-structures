package ru.danilapodpivas.horrorstructures.event;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.tree.CommandNode;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.structure.SelectionManager;
import ru.danilapodpivas.horrorstructures.world.StructureDefinition;
import ru.danilapodpivas.horrorstructures.world.StructureWorldData;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = HorrorStructures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandEvents {
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("savestructure").requires(source -> source.hasPermission(2)).then(Commands.argument("name", StringArgumentType.word()).executes(CommandEvents::saveStructure)));
        event.getDispatcher().register(Commands.literal("placestructure").requires(source -> source.hasPermission(2)).then(Commands.argument("name", StringArgumentType.word()).executes(CommandEvents::placeStructure)));
        CommandNode<CommandSourceStack> locate = event.getDispatcher().getRoot().getChild("locate");
        if (locate != null) {
            locate.addChild(Commands.literal("horror_structure")
                    .then(Commands.argument("name", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    StructureDefinition.ALL.stream().map(StructureDefinition::name).collect(Collectors.toList()), builder))
                            .executes(CommandEvents::locateGeneratedStructure)).build());
            CommandNode<CommandSourceStack> vanillaStructure = locate.getChild("structure");
            if (vanillaStructure != null) {
                for (StructureDefinition definition : StructureDefinition.ALL) {
                    vanillaStructure.addChild(Commands.literal(HorrorStructures.MOD_ID + ":" + definition.name())
                            .executes(context -> locateGeneratedStructure(context.getSource(), definition.name())).build());
                }
            }
        }
    }
    private static int saveStructure(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception exception) { source.sendFailure(Component.translatable("message.horror_structures.player_only")); return 0; }
        SelectionManager.Selection selection = SelectionManager.get(player);
        if (selection == null || !selection.isComplete()) { source.sendFailure(Component.translatable("message.horror_structures.incomplete_selection")); return 0; }
        String name = StringArgumentType.getString(context, "name");
        ResourceLocation id = ResourceLocation.tryBuild(HorrorStructures.MOD_ID, name);
        if (id == null) { source.sendFailure(Component.translatable("message.horror_structures.invalid_name", name)); return 0; }
        BlockPos min = selection.minimum();
        BlockPos max = selection.maximum();
        Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (volume > 50_000_000L) { source.sendFailure(Component.translatable("message.horror_structures.too_large", volume)); return 0; }
        ServerLevel level = player.serverLevel();
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.getOrCreate(id);
        template.fillFromWorld(level, min, size, true, Blocks.STRUCTURE_VOID);
        if (!manager.save(id)) { source.sendFailure(Component.translatable("message.horror_structures.save_failed", name)); return 0; }
        source.sendSuccess(() -> Component.translatable("message.horror_structures.saved", name, size.getX(), size.getY(), size.getZ(), volume), true);
        return 1;
    }
    private static int placeStructure(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception exception) { source.sendFailure(Component.translatable("message.horror_structures.player_only")); return 0; }
        String name = StringArgumentType.getString(context, "name");
        ResourceLocation id = ResourceLocation.tryBuild(HorrorStructures.MOD_ID, name);
        if (id == null) { source.sendFailure(Component.translatable("message.horror_structures.invalid_name", name)); return 0; }
        ServerLevel level = player.serverLevel();
        StructureTemplate template = level.getStructureManager().get(id).orElse(null);
        if (template == null) { source.sendFailure(Component.translatable("message.horror_structures.not_found", name)); return 0; }
        BlockPos origin = BlockPos.containing(source.getPosition());
        boolean placed = template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        if (!placed) { source.sendFailure(Component.translatable("message.horror_structures.place_failed", name)); return 0; }
        source.sendSuccess(() -> Component.translatable("message.horror_structures.placed", name, origin.getX(), origin.getY(), origin.getZ()), true);
        return 1;
    }

    private static int locateGeneratedStructure(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        return locateGeneratedStructure(context.getSource(), name);
    }

    private static int locateGeneratedStructure(CommandSourceStack source, String name) {
        StructureDefinition definition = StructureDefinition.byName(name);
        if (definition == null) {
            source.sendFailure(Component.translatable("message.horror_structures.not_found", name));
            return 0;
        }
        ServerLevel level = source.getServer().overworld();
        StructureWorldData.Placement placement = StructureWorldData.get(level).getPlacement(name);
        if (placement == null) {
            source.sendFailure(Component.translatable("message.horror_structures.not_found", name));
            return 0;
        }
        BlockPos target = placement.locatePos();
        int distance = Mth.floor(Math.sqrt(source.getPosition().distanceToSqr(target.getX(), source.getPosition().y, target.getZ())));
        source.sendSuccess(() -> Component.translatable("message.horror_structures.located", name, target.getX(), target.getY(), target.getZ(), distance), false);
        return distance;
    }
    private CommandEvents() {}
}
