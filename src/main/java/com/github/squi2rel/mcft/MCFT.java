package com.github.squi2rel.mcft;

import com.github.squi2rel.mcft.tracking.EyeTrackingRect;
import com.github.squi2rel.mcft.tracking.MouthTrackingRect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class MCFT extends JavaPlugin implements CommandExecutor, PluginMessageListener, Listener {
    public static HashMap<UUID, FTModel> models = new HashMap<>();
    public static int fps = 30, syncRadius = 64;

    @Override
    public void onEnable() {
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "mcft:config");
        getServer().getMessenger().registerIncomingPluginChannel(this, "mcft:tracking_params", this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "mcft:tracking_params");
        getServer().getMessenger().registerIncomingPluginChannel(this, "mcft:tracking_update", this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "mcft:tracking_update");
        Objects.requireNonNull(getCommand("mcftreload")).setExecutor(this);
        broadcast();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        loadConfig();
        sender.sendMessage("重载成功");
        models.clear();
        broadcast();
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.sendPluginMessage(this, "mcft:config", writeConfig(fps));
            models.forEach((u, m) -> {
                if (m.enabled) player.sendPluginMessage(this, "mcft:tracking_params", writeParams(m, u));
            });
        }, 40);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        models.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String s, @NotNull Player player, @NotNull byte[] bytes) {
        try {
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            buf.skipBytes(16);
            switch (s) {
                case "mcft:tracking_params" -> {
                    FTModel old = models.get(player.getUniqueId());
                    if (old == null) getLogger().info("玩家 " + player.getDisplayName() + " 正在使用MCFT");
                    FTModel now = new FTModel(EyeTrackingRect.read(buf), EyeTrackingRect.read(buf), MouthTrackingRect.read(buf), buf.readBoolean());
                    if (old != null) now.enabled = old.enabled;
                    now.validate(true);
                    models.put(player.getUniqueId(), now);
                    if (now.enabled) {
                        byte[] data = writeParams(now, player.getUniqueId());
                        for (Player target : getServer().getOnlinePlayers()) {
                            if (!target.equals(player)) {
                                target.sendPluginMessage(this, "mcft:tracking_params", data);
                            }
                        }
                    }
                }
                case "mcft:tracking_update" -> {
                    FTModel model = models.get(player.getUniqueId());
                    if (model == null || System.currentTimeMillis() - model.lastReceived - 10 < 1000 / fps) return;
                    byte[] ref = new byte[buf.readShort()];
                    buf.readBytes(ref);
                    model.readSync(ref);
                    model.validate(false);
                    model.update(fps);
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
                    for (Player target : nearbyPlayers(player.getLocation(), syncRadius)) {
                        if (!target.equals(player)) {
                            target.sendPluginMessage(this, "mcft:tracking_update", data);
                        }
                    }
                }
            }
        } catch (Exception e) {
            player.kickPlayer(e + ": " + e.getMessage());
            throw e;
        }
    }

    public void broadcast() {
        byte[] data = writeConfig(fps);
        for (Player player : getServer().getOnlinePlayers()) {
            player.sendPluginMessage(this, "mcft:config", data);
        }
    }

    public void loadConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        fps = config.getInt("fps");
        syncRadius = config.getInt("syncRadius");
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
            buf.writeBoolean(model.isFlat);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    public static byte[] writeConfig(int fps) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.heapBuffer();
        try {
            buf.writeInt(fps);
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
