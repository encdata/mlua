package dev.mlua.modules.title.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaReflectiveService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Server-side title operations exposed through the shared reflective Java service base. */
public final class MLuaTitleService extends MLuaReflectiveService {
    public MLuaTitleService(Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Title";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Server");
        return table;
    }

    public void SetTitle(LuaValue playerValue, String text) {
        send(playerValue, new ClientboundSetTitleTextPacket(Component.literal(text)));
    }

    public void SetSubtitle(LuaValue playerValue, String text) {
        send(playerValue, new ClientboundSetSubtitleTextPacket(Component.literal(text)));
    }

    public void SetActionBar(LuaValue playerValue, String text) {
        send(playerValue, new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }

    public void ClearTitle(LuaValue playerValue) {
        send(playerValue, new ClientboundClearTitlesPacket(false));
    }

    public void ResetTitle(LuaValue playerValue) {
        send(playerValue, new ClientboundClearTitlesPacket(true));
    }

    public void SetTitleAnimation(LuaValue playerValue, int fadeIn, int stay, int fadeOut) {
        send(playerValue, new ClientboundSetTitlesAnimationPacket(fadeIn * 20, stay * 20, fadeOut * 20));
    }

    private void send(LuaValue playerValue, net.minecraft.network.protocol.Packet<?> packet) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        if (player.connection == null) throw new LuaError("[MLua] Player is not connected");
        player.connection.send(packet);
    }

    private void requireServer() {
        if (this.context.side() != Side.SERVER) {
            throw new LuaError("[MLua] Title service methods are server-side only");
        }
    }

    private static ServerPlayer extractPlayer(LuaValue playerValue) {
        LuaTable table = playerValue.checktable();
        LuaValue entity = table.get("__entity");
        if (entity.isuserdata(Player.class)) {
            Player player = (Player) entity.checkuserdata(Player.class);
            if (player instanceof ServerPlayer serverPlayer) return serverPlayer;
            throw new LuaError("[MLua] Expected a ServerPlayer, got " + player.getClass().getSimpleName());
        }
        throw new LuaError("[MLua] Expected a Player table");
    }
}
