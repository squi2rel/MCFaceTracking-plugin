package com.github.squi2rel.mcft;

import com.github.squi2rel.mcft.tracking.EyeTrackingRect;
import com.github.squi2rel.mcft.tracking.MouthTrackingRect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class MCFT extends JavaPlugin implements PluginMessageListener, Listener {
    public static HashMap<UUID, FTModel> models = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "mcft:tracking_params", this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "mcft:tracking_params");
        getServer().getMessenger().registerIncomingPluginChannel(this, "mcft:tracking_update", this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "mcft:tracking_update");
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        models.forEach((u, m) -> {
            if (!m.enabled) return;
            byte[] data = writeParams(m, u);
            player.sendPluginMessage(this, "mcft:tracking_params", data);
        });
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        models.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String s, @NotNull Player player, @NotNull byte[] bytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        buf.skipBytes(16);
        switch (s) {
            case "mcft:tracking_params" -> {
                if (models.get(player.getUniqueId()) == null) getLogger().info("玩家 " + player.getDisplayName() + " 正在使用MCFT");
                models.put(player.getUniqueId(), new FTModel(EyeTrackingRect.read(buf), EyeTrackingRect.read(buf), MouthTrackingRect.read(buf)));
            }
            case "mcft:tracking_update" -> {
                FTModel model = models.get(player.getUniqueId());
                if (model == null) return;
                byte[] ref = new byte[buf.readShort()];
                buf.readBytes(ref);
                model.readSync(ref);
                if (!model.enabled) {
                    model.enabled = true;
                    getLogger().info("玩家 " + player.getDisplayName() + " 已连接OSC");
                    byte[] data = writeParams(model, player.getUniqueId());
                    for (Player target : getServer().getOnlinePlayers()) {
                        if (!target.equals(player)) {
                            target.sendPluginMessage(this, "mcft:tracking_params", data);
                        }
                    }
                }
                byte[] data = writeSync(model, player.getUniqueId());
                for (Player target : nearbyPlayers(player.getLocation(), 128)) {
                    if (!target.equals(player)) {
                        target.sendPluginMessage(this, "mcft:tracking_update", data);
                    }
                }
            }
        }
    }

    public static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static byte[] writeParams(FTModel model, UUID uuid) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.heapBuffer();
        try {
            writeUuid(buf, uuid);
            model.eyeR.write(buf);
            model.eyeL.write(buf);
            model.mouth.write(buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    public static byte[] writeSync(FTModel model, UUID uuid) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.heapBuffer();
        ByteBuf buf2 = PooledByteBufAllocator.DEFAULT.heapBuffer();
        try {
            writeUuid(buf, uuid);
            model.eyeR.writeSync(buf2);
            model.eyeL.writeSync(buf2);
            model.mouth.writeSync(buf2);
            byte[] data = new byte[buf2.readableBytes()];
            buf2.readBytes(data);
            buf.writeShort(data.length);
            buf.writeBytes(data);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
            buf2.release();
        }
    }

    public static List<Player> nearbyPlayers(Location pos, double r) {
        List<Player> players = new ArrayList<>();
        double radiusSquared = r * r;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != pos.getWorld()) continue;
            if (player.getLocation().distanceSquared(pos) <= radiusSquared) {
                players.add(player);
            }
        }
        return players;
    }
}
