package com.demigodsrpg.chitchatbot;

import com.demigodsrpg.chitchat.Chitchat;
import com.demigodsrpg.chitchatbot.ai.Brain;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Bot implements Listener {
    private static final Random RANDOM = new Random();
    private static final String SAVE_PATH = BotPlugin.INST.getDataFolder().getPath() + "/bots/";
    private final Cache<String, Integer> SPAM_CACHE = CacheBuilder.newBuilder().
            expireAfterWrite(8, TimeUnit.SECONDS).
            build();

    private final List<String> listensTo;
    private final Brain brain;
    private final String name, prefix;
    private final boolean talks;
    private final long freqTicks;
    private final double challengeChance;
    private final boolean requiresPerm;
    private final String permission;

    public Bot(String name, String prefix, boolean talks, long freqTicks, double challengeChance, int wordLimit,
               boolean requiresPerm, String... listensTo) {
        this(name, prefix, talks, freqTicks, challengeChance, wordLimit, requiresPerm, Arrays.asList(listensTo));
    }

    public Bot(String name, String prefix, boolean talks, long freqTicks, double challengeChance, int wordLimit,
               boolean requiresPerm, List<String> listensTo) {
        this.name = name;
        this.prefix = prefix;
        this.talks = talks;
        this.freqTicks = freqTicks;
        this.challengeChance = challengeChance;
        this.requiresPerm = requiresPerm;
        this.permission = "chitchatbot." + name.toLowerCase();
        this.listensTo = listensTo;
        this.brain = tryLoadFromFile(wordLimit);
        brain.setLastPlayer("");
        brain.setLastTime(0L);
        brain.setLastMessage("");
    }

    public Brain getBrain() {
        return brain;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean getTalks() {
        return talks;
    }

    public long getFreqTicks() {
        return freqTicks;
    }

    public double getChallengeChance() {
        return challengeChance;
    }

    public int getSpamAmount(String replyTo) {
        int amount = SPAM_CACHE.asMap().getOrDefault(replyTo, 0);
        SPAM_CACHE.put(replyTo, amount + 1);
        return amount;
    }

    private void createFile(File file) {
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (Exception oops) {
            oops.printStackTrace();
        }
    }

    public void saveToFile() {
        File file = new File(SAVE_PATH + name + ".json");
        if (!(file.exists())) {
            createFile(file);
        }
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(brain);
        try {
            PrintWriter writer = new PrintWriter(file);
            writer.print(json);
            writer.close();
        } catch (Exception oops) {
            oops.printStackTrace();
        }
    }

    public Brain tryLoadFromFile(int wordLimit) {
        Gson gson = new GsonBuilder().create();
        try {
            File file = new File(SAVE_PATH + name + ".json");
            if (file.exists()) {
                FileInputStream inputStream = new FileInputStream(file);
                InputStreamReader reader = new InputStreamReader(inputStream);
                Brain brain = gson.fromJson(reader, Brain.class);
                brain.refresh(wordLimit);
                reader.close();
                return brain;
            }
        } catch (Exception oops) {
            oops.printStackTrace();
        }
        return new Brain(wordLimit);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage().replaceAll("⏎", "");
        boolean learn = (!requiresPerm || event.getPlayer().hasPermission(permission)) &&
                (listensTo.isEmpty() || listensTo.contains(event.getPlayer().getName()));
        if (message.toLowerCase().contains("@" + getName().toLowerCase())) {
            int spamAmount = getSpamAmount(event.getPlayer().getName());
            if (spamAmount < 1) {
                String statement = removeName(message);
                List<String> reply = getBrain().getReply(event.getPlayer().getName(), statement, learn);
                Bukkit.getScheduler().scheduleAsyncDelayedTask(BotPlugin.INST, () -> {
                    if (!reply.isEmpty()) {
                        if (reply.get(0).startsWith("@")) {
                            for (String part : reply) {
                                event.getPlayer().sendMessage(ChatColor.GRAY + "** " + getPrefix() + part);
                            }
                        } else {
                            for (String part : reply) {
                                Chitchat.sendMessage(getPrefix() + part);
                            }
                        }
                    } else {
                        Chitchat.sendMessage(getPrefix() + "beep. boop. beep.");
                    }
                }, 10 * (1 + RANDOM.nextInt(2)));
            } else {
                // Let them know the bot doesn't like spam
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.GRAY + "** " + getName() + " is thinking... **");
            }
        } else if (!message.contains("@")) {
            if (learn) {
                try {
                    if (getBrain().getLastTime() + 7000L > System.currentTimeMillis() && getBrain().getLastPlayer().
                            equals(event.getPlayer().getName())) {
                        getBrain().setLastMessage(getBrain().getLastMessage() + "⏎" + message);
                        getBrain().setLastTime(System.currentTimeMillis());
                    } else {
                        if (!"".equals(getBrain().getLastMessage())) {
                            getBrain().add(getBrain().getLastMessage());
                        }
                        getBrain().setLastMessage(message);
                        getBrain().setLastPlayer(event.getPlayer().getName());
                    }
                } catch (Exception oops) {
                    oops.printStackTrace();
                }
            }
        }
    }

    private String removeName(String message) {
        return message.replaceAll("(?i)(@" + getName().toLowerCase() + ")", "").trim();
    }
}
