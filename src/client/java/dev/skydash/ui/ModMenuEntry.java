package dev.skydash.ui;
import com.terraformersmc.modmenu.api.*;
public final class ModMenuEntry implements ModMenuApi {
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DashboardScreen::new;
    }
}
