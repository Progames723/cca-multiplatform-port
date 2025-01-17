/*
 * Cardinal-Components-API
 * Copyright (C) 2019-2023 OnyxStudios
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package dev.progames723.cca_architectury_port.mixin.level.common;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import dev.progames723.cca_architectury_port.api.v3.component.ComponentContainer;
import dev.progames723.cca_architectury_port.api.v3.component.ComponentKey;
import dev.progames723.cca_architectury_port.api.v3.component.ComponentProvider;
import dev.progames723.cca_architectury_port.api.v3.component.sync.AutoSyncedComponent;
import dev.progames723.cca_architectury_port.api.v3.component.sync.ComponentPacketWriter;
import dev.progames723.cca_architectury_port.internal.level.ComponentsLevelNetworking;
import dev.progames723.cca_architectury_port.internal.level.StaticLevelComponentPlugin;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelVersion;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.timers.TimerQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

@Mixin(PrimaryLevelData.class)
public abstract class MixinPrimaryLevelData implements ServerLevelData, ComponentProvider {
    @Unique
    private ComponentContainer components;

    @Inject(method = "<init>(Lcom/mojang/datafixers/DataFixer;ILnet/minecraft/nbt/CompoundTag;ZIIIFJJIIIZIZZZLnet/minecraft/world/level/border/WorldBorder$Settings;IILjava/util/UUID;Ljava/util/Set;Ljava/util/Set;Lnet/minecraft/world/level/timers/TimerQueue;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/dimension/end/EndDragonFight$Data;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/levelgen/WorldOptions;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lcom/mojang/serialization/Lifecycle;)V", at = @At("RETURN"))
    private void initComponents(DataFixer dataFixer, int dataVersion, CompoundTag playerData, boolean modded, int spawnX, int spawnY, int spawnZ, float spawnAngle, long time, long timeOfDay, int version, int clearWeatherTime, int rainTime, boolean raining, int thunderTime, boolean thundering, boolean initialized, boolean difficultyLocked, WorldBorder.Settings worldBorder, int wanderingTraderSpawnDelay, int wanderingTraderSpawnChance, UUID wanderingTraderId, Set<String> serverBrands, Set<String> removedFeatures, TimerQueue<MinecraftServer> scheduledEvents, CompoundTag customBossEvents, EndDragonFight.Data dragonFight, LevelSettings levelInfo, WorldOptions generatorOptions, PrimaryLevelData.SpecialWorldProperty specialProperty, Lifecycle lifecycle, CallbackInfo ci)  {
        this.components = StaticLevelComponentPlugin.createContainer(this);
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private static void readComponents(Dynamic<Tag> dynamic, DataFixer dataFixer, int dataVersion, CompoundTag playerData, LevelSettings levelInfo, LevelVersion saveVersionInfo, PrimaryLevelData.SpecialWorldProperty specialProperty, WorldOptions generatorOptions, Lifecycle lifecycle, CallbackInfoReturnable<PrimaryLevelData> cir) {
        ((ComponentProvider) cir.getReturnValue()).getComponentContainer().fromDynamic(dynamic);
    }

    @Inject(method = "setTagData", at = @At("RETURN"))
    private void writeComponents(RegistryAccess tracker, CompoundTag data, CompoundTag player, CallbackInfo ci) {
        this.components.toTag(data);
    }

    @Nonnull
    @Override
    public ComponentContainer getComponentContainer() {
        return this.components;
    }

    @Override
    public Iterable<ServerPlayer> getRecipientsForComponentSync() {
        throw new UnsupportedOperationException("Please call LevelComponents#sync(MinecraftServer) instead of ComponentKey#sync");
    }

    @Nullable
    @Override
    public <C extends AutoSyncedComponent> ClientboundCustomPayloadPacket toComponentPacket(ComponentKey<? super C> key, ComponentPacketWriter writer, ServerPlayer recipient) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeResourceLocation(key.getId());
        writer.writeSyncPacket(buf, recipient);
        return new ClientboundCustomPayloadPacket(ComponentsLevelNetworking.PACKET_ID, buf);
    }

}
