package dev.mlua.modules.title.service;

import net.minecraft.network.protocol.Packet;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.modules.players.MLuaPlayerResolver;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Sends titles, subtitles, action bars, and title timing to a connected player addressed by Player instance, name, or UUID.")
public final class MLuaTitleService extends MLuaServerThreadReflectiveService {
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

    @MLuaDescription("Sets title fade-in, display, and fade-out timing in whole seconds. Each value must be non-negative.")
    public void SetTitleAnimation(LuaValue playerValue, LuaValue fadeInValue, LuaValue stayValue, LuaValue fadeOutValue) {
        int fadeIn = titleDuration(fadeInValue, "fadeIn");
        int stay = titleDuration(stayValue, "stay");
        int fadeOut = titleDuration(fadeOutValue, "fadeOut");
        send(playerValue, new ClientboundSetTitlesAnimationPacket(fadeIn * 20, stay * 20, fadeOut * 20));
    }

    private static int titleDuration(LuaValue value, String name) {
        int seconds = MLuaLuaArguments.wholeInt(value, "Title:SetTitleAnimation", name);
        if (seconds < 0 || seconds > Integer.MAX_VALUE / 20) {
            throw new LuaError("[MLua] Title:SetTitleAnimation " + name
                + " must be between 0 and " + (Integer.MAX_VALUE / 20) + " seconds.");
        }
        return seconds;
    }

    private void send(LuaValue playerValue, Packet<?> packet) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        if (player.connection == null) throw new LuaError("[MLua] Player is not connected");
        player.connection.send(packet);
    }

    private ServerPlayer extractPlayer(LuaValue playerValue) {
        return MLuaPlayerResolver.resolveServerPlayer(playerValue, this.context, this.name());
    }
}
