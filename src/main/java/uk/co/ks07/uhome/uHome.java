package uk.co.ks07.uhome;

import uk.co.ks07.uhome.storage.ConnectionManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.regex.Matcher;

import uk.co.ks07.uhome.griefcraft.Updater;
import uk.co.ks07.uhome.griefcraft.Metrics;
import uk.co.ks07.uhome.griefcraft.UHomePlotter;
import uk.co.ks07.uhome.locale.LocaleManager;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import uk.co.ks07.uhome.importers.ImporterManager;

public class uHome extends JavaPlugin {

    private HomeList homeList;
    public String name;
    public String version;
    private Updater updater;
    public PluginManager pm;
    public static final String DEFAULT_HOME = "home";
    
    public Economy economy;

    @Override
    public void onDisable() {
        ConnectionManager.closeConnection(this.getLogger());
    }

    @Override
    public void onEnable() {
        this.pm = getServer().getPluginManager();
        this.name = this.getDescription().getName();
        this.version = this.getDescription().getVersion();

        this.getLogger().setLevel(Level.INFO);

        SuperPermsManager.initialize(this);

        try {
            this.getConfig().options().copyDefaults(true);
            HomeConfig.initialize(this.getConfig(), getDataFolder(), this.getLogger());
            this.saveConfig();
        } catch (Exception ex) {
            this.getLogger().log(Level.SEVERE, "Could not load config!", ex);
        }
        
        if (HomeConfig.enableEcon) {
            if (getServer().getPluginManager().getPlugin("Vault") != null) {
                RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    economy = rsp.getProvider();
                    this.getLogger().info("Connected to " + economy.getName() + " for economy support.");
                } else {
                    this.getLogger().warning("Vault could not find any economy plugin to connect to. Please install one or disable economy.");
                    HomeConfig.enableEcon = false;
                }
            } else {
                this.getLogger().warning("Coult not find Vault plugin, but economy is enabled. Please install Vault or disable economy.");
                HomeConfig.enableEcon = false;
            }
        }

        libCheck();
        boolean needImport = convertOldDB(getDataFolder());
        if (!sqlCheck()) {
            return;
        }

        homeList = new HomeList(this, needImport, this.getLogger());
        
        ImporterManager impMan = new ImporterManager(this, homeList);
        impMan.checkImports();

        File customLocale = new File(this.getDataFolder(), "customlocale.properties");

        if (!customLocale.exists()) {
            writeResource(this.getResource("customlocale.properties"), customLocale);
        }

        LocaleManager.init(customLocale, this.getLogger());

        this.beginMetrics();

        this.getCommand("sethome").setExecutor(new SetHomeCommand(this, homeList));
        this.getCommand("home").setExecutor(new HomeCommand(this, homeList));

        this.pm.registerEvents(new UHomeListener(this, this.homeList), this);
    }

    private void libCheck() {
        if (HomeConfig.downloadLibs) {
            updater = new Updater();
            try {
                updater.check();
                updater.update();
            } catch (Exception e) {
                this.getLogger().warning("Failed to update libs.");
            }
        }
    }

    private boolean convertOldDB(File df) {
        File oldDatabase = new File(df, "homes.db");
        File newDatabase = new File(df, "uhomes.db");
        if (!newDatabase.exists() && oldDatabase.exists()) {
            // Create new database file.
            updateFiles(newDatabase);
            oldDatabase.renameTo(new File(df, "homes.db.old"));

            // Return true if importing is required (sqlite only).
            if (!HomeConfig.usemySQL) {
                return true;
            }
        } else if (newDatabase.exists() && oldDatabase.exists()) {
            // We no longer need this file since uhomes.db exists
            oldDatabase.renameTo(new File(df, "homes.db.old"));
        }
        return false;
    }

    private boolean sqlCheck() {
        Connection conn = ConnectionManager.initialize(this.getLogger());
        if (conn == null) {
            this.getLogger().severe("Could not establish SQL connection.");
            pm.disablePlugin(this);
            return false;
        }
        return true;
    }

    private void updateFiles(File newDatabase) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        if (newDatabase.exists()) {
            newDatabase.delete();
        }
        try {
            newDatabase.createNewFile();
        } catch (IOException ex) {
            this.getLogger().log(Level.SEVERE, "Could not create new database file", ex);
        }
    }

    // Thanks to xZise for original code.
    public static void writeResource(InputStream fromResource, File toFile) {
        FileOutputStream to = null;
        try {
            to = new FileOutputStream(toFile);
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = fromResource.read(buffer)) != -1) {
                to.write(buffer, 0, bytesRead);
            }
        } catch (IOException ex) {
        } finally {
            if (fromResource != null) {
                try {
                    fromResource.close();
                } catch (IOException e) {
                }
            }
            if (to != null) {
                try {
                    to.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void beginMetrics() {
        try {
            Metrics metrics = new Metrics(this);

            Metrics.Graph homesGraph = metrics.createGraph("Home Count");

            // Plot the total amount of protections
            homesGraph.addPlotter(new UHomePlotter("Total Homes", this.homeList) {

                @Override
                public int getValue() {
                    return this.homeList.getTotalWarps();
                }

            });

            Metrics.Graph limitGraph = metrics.createGraph("Active Limits");

            // Plot the number of dynamic home limits registered
            limitGraph.addPlotter(new UHomePlotter("Registered Home Limits", this.homeList) {

                @Override
                public int getValue() {
                    return HomeConfig.permLimits.size();
                }

            });
            // Plot the number of dynamic home invite limits registered
            limitGraph.addPlotter(new UHomePlotter("Registered Invite Limits", this.homeList) {

                @Override
                public int getValue() {
                    if (HomeConfig.enableInvite) {
                        return HomeConfig.permInvLimits.size();
                    } else {
                        return 0;
                    }
                }

            });

            if (metrics.start()) {
                this.getLogger().info("Sending anonymous usage statistics to metrics.griefcraft.com.");
            }
        } catch (IOException e ) {
            this.getLogger().log(Level.WARNING, "Failed to connect to plugin metrics.", e);
        }
    }
}
