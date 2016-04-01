package com.demigodsrpg.chitchatbot;

import com.demigodsrpg.chitchat.Chitchat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
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
                    return new Bot(botName, prefix, talks, freqTicks, challengeChance, wordLimit, listensTo);
                }).collect(Collectors.toList()));

        int count = 0;
        for (Bot bot : BOTS) {
            getServer().getPluginManager().registerEvents(bot, this);
            if (bot.getTalks()) {
                count++;
                getServer().getScheduler().scheduleAsyncRepeatingTask(this, () -> {
                    List<String> sentence;
                    if (randomPercentBool(bot.getChallengeChance())) {
                        sentence = bot.getBrain().challenge(getRandomPlayer().getName());
                    } else {
                        sentence = bot.getBrain().getSentence().get(1);
                    }
                    if (!sentence.isEmpty()) {
                        for (String part : sentence) {
                            Chitchat.sendMessage(bot.getPrefix() + part);
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
            bot.getBrain().purge();
        }
        HandlerList.unregisterAll(this);
        getLogger().info("Brains purged, poor things... :C");
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
