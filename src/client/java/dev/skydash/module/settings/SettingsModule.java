package dev.skydash.module.settings;
import dev.skydash.*;
import dev.skydash.module.*;
public final class SettingsModule extends BaseModule {
    public SettingsModule(DashboardContext c) {
        super(c,"settings","Configuration");
    }
    public void tick() {
        lines("Dashboard: toggle and pin each module","HUD layout: drag pinned panels","Profile refresh: "+ctx.config.profileTtl()+"s","Public fallback: "+ctx.config.publicTtl()+"s","Use Settings to edit goals, alerts and timers");
    }
}
