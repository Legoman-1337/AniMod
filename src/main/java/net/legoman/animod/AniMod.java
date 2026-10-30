package net.legoman.animod;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.legoman.animod.story.StoryAtmosphereEvents;
import net.legoman.animod.story.StoryManager;
import net.legoman.animod.story.StoryState;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.GeckoLib;

import java.util.*;

import static net.legoman.animod.AnyaAIPrompts.normalize;

public class AniMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("animod");

    public static MinecraftServer currentServer;
    public static final ResourceLocation TELEPORT_PROMPT_PACKET = new ResourceLocation("animod", "teleport_prompt");
    public static final ResourceLocation TELEPORT_CONFIRM_PACKET = new ResourceLocation("animod", "teleport_confirm");
    public static final ResourceLocation STORY_CUTSCENE_PACKET = new ResourceLocation("animod", "story_cutscene");
    public static final ResourceLocation INSOMNIA_SKY_PACKET = new ResourceLocation("animod", "insomnia_sky");

    private static final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private static final ThreadLocal<SummonedAnya> summonedAnya = new ThreadLocal<>();

    private record PendingTeleport(ResourceKey<Level> dimension, double x, double y, double z, long expiresAtTick) {}
    private record TargetLocation(ResourceKey<Level> dimension, double x, double y, double z) {}
    private record SummonedAnya(UUID uuid, Vec3 position) {}

    public static int getKarma() {
        return KarmaManager.get();
    }

    private static long lastComplimentMinecraftDay = Long.MIN_VALUE;
    private static long lastPeacefulHitKarmaTime = Long.MIN_VALUE;
    private static final long PEACEFUL_HIT_COOLDOWN_MS = 5L * 60L * 1000L;

    @Override
    public void onInitialize() {
        ModSounds.register();

        FabricDefaultAttributeRegistry.register(AnyaEntity.ANYA, Mob.createMobAttributes());
        GeckoLib.initialize();

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AnyaEntity anya) {
                AniSaveData.getServerState(level).updateAnyaLocation(anya);
            } else if (entity instanceof ServerPlayer player) {
                AniSaveData data = AniSaveData.getServerState(level);
                StoryManager.syncInsomniaSky(player, data.insomniaNightActive);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof AnyaEntity anya) {
                AniSaveData data = AniSaveData.getServerState(level);
                data.updateAnyaLocation(anya);
                data.setDirty();
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(TELEPORT_CONFIRM_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    boolean accepted = buf.readBoolean();
                    server.execute(() -> finishTeleportConfirmation(player, accepted));
                });

        // Блокировка кровати и 3-секундный всплеск кошмара
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer sp) {
                if (StoryManager.isBurialActive(sp.getUUID())) {
                    return InteractionResult.FAIL;
                }
                BlockState state = world.getBlockState(hitResult.getBlockPos());
                if (state.getBlock() instanceof BedBlock && StoryManager.isInsomniaNightActive(world)) {
                    StoryManager.triggerBedGlitch(sp);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer sp && StoryManager.isBurialActive(sp.getUUID())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            StoryManager.onPlayerRespawn(newPlayer);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            AniSaveData data = AniSaveData.getServerState(server.overworld());

            if (!data.migrated) {
                if (server.overworld().getLevelData().getGameTime() == 0L) {
                    data.karma = 50;
                } else {
                    data.karma = KarmaManager.loadLegacyKarma();
                }
                data.migrated = true;
                data.setDirty();
            }

            KarmaManager.setFromSave(data.karma);
            AnyaAI.loadMemory(data.memory);
            LOGGER.info("AniMod: Состояние мира загружено (Карма: {}, Память: {} фактов)", data.karma, data.memory.size());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            pendingTeleports.clear();
            KarmaManager.setFromSave(50);
            AnyaAI.loadMemory(new ArrayList<>());
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String rawText = message.signedBody().content();
            String playerName = sender.getName().getString();

            if (playerName.equalsIgnoreCase("legoman")) {
                if (rawText.equalsIgnoreCase("!карма плюс")) {
                    KarmaManager.set(Math.min(100, KarmaManager.get() + 10));
                    sender.sendSystemMessage(Component.literal("§a[Система] Карма Ани повышена! Текущая: " + KarmaManager.get()));
                    drawKarmaActionBar(sender);
                    return;
                }
                if (rawText.equalsIgnoreCase("!карма минус")) {
                    KarmaManager.set(Math.max(0, KarmaManager.get() - 15));
                    sender.sendSystemMessage(Component.literal("§c[Система] Карма Ани упала! Текущая: " + KarmaManager.get()));
                    drawKarmaActionBar(sender);
                    return;
                }
            }

            String lowerRaw = rawText.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if ((lowerRaw.contains("пожалуйст") || lowerRaw.contains("можно")) &&
                    (lowerRaw.contains("подар") || lowerRaw.contains("прими"))) {

                AABB area = sender.getBoundingBox().inflate(32.0, 16.0, 32.0);
                List<AnyaEntity> anyas = sender.serverLevel().getEntitiesOfClass(AnyaEntity.class, area);
                for (AnyaEntity anya : anyas) {
                    anya.gifts.resetCooldowns();
                }
                sender.sendSystemMessage(Component.literal("§7[Система] Дневные лимиты сброшены. Аня снова готова принять дары."));
            }

            String norm = normalize(rawText).replaceAll("[^а-яa-z0-9]", "");
            if (norm.isEmpty()) return;
            applyMessageKarma(sender, rawText);
            AnyaAI.processAndSend(sender, rawText);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("ani")
                    .then(Commands.literal("add").then(Commands.literal("quest")
                            .then(Commands.argument("tier", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                int tier = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "tier");
                                if (player == null) {
                                    context.getSource().sendFailure(Component.literal("Команда доступна только игроку в мире."));
                                    return 0;
                                }
                                if (QuestManager.offerTier(player, tier)) return 1;
                                context.getSource().sendFailure(Component.literal("Не удалось выдать квест: возможно, уже есть активное задание."));
                                return 0;
                            }))))
                    .then(Commands.literal("quest").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player != null) QuestManager.sendStatus(player);
                        return 1;
                    }))
                    .then(Commands.literal("cleanQuest").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player != null && QuestManager.cleanQuest(player)) {
                            context.getSource().sendSuccess(() -> Component.literal("§d[Аня] Текущее задание отменено; предложу новое позже."), false);
                        } else {
                            context.getSource().sendFailure(Component.literal("Сейчас нет активного задания для отмены."));
                        }
                        return 1;
                    }))
                    .then(Commands.literal("traits").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player != null) {
                            String trait = getTrait();
                            String color = getTraitColor();
                            player.sendSystemMessage(Component.literal("§7[Система] Состояние Ани: " + color + trait + " §7(Карма: " + KarmaManager.get() + ")"));
                        }
                        return 1;
                    }))
                    .then(Commands.literal("karma").then(Commands.literal("set").then(Commands.argument("number", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100)).executes(context -> {
                        int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "number");
                        ServerPlayer player = context.getSource().getPlayer();
                        KarmaManager.set(value);
                        if (player != null) drawKarmaActionBar(player);
                        context.getSource().sendSuccess(() -> Component.literal("§a[Система] Карма установлена на: " + KarmaManager.get()), false);
                        return 1;
                    }))))
                    .then(Commands.literal("gifts").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player != null) {
                            player.sendSystemMessage(Component.literal("§d--- ИНВЕНТАРИ АНИ ---"));
                            player.sendSystemMessage(Component.literal("§eОбычный ПКМ: §fОсновной инвентарь (вещи). Предметы внутри замедляют полет Ани."));
                            player.sendSystemMessage(Component.literal("§eShift + ПКМ: §fПодарки. Никак не замедляют Аню."));
                            player.sendSystemMessage(Component.literal(" "));
                            player.sendSystemMessage(Component.literal("§dИдеальный Баланс по сложностям:"));
                            player.sendSystemMessage(Component.literal("§bМирный: §fИнвентари отключены."));
                            player.sendSystemMessage(Component.literal("§bЛегкий/Нормальный: §fЦветы/Средние +2. Ценные до +8. Штрафы: от -2 до -3."));
                            player.sendSystemMessage(Component.literal("§bСложный: §fЦветы/Средние +1. Ценные слабее. Штрафы: от -3 до -5."));
                            player.sendSystemMessage(Component.literal("§bХардкор: §fЦветы/Средние +1. Ценные -30%. Штрафы: суровые (до -8)."));
                            player.sendSystemMessage(Component.literal(" "));
                            player.sendSystemMessage(Component.literal("§aКарма за подарки начисляется за 1 шт."));
                            player.sendSystemMessage(Component.literal("§aЛимиты в день: 4 Средних, 1 Дорогой. Цветы - без лимита (но не больше 4 разных за раз!)."));
                            player.sendSystemMessage(Component.literal("§eЛайфхак: Напишите в чат 'Аня, пожалуйста, можно я подарю подарок?', чтобы сбросить дневные лимиты."));
                        }
                        return 1;
                    }))
                    .then(Commands.literal("tp")
                            .then(Commands.literal("call").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null && player.serverLevel() != null) {
                                    List<AnyaEntity> anyas = player.serverLevel().getEntitiesOfClass(AnyaEntity.class, player.getBoundingBox().inflate(500.0));
                                    if (!anyas.isEmpty()) {
                                        anyas.get(0).teleportTo(player.getX(), player.getY(), player.getZ());
                                        context.getSource().sendSuccess(() -> Component.literal("§d[Система] Аня телепортирована к вам."), false);
                                    } else {
                                        context.getSource().sendSuccess(() -> Component.literal("§c[Система] Аня не найдена в радиусе 500 блоков."), false);
                                    }
                                }
                                return 1;
                            }))
                            .then(Commands.literal("to").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null && player.serverLevel() != null) {
                                    beginTeleportToAnya(player);
                                }
                                return 1;
                            })))
                    .then(Commands.literal("story")
                            .then(Commands.literal("status").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null) {
                                    AniSaveData data = AniSaveData.getServerState(player.serverLevel());
                                    StoryState st = data.getStoryState();
                                    player.sendSystemMessage(Component.literal("§d--- СЮЖЕТ АНИ ---"));
                                    player.sendSystemMessage(Component.literal("§eДень сюжета: §f" + data.storyDay));
                                    player.sendSystemMessage(Component.literal("§eСостояние: §b" + st.name() + " §7(" + st.getDescription() + ")"));
                                    player.sendSystemMessage(Component.literal("§eЛимит HP игрока: §c" + data.anyaEventHp));
                                }
                                return 1;
                            }))
                            .then(Commands.literal("list").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null) {
                                    player.sendSystemMessage(Component.literal("§d--- СПИСОК СТАДИЙ СЮЖЕТА ---"));
                                    for (StoryState s : StoryState.values()) {
                                        player.sendSystemMessage(Component.literal("§b" + s.name() + " §7- " + s.getDescription()));
                                    }
                                }
                                return 1;
                            }))
                            .then(Commands.literal("setDay").then(Commands.argument("day", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 30)).executes(context -> {
                                int day = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "day");
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null) {
                                    AniSaveData data = AniSaveData.getServerState(player.serverLevel());
                                    data.storyDay = day;
                                    StoryManager.applyDayLogic(player.serverLevel().getServer(), data, day);
                                    player.sendSystemMessage(Component.literal("§a[Сюжет] Установлен день: " + day + " (Стадия: " + data.getStoryState().name() + ", HP: " + data.anyaEventHp + ")"));
                                }
                                return 1;
                            })))
                            .then(Commands.literal("setState").then(Commands.argument("state", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                                String stateName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "state");
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null) {
                                    StoryState st = StoryState.fromName(stateName);
                                    if (st != null) {
                                        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
                                        StoryState oldState = data.getStoryState();
                                        data.setStoryState(st);
                                        if (st == StoryState.ANYA_BODY_EVENT) data.anyaEventHp = 10.0;
                                        else if (st == StoryState.ANYA_IS_CRYING) data.anyaEventHp = 16.0;
                                        else if (st == StoryState.ANYA_IS_ANGRY) data.anyaEventHp = 14.0;
                                        else data.anyaEventHp = 20.0;
                                        data.setDirty();

                                        StoryManager.triggerStateTransition(player.serverLevel().getServer(), data, oldState, st);
                                        player.sendSystemMessage(Component.literal("§a[Сюжет] Состояние переключено на: §b" + st.name() + " §7(HP: " + data.anyaEventHp + ")"));
                                    } else {
                                        player.sendSystemMessage(Component.literal("§c[Сюжет] Неизвестное состояние. Введи /ani story list"));
                                    }
                                }
                                return 1;
                            })))
                            .then(Commands.literal("event").then(Commands.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                                String type = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "type");
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null) {
                                    switch (type.toLowerCase()) {
                                        case "note" -> StoryAtmosphereEvents.giveRealTimeNote(player);
                                        case "torches" -> StoryAtmosphereEvents.extinguishTorches(player);
                                        case "night" -> StoryAtmosphereEvents.suddenNight(player);
                                        case "leave" -> StoryAtmosphereEvents.fakePlayerLeave(player);
                                        default -> player.sendSystemMessage(Component.literal("§cДоступные ивенты: note, torches, night, leave"));
                                    }
                                }
                                return 1;
                            })))));
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && player.getName().getString().equalsIgnoreCase("legoman")
                    && (entity instanceof Animal || entity instanceof Villager)) {
                long now = System.currentTimeMillis();
                if (lastPeacefulHitKarmaTime == Long.MIN_VALUE || now - lastPeacefulHitKarmaTime >= PEACEFUL_HIT_COOLDOWN_MS) {
                    KarmaManager.set(Math.max(0, KarmaManager.get() - 3));
                    lastPeacefulHitKarmaTime = now;
                    player.sendSystemMessage(Component.literal("§c[Аня] Зачем ты бьешь мирных существ?! (-3 кармы)"));
                    AnyaAI.addMemoryFact("Игрок ударил мирное существо (" + entity.getType().getDescription().getString() + ")", null);
                }
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StoryManager.tick(server);

            // Бесшумный авто-спавн Ани перед игроком
            if (server.getTickCount() % 40 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase("legoman")) {
                        ServerLevel level = player.serverLevel();
                        List<AnyaEntity> nearbyAnyas = level.getEntitiesOfClass(AnyaEntity.class, player.getBoundingBox().inflate(128.0), AnyaEntity::isAlive);
                        if (nearbyAnyas.isEmpty()) {
                            boolean existsAnywhere = false;
                            for (ServerLevel sl : server.getAllLevels()) {
                                for (Entity e : sl.getAllEntities()) {
                                    if (e instanceof AnyaEntity a && a.isAlive()) {
                                        existsAnywhere = true;
                                        break;
                                    }
                                }
                                if (existsAnywhere) break;
                            }
                            if (!existsAnywhere) {
                                AnyaEntity anya = new AnyaEntity(AnyaEntity.ANYA, level);
                                Vec3 look = player.getLookAngle();
                                double spawnX = player.getX() + look.x * 2.5;
                                double spawnY = player.getEyeY() - 0.2;
                                double spawnZ = player.getZ() + look.z * 2.5;
                                anya.moveTo(spawnX, spawnY, spawnZ, player.getYRot() + 180.0F, 0.0F);
                                level.addFreshEntity(anya);
                                AniSaveData.getServerState(level).setSoleAnya(anya.getUUID(), level.dimension().location().toString(), spawnX, spawnY, spawnZ);
                            }
                        }
                    }
                }
            }

            if (server.getTickCount() % 20 == 0) {
                QuestManager.tick(server);
            }
            if (server.getTickCount() % 10 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase("legoman")) drawKarmaActionBar(player);
                }
            }
            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase("legoman")) AnyaAI.updateContextCache(player, true);
                }
            }
        });

        LOGGER.info("AniMod запущен");
    }

    private static void beginTeleportToAnya(ServerPlayer player) {
        ServerLevel playerLevel = player.serverLevel();
        MinecraftServer server = playerLevel.getServer();

        TargetLocation target = findAnyaLocation(player);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§c[Система] Аня не найдена. Призовите её с помощью /summon animod:anya"));
            return;
        }

        if (!target.dimension().equals(playerLevel.dimension())) {
            player.sendSystemMessage(Component.literal("§c[Система] Аня находится в другом измерении (" + target.dimension().location() + ")."));
            return;
        }

        double dx = target.x() - player.getX();
        double dy = target.y() - player.getY();
        double dz = target.z() - player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance <= 2000.0) {
            teleportPlayerToCoords(player, playerLevel, target.x(), target.y(), target.z());
            return;
        }

        if (!ServerPlayNetworking.canSend(player, TELEPORT_PROMPT_PACKET)) {
            player.sendSystemMessage(Component.literal("§c[Система] Для подтверждения дальней телепортации необходим клиент AniMod."));
            return;
        }

        pendingTeleports.put(player.getUUID(),
                new PendingTeleport(target.dimension(), target.x(), target.y(), target.z(), server.getTickCount() + 1200L));

        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeDouble(distance);
        ServerPlayNetworking.send(player, TELEPORT_PROMPT_PACKET, packet);
    }

    private static void finishTeleportConfirmation(ServerPlayer player, boolean accepted) {
        PendingTeleport pending = pendingTeleports.remove(player.getUUID());
        if (!accepted || pending == null) return;

        MinecraftServer server = player.serverLevel().getServer();
        if (server.getTickCount() > pending.expiresAtTick()) {
            player.sendSystemMessage(Component.literal("§c[Система] Время подтверждения телепортации истекло. Введи команду ещё раз."));
            return;
        }

        ServerLevel targetLevel = server.getLevel(pending.dimension());
        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal("§c[Система] Измерение недоступно."));
            return;
        }

        teleportPlayerToCoords(player, targetLevel, pending.x(), pending.y(), pending.z());
    }

    private static void teleportPlayerToCoords(ServerPlayer player, ServerLevel targetLevel, double x, double y, double z) {
        AABB box = new AABB(x, y, z, x, y, z).inflate(16.0);
        List<AnyaEntity> nearby = targetLevel.getEntitiesOfClass(AnyaEntity.class, box, AnyaEntity::isAlive);
        if (!nearby.isEmpty()) {
            AnyaEntity anya = nearby.get(0);
            player.teleportTo(targetLevel, anya.getX(), anya.getY(), anya.getZ(), player.getYRot(), player.getXRot());
            AniSaveData.getServerState(targetLevel).updateAnyaLocation(anya);
        } else {
            player.teleportTo(targetLevel, x, y, z, player.getYRot(), player.getXRot());
        }
        player.sendSystemMessage(Component.literal("§d[Система] Вы телепортированы к Ане."));
    }

    private static TargetLocation findAnyaLocation(ServerPlayer player) {
        ServerLevel playerLevel = player.serverLevel();
        MinecraftServer server = playerLevel.getServer();

        for (Entity entity : playerLevel.getAllEntities()) {
            if (entity instanceof AnyaEntity anya && anya.isAlive()) {
                AniSaveData.getServerState(playerLevel).updateAnyaLocation(anya);
                return new TargetLocation(playerLevel.dimension(), anya.getX(), anya.getY(), anya.getZ());
            }
        }

        for (ServerLevel otherLevel : server.getAllLevels()) {
            if (otherLevel == playerLevel) continue;
            for (Entity entity : otherLevel.getAllEntities()) {
                if (entity instanceof AnyaEntity anya && anya.isAlive()) {
                    AniSaveData.getServerState(server.overworld()).updateAnyaLocation(anya);
                    return new TargetLocation(otherLevel.dimension(), anya.getX(), anya.getY(), anya.getZ());
                }
            }
        }

        AniSaveData data = AniSaveData.getServerState(server.overworld());
        AniSaveData.AnyaLocation activeLoc = data.getActiveAnyaLocation();
        if (activeLoc != null) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(activeLoc.dimension);
            if (dimLoc != null) {
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                return new TargetLocation(dimKey, activeLoc.x, activeLoc.y, activeLoc.z);
            }
        }

        return null;
    }

    public static void beginVanillaAnyaSummon() {
        summonedAnya.remove();
    }

    public static void rememberCreatedAnya(Entity entity) {
        if (entity instanceof AnyaEntity anya) {
            summonedAnya.set(new SummonedAnya(anya.getUUID(), anya.position()));
        }
    }

    public static void finishVanillaAnyaSummon(ServerLevel level, boolean succeeded) {
        SummonedAnya created = summonedAnya.get();
        summonedAnya.remove();
        if (!succeeded || created == null) return;

        AniSaveData data = AniSaveData.getServerState(level);

        data.setSoleAnya(created.uuid(), level.dimension().location().toString(),
                created.position().x, created.position().y, created.position().z);

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AnyaEntity anya && !anya.getUUID().equals(created.uuid())) {
                anya.discard();
            }
        }
    }

    private static String getTrait() {
        if (KarmaManager.get() < 20) return "Хоррор / Абсолютное безумие";
        if (KarmaManager.get() < 30) return "Обиженная";
        if (KarmaManager.get() < 50) return "Цундере";
        if (KarmaManager.get() < 75) return "Нейтральная";
        if (KarmaManager.get() < 90) return "Любящая";
        return "Яндере";
    }

    public static String getTraitColor() {
        if (KarmaManager.get() < 20) return "§4";
        if (KarmaManager.get() < 30) return "§d";
        if (KarmaManager.get() < 50) return "§6";
        if (KarmaManager.get() < 75) return "§f";
        if (KarmaManager.get() < 90) return "§a";
        return "§c";
    }

    public static String getTraitColorName() {
        if (KarmaManager.get() < 20) return "dark_red";
        if (KarmaManager.get() < 30) return "light_purple";
        if (KarmaManager.get() < 50) return "gold";
        if (KarmaManager.get() < 75) return "white";
        if (KarmaManager.get() < 90) return "green";
        return "red";
    }

    private static void drawKarmaActionBar(ServerPlayer player) {
        String face;
        if (KarmaManager.get() < 20) face = "\uE006";
        else if (KarmaManager.get() < 30) face = "\uE003";
        else if (KarmaManager.get() < 50) face = "\uE004";
        else if (KarmaManager.get() < 75) face = "\uE005";
        else if (KarmaManager.get() < 90) face = "\uE002";
        else face = "\uE001";
        int filled = Math.max(0, Math.min(100, KarmaManager.get())) / 10;
        StringBuilder bar = new StringBuilder(10);
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "\uE011" : "\uE012");
        player.sendSystemMessage(Component.literal("§f" + face + "  " + bar + "  §e" + KarmaManager.get()), true);
    }

    public static int[] getKarmaModifiers(ServerPlayer player) {
        boolean hardcore = player.serverLevel().getServer().isHardcore();
        if (hardcore) return new int[]{2, 8};
        switch (player.serverLevel().getDifficulty()) {
            case PEACEFUL: return new int[]{5, 1};
            case EASY:
            case NORMAL: return new int[]{4, 2};
            case HARD:
            default: return new int[]{3, 3};
        }
    }

    private boolean applyMessageKarma(ServerPlayer player, String text) {
        if (player == null || text == null || text.isBlank()) return false;
        String normalized = normalize(text).toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (AnyaTextEmotion.isInsult(normalized)) {
            int old = KarmaManager.get();
            int[] mods = getKarmaModifiers(player);
            KarmaManager.set(Math.max(0, KarmaManager.get() - mods[1]));
            if (KarmaManager.get() != old) player.sendSystemMessage(Component.literal("§c[Аня] Это уже было лишним. (-" + mods[1] + " кармы)"));
            triggerAnyaReaction(player, 1);
            AnyaAI.addMemoryFact("Игрок разозлился и оскорбил меня.", text);
        } else if (AnyaTextEmotion.isCompliment(normalized)) {
            long minecraftDay = player.serverLevel().getDayTime() / 24000L;
            if (minecraftDay != lastComplimentMinecraftDay) {
                int old = KarmaManager.get();
                int[] mods = getKarmaModifiers(player);
                KarmaManager.set(Math.min(100, KarmaManager.get() + mods[0]));
                lastComplimentMinecraftDay = minecraftDay;
                player.sendSystemMessage(Component.literal(KarmaManager.get() != old ? "§d[Аня] +" + mods[0] + " кармы за тёплые слова 💗" : "§7[Аня] Я и так уже на максимальной карме."));
                triggerAnyaReaction(player, 2);
            }
            AnyaAI.addMemoryFact("Игрок сделал мне приятный комплимент / признался в симпатии.", text);
        }
        return false;
    }

    private void triggerAnyaReaction(ServerPlayer player, int type) {
        if (player == null) return;
        AABB area = player.getBoundingBox().inflate(32.0, 16.0, 32.0);
        List<AnyaEntity> anyas = player.serverLevel().getEntitiesOfClass(AnyaEntity.class, area);
        AnyaEntity closest = null;
        double best = Double.MAX_VALUE;
        for (AnyaEntity anya : anyas) {
            double distance = anya.distanceToSqr(player);
            if (distance < best) { best = distance; closest = anya; }
        }
        if (closest != null) closest.triggerReaction(type);
    }
}