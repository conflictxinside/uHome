package uk.co.ks07.uhome;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import uk.co.ks07.uhome.timers.HomeCoolDown;
import uk.co.ks07.uhome.timers.WarmUp;
import uk.co.ks07.uhome.timers.SetHomeCoolDown;

public class HomeList {

    private HashMap<String, HashMap<String, Home>> homeList;
    private Server server;
    private final HomeCoolDown homeCoolDown = HomeCoolDown.getInstance();
    private final SetHomeCoolDown setHomeCoolDown = SetHomeCoolDown.getInstance();
    private HashMap<String, HashSet<Home>> inviteList;

    public HomeList(Server server, boolean needImport, Logger log) {
        WarpDataSource.initialize(needImport, server, log);
        homeList = WarpDataSource.getMap(log);
        inviteList = new HashMap<String, HashSet<Home>>();
        this.server = server;
    }

    public void addHome(Player player, Plugin plugin, String name, Logger log) {
        if (!(setHomeCoolDown.playerHasCooled(player))) {
            player.sendMessage(ChatColor.RED + "You need to wait "
                    + setHomeCoolDown.estimateTimeLeft(player) + " more seconds of the "
                    + setHomeCoolDown.getTimer(player) + " second cooldown before you can edit your homes.");
        } else {
            if (!homeList.containsKey(player.getName())) {
                // Player has no warps.
                HashMap<String, Home> warps = new HashMap<String, Home>();
                Home warp = new Home(player, name);
                warps.put(name, warp);
                homeList.put(player.getName(), warps);
                WarpDataSource.addWarp(warp, log);
                player.sendMessage(ChatColor.AQUA + "Welcome to your first home!");
                setHomeCoolDown.addPlayer(player, plugin);
            } else if (!this.homeExists(player.getName(), name)) {
                if (this.playerCanSet(player)) {
                    // Player has warps, but not with the given name.
                    Home warp = new Home(player, name);
                    homeList.get(player.getName()).put(name, warp);
                    WarpDataSource.addWarp(warp, log);
                    player.sendMessage(ChatColor.AQUA + "Welcome to your new home :).");
                    setHomeCoolDown.addPlayer(player, plugin);
                } else {
                    // Player cannot set a new warp as they are at their warp limit.
                    player.sendMessage(ChatColor.RED + "You have too many homes! You must delete one before you can set a new home!");
                }
            } else {
                // Player has a warp with the given name.
                Home warp = homeList.get(player.getName()).get(name);
                warp.setLocation(player.getLocation());
                WarpDataSource.moveWarp(warp, log);
                player.sendMessage(ChatColor.AQUA + "Succesfully moved your home.");
                setHomeCoolDown.addPlayer(player, plugin);
            }
        }
    }

    public void adminAddHome(Player player, String owner, String name, Logger log) {
        // Adds a home ignoring limits, ownership and cooldown.
        if (!homeList.containsKey(owner)) {
            // Player has no warps.
            HashMap<String, Home> warps = new HashMap<String, Home>();
            Home warp = new Home(owner, player.getLocation(), name);
            warps.put(name, warp);
            homeList.put(owner, warps);
            WarpDataSource.addWarp(warp, log);
            player.sendMessage(ChatColor.AQUA + "Created first home for " + owner);
        } else if (!this.homeExists(owner, name)) {
            // Player has warps, but not with the given name.
            Home warp = new Home(owner, player.getLocation(), name);
            homeList.get(owner).put(name, warp);
            WarpDataSource.addWarp(warp, log);
            player.sendMessage(ChatColor.AQUA + "Created new home for " + owner);
        } else {
            // Player has a warp with the given name.
            Home warp = homeList.get(owner).get(name);
            warp.setLocation(player.getLocation());
            WarpDataSource.moveWarp(warp, log);
            player.sendMessage(ChatColor.AQUA + "Succesfully moved home for " + owner);
        }
    }

    public void warpTo(String target, Player player, Plugin plugin) {
        this.warpTo(player.getName(), target, player, plugin);
    }

    public void warpTo(String targetOwner, String target, Player player, Plugin plugin) {
        MatchList matches = this.getMatches(target, player, targetOwner);
        target = matches.getMatch(target);
        if (homeList.get(targetOwner).containsKey(target)) {
            Home warp = homeList.get(targetOwner).get(target);
            if (warp.playerCanWarp(player)) {
                if (homeCoolDown.playerHasCooled(player)) {
                    WarmUp.addPlayer(player, warp, plugin);
                    homeCoolDown.addPlayer(player, plugin);
                } else {
                    player.sendMessage(ChatColor.RED + "You need to wait "
                            + homeCoolDown.estimateTimeLeft(player) + " more seconds of the "
                            + homeCoolDown.getTimer(player) + " second cooldown.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to warp to " + targetOwner + "'s home");
            }
        } else {
            player.sendMessage(ChatColor.RED + "The warp " + target + " doesn't exist!");
        }
    }

    public void sendPlayerHome(Player player, Plugin plugin) {
        if (homeList.containsKey(player.getName())) {
            if (homeCoolDown.playerHasCooled(player)) {
                WarmUp.addPlayer(player, homeList.get(player.getName()).get(uHome.DEFAULT_HOME), plugin);
                homeCoolDown.addPlayer(player, plugin);
            } else {
                player.sendMessage(ChatColor.RED + "You need to wait "
                        + homeCoolDown.estimateTimeLeft(player) + " more seconds of the "
                        + homeCoolDown.getTimer(player) + " second cooldown.");
            }
        }
    }

    public Location getHomeLocation(String owner, String name) {
        return this.homeList.get(owner).get(name).getLocation;
    }

    public boolean playerHasDefaultHome(Player player) {
        return this.homeExists(player.getName(), uHome.DEFAULT_HOME);
    }

    public boolean playerHasHomes(Player player) {
        return this.homeList.containsKey(player.getName());
    }

    public boolean playerCanWarp(Player player, String owner, String name) {
        return homeList.get(owner).get(name).playerCanWarp(player);
    }

    public void invitePlayer(Player owner, String player, String name) {
        homeList.get(owner.getName()).get(name).addInvitees(player);
        owner.sendMessage("Invited " + player + " to your home " + name);
        if (!inviteList.containsKey(player)) {
            inviteList.put(player, new HashSet<Home>());
        }
        inviteList.get(player).add(homeList.get(owner.getName()).get(name));
        owner.sendMessage("Invited " + player + " to your home " + name);
        Player invitee = server.getPlayerExact(player);
        if (invitee != null) {
            invitee.sendMessage("You have been invited to " + owner.getName() + "'s home " + name);
        }
    }

    public void uninvitePlayer(Player owner, String player, String name) {
        homeList.get(owner.getName()).get(name).removeInvitee(player);
        if (inviteList.containsKey(player)) {
            inviteList.get(player).remove(homeList.get(owner.getName()).get(name));
        }
        owner.sendMessage("Uninvited " + player + " from your home " + name);
        Player invitee = server.getPlayerExact(player);
        if (invitee != null) {
            invitee.sendMessage("You have been uninvited from " + owner.getName() + "'s home " + name);
        }
    }

    public int getPlayerHomeCount(String owner) {
        return homeList.get(owner).size();
    }

    public boolean playerCanSet(Player player) {
        int playerWarps = homeList.get(player.getName()).size();
        int playerMaxWarps = this.playerGetLimit(player);

        return ((playerMaxWarps < 0) || (playerWarps < playerMaxWarps));
    }

    public int playerGetLimit(Player player) {
        if (SuperPermsManager.hasPermission(player, SuperPermsManager.bypassLimit)) {
            return -1;
        } else {
            int playerMaxWarps;

            if (SuperPermsManager.hasPermission(player, SuperPermsManager.limitA)) {
                playerMaxWarps = HomeConfig.limits.get("a");
            } else if (SuperPermsManager.hasPermission(player, SuperPermsManager.limitB)) {
                playerMaxWarps = HomeConfig.limits.get("b");
            } else if (SuperPermsManager.hasPermission(player, SuperPermsManager.limitC)) {
                playerMaxWarps = HomeConfig.limits.get("c");
            } else if (SuperPermsManager.hasPermission(player, SuperPermsManager.limitD)) {
                playerMaxWarps = HomeConfig.limits.get("d");
            } else if (SuperPermsManager.hasPermission(player, SuperPermsManager.limitE)) {
                playerMaxWarps = HomeConfig.limits.get("e");
            } else {
                playerMaxWarps = HomeConfig.defaultLimit;
            }

            return playerMaxWarps;
        }
    }

    public void deleteHome(Player player, Logger log) {
        if (this.playerHasDefaultHome(player)) {
            Home warp = homeList.get(player.getName()).get(uHome.DEFAULT_HOME);
            homeList.get(player.getName()).remove(uHome.DEFAULT_HOME);
            WarpDataSource.deleteWarp(warp, log);
            player.sendMessage(ChatColor.AQUA + "You have deleted your home");
        } else {
            player.sendMessage(ChatColor.RED + "You have no home to delete :(");
        }
    }

    public void deleteHome(Player owner, String name, Logger log) {
        if (this.homeExists(owner.getName(), name)) {
            Home warp = homeList.get(owner.getName()).get(name);
            homeList.get(owner.getName()).remove(name);
            WarpDataSource.deleteWarp(warp, log);
            owner.sendMessage(ChatColor.AQUA + "You have deleted your home '" + name + "'.");
        } else {
            owner.sendMessage(ChatColor.RED + "You don't have a home called '" + name + "'!");
        }
    }

    public void deleteHome(String owner, String name, CommandSender sender, Logger log) {
        if (this.homeExists(owner, name)) {
            Home warp = homeList.get(owner).get(name);
            homeList.get(owner).remove(name);
            WarpDataSource.deleteWarp(warp, log);
            sender.sendMessage(ChatColor.AQUA + "You have deleted " + owner + "'s home '" + name + "'.");
        } else {
            sender.sendMessage(ChatColor.RED + "There is no home '" + name + "' for " + owner + "!");
        }
    }

    public void deleteHome(String owner, String name, Logger log) {
        if (this.homeExists(owner, name)) {
            Home warp = homeList.get(owner).get(name);
            homeList.get(owner).remove(name);
            WarpDataSource.deleteWarp(warp, log);
        }
    }

    public boolean homeExists(String owner, String name) {
        if (this.hasHomes(owner)) {
            return homeList.get(owner).containsKey(name);
        } else {
            return false;
        }
    }

    public boolean hasHomes(String player) {
        return (homeList.containsKey(player) && homeList.get(player).size() > 0);
    }

    public boolean hasInvitedToHomes(String player) {
        return (inviteList.containsKey(player) && inviteList.get(player).size() > 0);
    }

    public void list(Player player) {
        String results = this.getPlayerList(player.getName());

        if (results == null) {
            player.sendMessage(ChatColor.RED + "You have no homes!");
        } else {
            player.sendMessage(ChatColor.AQUA + "You have the following homes:");
            player.sendMessage(results);
        }
    }

    public void listInvitedTo(Player player) {
        String results = this.getInvitedToList(player.getName());

        if (results == null) {
            player.sendMessage(ChatColor.RED + "You have no invites!");
        } else {
            player.sendMessage(ChatColor.AQUA + "You have been invited to the following homes:");
            player.sendMessage(results);
        }
    }

    public void listRequests(Player player) {
        String results[] = this.getRequestList(player.getName());

        if (results == null) {
            player.sendMessage(ChatColor.RED + "You haven't invited anyone!");
        } else {
            player.sendMessage(ChatColor.AQUA + "You have invited others to the following homes:");
            for (String s : results) {
                player.sendMessage(s);
            }
        }
    }

    public void listOther(CommandSender sender, String owner) {
        String results = this.getPlayerList(owner.toLowerCase());

        if (results == null) {
            sender.sendMessage(ChatColor.RED + "That player has no homes.");
        } else {
            sender.sendMessage(ChatColor.AQUA + "That player has the following homes:");
            sender.sendMessage(results);
        }
    }

    public String getPlayerList(String owner) {
        if (this.hasHomes(owner)) {
            ArrayList<Home> results = new ArrayList(homeList.get(owner).values());

            String ret = results.toString().replace("[", "").replace("]", "");
            return ret;
        } else {
            return null;
        }
    }

    public String getInvitedToList(String owner) {
        if (this.hasInvitedToHomes(owner)) {
            StringBuilder ret = new StringBuilder(32);

            for (Home home : inviteList.get(owner)) {
                ret.append(home.owner).append(" ").append(home.name).append(", ");
            }

            return ret.delete(ret.length() - 2, ret.length() - 1).toString();
        } else {
            return null;
        }
    }

    public String[] getRequestList(String owner) {
        if (this.hasHomes(owner)) {
            String[] reqs = new String[this.getPlayerHomeCount(owner)];
            boolean anyInvites = false;
            int i = -1;

            for (Home home : homeList.get(owner).values()) {
                if (home.hasInvitees()) {
                    anyInvites = true;
                    i += 1;

                    StringBuilder temp = new StringBuilder(32);
                    temp.append(home.name).append(" - (");

                    for (String invitee : home.getInvitees()) {
                        temp.append(invitee).append(", ");
                    }
                    temp.delete(temp.length() - 2, temp.length() - 1).append(")");

                    reqs[i] = temp.toString();
                }
            }

            if (anyInvites) {
                return reqs;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public int getTotalWarps() {
        int ret = 0;
        for (HashMap<String, Home> pList : homeList.values()) {
            ret += pList.size();
        }
        return ret;
    }

    private MatchList getMatches(String name, Player player, String owner) {
        ArrayList<Home> exactMatches = new ArrayList<Home>();
        ArrayList<Home> matches = new ArrayList<Home>();

        if (!this.hasHomes(owner)) {
            return new MatchList(exactMatches, matches);
        }

        List<String> names = new ArrayList<String>(homeList.get(owner).keySet());
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.SECONDARY);
        Collections.sort(names, collator);

        for (int i = 0; i < names.size(); i++) {
            String currName = names.get(i);
            Home warp = homeList.get(owner).get(currName);
            if (warp.playerCanWarp(player)) {
                if (warp.name.equalsIgnoreCase(name)) {
                    exactMatches.add(warp);
                } else if (warp.name.toLowerCase().contains(name.toLowerCase())) {
                    matches.add(warp);
                }
            }
        }
        if (exactMatches.size() > 1) {
            for (Home warp : exactMatches) {
                if (!warp.name.equals(name)) {
                    exactMatches.remove(warp);
                    matches.add(0, warp);
                }
            }
        }
        return new MatchList(exactMatches, matches);
    }

    public Home getHomeFor(Player player) {
        return homeList.get(player.getName()).get(uHome.DEFAULT_HOME);
    }

    public static enum ExitStatus {
        SUCCESS,
        NOT_EXISTS,
        NOT_PERMITTED,
        UNKNOWN;
    }
}

class MatchList {

    public MatchList(ArrayList<Home> exactMatches, ArrayList<Home> matches) {
        this.exactMatches = exactMatches;
        this.matches = matches;
    }
    public ArrayList<Home> exactMatches;
    public ArrayList<Home> matches;

    public String getMatch(String name) {
        if (exactMatches.size() == 1) {
            return exactMatches.get(0).name;
        }
        if (exactMatches.isEmpty() && matches.size() == 1) {
            return matches.get(0).name;
        }
        return name;
    }
}
