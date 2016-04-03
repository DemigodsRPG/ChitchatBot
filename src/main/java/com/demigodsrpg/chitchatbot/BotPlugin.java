package com.demigodsrpg.chitchatbot;

import com.demigodsrpg.chitchat.Chitchat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public class BotPlugin extends JavaPlugin {
    public static BotPlugin INST;
    private List<Bot> BOTS = new ArrayList<>();

    @Override
    @SuppressWarnings("deprecation")
    public void onEnable() {
        INST = this;

        saveDefaultConfig();

        BOTS.addAll(getConfig().getConfigurationSection("bots").getKeys(false).stream().
                map(botName -> {
                    String prefix = ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("bots." + botName + ".prefix", ""));
                    boolean talks = getConfig().getBoolean("bots." + botName + ".talks", false);
                    long freqTicks = getConfig().getLong("bots." + botName + ".freqTicks", 7000);
                    double challengeChance = getConfig().getDouble("bots." + botName + ".challengeChance", 0.3);
                    int wordLimit = getConfig().getInt("bots." + botName + ".wordlimit", 10000);
                    List<String> listensTo = getConfig().getStringList("bots." + botName + ".listensTo");
                    boolean requiresPerm = getConfig().getBoolean("bots." + botName + ".requires_perm", false);
                    Permission perm = new Permission("chitchatbot.bot." + botName.toLowerCase(), PermissionDefault.FALSE);
                    try {
                        getServer().getPluginManager().addPermission(perm);
                    } catch (Exception ignored) {
                    }
                    return new Bot(botName, prefix, talks, freqTicks, challengeChance, wordLimit, requiresPerm, listensTo);
                }).collect(Collectors.toList()));

        int count = 0;
        for (Bot bot : BOTS) {
            getServer().getPluginManager().registerEvents(bot, this);
            if (bot.getTalks()) {
                count++;
                getServer().getScheduler().scheduleAsyncRepeatingTask(this, () -> {
                    List<String> sentence;
                    if (randomPercentBool(bot.getChallengeChance())) {
                        Player player = getRandomPlayer();
                        sentence = bot.getBrain().challenge(player.getName());
                        if (!sentence.isEmpty()) {
                            for (String part : sentence) {
                                player.sendMessage(ChatColor.GRAY + "** " + bot.getPrefix() + part);
                            }
                        }
                    } else {
                        sentence = bot.getBrain().getSentence().get(1);
                        if (!sentence.isEmpty()) {
                            for (String part : sentence) {
                                Chitchat.sendMessage(bot.getPrefix() + part);
                            }
                        }
                    }
                }, count * bot.getFreqTicks() / 100, bot.getFreqTicks());
            }
        }

        getLogger().info("Brains enabled, ready to chat.");
    }

    @Override
    public void onDisable() {
        for (Bot bot : BOTS) {
            bot.saveToFile();
            bot.purge(false);
        }
        HandlerList.unregisterAll(this);
        getLogger().info("Brains purged, poor things... :C");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("chitchatbot".equals(command.getName())) {
            if (args.length >= 2) {
                Optional<Bot> oBot = BOTS.stream().filter(b -> b.getName().equalsIgnoreCase(args[1])).findAny();
                if (!oBot.isPresent()) {
                    sender.sendMessage(ChatColor.RED + "No such bot exists, try again.");
                }
                Bot bot = oBot.get();
                if ("info".equalsIgnoreCase(args[0])) {
                    if (!sender.hasPermission("chitchatbot.info")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                        return true;
                    }

                    // INFO
                    sender.sendMessage(ChatColor.GREEN + "Info for " + bot.getName() + ":");
                    sender.sendMessage(ChatColor.YELLOW + " - Permission: " + "chitchatbot.bot." + bot.getName().
                            toLowerCase());
                    sender.sendMessage(ChatColor.YELLOW + " - Prefix: " + bot.getPrefix());
                    sender.sendMessage(ChatColor.YELLOW + " - Auto-talks: " + bot.getTalks());
                    if (bot.getTalks()) {
                        sender.sendMessage(ChatColor.YELLOW + " - Talk frequency: " + bot.getFreqTicks());
                    }
                    sender.sendMessage(ChatColor.YELLOW + " - Challenge chance: " + bot.getChallengeChance());
                    sender.sendMessage(ChatColor.YELLOW + " - # of Quads: " + bot.getBrain().size());
                    return true;
                }
                if ("purge".equalsIgnoreCase(args[0])) {
                    if (sender instanceof Player) {
                        sender.sendMessage(ChatColor.RED + "Only the console may use this command.");
                        return true;
                    }

                    bot.purge(true);
                    sender.sendMessage(ChatColor.RED + bot.getName() + " memory purged, backup made.");
                    return true;
                }
            }
            sender.sendMessage(ChatColor.RED + "Invalid arguments.");
            return false;
        }
        return false;
    }

    /*
     * From CensoredLib:
     */

    private static final Random random = new Random();

    /**
     * Returns a boolean whose value is based on the given <code>percent</code>.
     *
     * @param percent the percent chance for true.
     * @return Boolean
     */
    public static boolean randomPercentBool(double percent) {
        if (percent <= 0.0) return false;
        double roll = generateDoubleRange(0.0, 100.0);
        return roll <= percent;
    }

    /**
     * Generates a double with a value between <code>min</code> and <code>max</code>.
     *
     * @param min the minimum value of the integer.
     * @param max the maximum value of the integer.
     * @return Integer
     */
    public static double generateDoubleRange(double min, double max) {
        return (max - min) * random.nextDouble() + min;
    }

    /**
     * Generates an integer with a value between <code>min</code> and <code>max</code>.
     *
     * @param min the minimum value of the integer.
     * @param max the maximum value of the integer.
     * @return Integer
     */
    public static int generateIntRange(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public static Player getRandomPlayer() {
        List<Player> online = new ArrayList<>(Bukkit.getServer().getOnlinePlayers());
        return online.get(generateIntRange(0, online.size() - 1));
    }
}
