package anvil.fix;

import org.bukkit.plugin.java.JavaPlugin;

@MainClass
public final class Main extends JavaPlugin {

    public static Main instance;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        if (!getServer().getPluginManager().isPluginEnabled("packetevents")) {
            this.getLogger().severe("Missing dependency: packetevents! Download from https://www.spigotmc.org/resources/packetevents-api.80279/ in order to use this plugin.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.getServer().getPluginManager().registerEvents(new Events(), this);
        PacketListener.init();
        this.getLogger().info("Modern-NotTooExpensive has been enabled!");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Modern-NotTooExpensive has been disabled!");
    }
}