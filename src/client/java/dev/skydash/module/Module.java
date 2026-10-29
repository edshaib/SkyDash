package dev.skydash.module;
import dev.skydash.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
public interface Module {
    String id();
    String name();
    boolean enabled();
    void tick();
    void render(GuiGraphicsExtractor graphics,int x,int y,int width,int maxLines);
    Config.ModuleConfig config();
    List<String> lines();
}
