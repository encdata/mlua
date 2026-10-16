package dev.mlua.modules.title.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
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

@MLuaDescription("Service for sending titles, subtitles, and action bars.")
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

    @MLuaDescription("Displays a title to a player.")
    public void SetTitle(LuaValue playerValue, String text) {
        send(playerValue, new ClientboundSetTitleTextPacket(Component.literal(text)));
    }

    @MLuaDescription("Displays a subtitle to a player.")
    public void SetSubtitle(LuaValue playerValue, String text) {
        send(playerValue, new ClientboundSetSubtitleTextPacket(Component.literal(text)));
    }

    @MLuaDescription("Displays a short action bar message to a player.")
    public void SetActionBar(LuaValue playerValue, String text) {
        send(playerValue, new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }

    @MLuaDescription("Clears the current title and subtitle.")
    public void ClearTitle(LuaValue playerValue) {
        send(playerValue, new ClientboundClearTitlesPacket(false));
    }

    @MLuaDescription("Resets title display timing to the default.")
    public void ResetTitle(LuaValue playerValue) {
        send(playerValue, new ClientboundClearTitlesPacket(true));
    }

    @MLuaDescription("Sets title fade-in, display, and fade-out timing.")
    public void SetTitleAnimation(LuaValue playerValue, int fadeIn, int stay, int fadeOut) {
        send(playerValue, new ClientboundSetTitlesAnimationPacket(fadeIn * 20, stay * 20, fadeOut * 20));
    }

    private void send(LuaValue playerValue, net.minecraft.network.protocol.Packet<?> packet) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        if (player.connection == null) throw new LuaError("[MLua] Player is not connected");
        player.connection.send(packet);
    }

    private ServerPlayer extractPlayer(LuaValue playerValue) {
        Player player = (Player) MLuaEntityWrapper.requireLuaInstance(playerValue, context.mlua().registration(), "Player", "Player");
        if (player instanceof ServerPlayer serverPlayer) return serverPlayer;
        throw new LuaError("[MLua] Expected a ServerPlayer, got " + player.getClass().getSimpleName());
    }
}
