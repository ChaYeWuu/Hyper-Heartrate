package com.chayewuu.hyperheartrate.network;

import com.chayewuu.hyperheartrate.HeartRateMod;
import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

/**
 * 联机心率同步网络通信。
 * <p>
 * 使用 Fabric Networking API 的 CustomPayload 体系实现客户端↔服务端心率同步。
 * </p>
 *
 * <p><b>不阻挡未安装模组的玩家：</b>服务端仅向 {@code canSend} 返回 true 的玩家发送 S2C 包，
 * 未安装模组的玩家不会收到任何自定义包，可正常进服。</p>
 *
 * <p>数据流：</p>
 * <ol>
 *     <li>客户端每秒发送 C2S 包（自身心率）</li>
 *     <li>服务端收到后，广播 S2C 包给 64 格内安装了模组的其他玩家</li>
 *     <li>客户端收到 S2C 包，存入 {@link RemoteHeartRateStore}</li>
 * </ol>
 */
public class MultiplayerNetworking {

    // ===== C2S Payload：客户端→服务端 =====

    public record HeartRateC2SPayload(int heartRate) implements CustomPayload {
        public static final CustomPayload.Id<HeartRateC2SPayload> ID =
                new CustomPayload.Id<>(Identifier.of(HeartRateMod.MOD_ID, "hr_c2s"));

        public static final PacketCodec<RegistryByteBuf, HeartRateC2SPayload> PACKET_CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeVarInt(payload.heartRate()),
                        buf -> new HeartRateC2SPayload(buf.readVarInt())
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ===== S2C Payload：服务端→客户端 =====

    public record HeartRateS2CPayload(UUID playerUuid, int heartRate) implements CustomPayload {
        public static final CustomPayload.Id<HeartRateS2CPayload> ID =
                new CustomPayload.Id<>(Identifier.of(HeartRateMod.MOD_ID, "hr_s2c"));

        public static final PacketCodec<RegistryByteBuf, HeartRateS2CPayload> PACKET_CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeUuid(payload.playerUuid());
                            buf.writeVarInt(payload.heartRate());
                        },
                        buf -> new HeartRateS2CPayload(buf.readUuid(), buf.readVarInt())
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 注册所有 payload 类型（服务端 + 客户端共用）。
     * <p>应在 {@code onInitialize} 中调用。</p>
     */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(HeartRateC2SPayload.ID, HeartRateC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(HeartRateS2CPayload.ID, HeartRateS2CPayload.PACKET_CODEC);

        ModLogger.info("[MultiplayerNetworking] Payload 类型已注册");
    }

    /**
     * 注册服务端接收器（C2S）。
     * <p>在服务端 onInitialize 中调用。收到客户端心率后广播给附近安装了模组的其他玩家。</p>
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HeartRateC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity sender = context.player();
            int hr = payload.heartRate();
            MinecraftServer server = context.server();

            // 构造 S2C 包
            HeartRateS2CPayload s2c = new HeartRateS2CPayload(sender.getUuid(), hr);

            // 广播给 64 格内安装了模组的其他玩家
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            for (ServerPlayerEntity other : players) {
                if (other == sender) {
                    continue;
                }
                // 仅向安装了模组的玩家发送（不阻挡未安装玩家）
                if (!ServerPlayNetworking.canSend(other, HeartRateS2CPayload.ID)) {
                    continue;
                }
                // 距离检查（64 格内）
                if (other.squaredDistanceTo(sender) > 64 * 64) {
                    continue;
                }
                ServerPlayNetworking.send(other, s2c);
            }
        });

        ModLogger.info("[MultiplayerNetworking] 服务端 C2S 接收器已注册");
    }

    /**
     * 注册客户端接收器（S2C）。
     * <p>在客户端 onInitializeClient 中调用。收到其他玩家心率后存入 RemoteHeartRateStore。</p>
     */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(HeartRateS2CPayload.ID, (payload, context) -> {
            RemoteHeartRateStore.getInstance().updateHeartRate(payload.playerUuid(), payload.heartRate());
        });

        ModLogger.info("[MultiplayerNetworking] 客户端 S2C 接收器已注册");
    }

    /**
     * 客户端发送自身心率到服务端。
     * <p>每秒调用一次。仅在联机功能开启时发送。</p>
     */
    public static void sendHeartRateToServer() {
        ModConfig config = ConfigManager.getConfig();
        if (!config.isMultiplayerEnabled()) {
            return;
        }
        // 检查是否连接到服务端（单人游戏也走网络通道）
        if (!ClientPlayNetworking.canSend(HeartRateC2SPayload.ID)) {
            return;
        }
        int hr = HeartRateManager.getInstance().getCurrentHeartRate();
        // 即使心率为 0 也发送，让其他玩家知道我们"没有心率"
        ClientPlayNetworking.send(new HeartRateC2SPayload(hr));
    }
}
