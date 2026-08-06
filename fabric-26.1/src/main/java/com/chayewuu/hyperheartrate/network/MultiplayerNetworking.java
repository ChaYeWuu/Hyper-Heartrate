package com.chayewuu.hyperheartrate.network;

import com.chayewuu.hyperheartrate.HeartRateMod;
import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * 联机心率同步网络通信。
 * <p>
 * 使用 Fabric Networking API 的 CustomPacketPayload 体系实现客户端↔服务端心率同步。
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

    public record HeartRateC2SPayload(int heartRate) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<HeartRateC2SPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HeartRateMod.MOD_ID, "hr_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, HeartRateC2SPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeVarInt(payload.heartRate),
                        buf -> new HeartRateC2SPayload(buf.readVarInt())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ===== S2C Payload：服务端→客户端 =====

    public record HeartRateS2CPayload(UUID playerUuid, int heartRate) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<HeartRateS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HeartRateMod.MOD_ID, "hr_s2c"));

        public static final StreamCodec<RegistryFriendlyByteBuf, HeartRateS2CPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeUUID(payload.playerUuid);
                            buf.writeVarInt(payload.heartRate);
                        },
                        buf -> new HeartRateS2CPayload(buf.readUUID(), buf.readVarInt())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 注册所有 payload 类型（服务端 + 客户端共用）。
     * <p>应在 {@code onInitialize} 中调用。</p>
     */
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(HeartRateC2SPayload.TYPE, HeartRateC2SPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HeartRateS2CPayload.TYPE, HeartRateS2CPayload.STREAM_CODEC);

        ModLogger.info("[MultiplayerNetworking] Payload 类型已注册");
    }

    /**
     * 注册服务端接收器（C2S）。
     * <p>在服务端 onInitialize 中调用。收到客户端心率后广播给附近安装了模组的其他玩家。</p>
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HeartRateC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            int hr = payload.heartRate();
            MinecraftServer server = context.server();

            // 构造 S2C 包
            HeartRateS2CPayload s2c = new HeartRateS2CPayload(sender.getUUID(), hr);

            // 广播给 64 格内安装了模组的其他玩家
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            for (ServerPlayer other : players) {
                if (other == sender) {
                    continue;
                }
                // 仅向安装了模组的玩家发送（不阻挡未安装玩家）
                if (!ServerPlayNetworking.canSend(other, HeartRateS2CPayload.TYPE)) {
                    continue;
                }
                // 距离检查（64 格内）
                if (other.distanceToSqr(sender) > 64 * 64) {
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
        ClientPlayNetworking.registerGlobalReceiver(HeartRateS2CPayload.TYPE, (payload, context) -> {
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
        if (!ClientPlayNetworking.canSend(HeartRateC2SPayload.TYPE)) {
            return;
        }
        int hr = HeartRateManager.getInstance().getCurrentHeartRate();
        // 即使心率为 0 也发送，让其他玩家知道我们"没有心率"
        ClientPlayNetworking.send(new HeartRateC2SPayload(hr));
    }
}
