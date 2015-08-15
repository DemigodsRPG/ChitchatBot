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

    public Bot(String name, String prefix, boolean talks, long freqTicks, String... listensTo) {
        this(name, prefix, talks, freqTicks, Arrays.asList(listensTo));
    }

    public Bot(String name, String prefix, boolean talks, long freqTicks, List<String> listensTo) {
        this.name = name;
        this.prefix = prefix;
        this.talks = talks;
        this.freqTicks = freqTicks;
        this.listensTo = listensTo;
        this.brain = tryLoadFromFile();
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

    public Brain tryLoadFromFile() {
        Gson gson = new GsonBuilder().create();
        try {
            File file = new File(SAVE_PATH + name + ".json");
            if (file.exists()) {
                FileInputStream inputStream = new FileInputStream(file);
                InputStreamReader reader = new InputStreamReader(inputStream);
                Brain brain = gson.fromJson(reader, Brain.class);
                reader.close();
                return brain;
            }
        } catch (Exception oops) {
            oops.printStackTrace();
        }
        return new Brain();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        if (message.toLowerCase().contains("@" + getName().toLowerCase())) {
            int spamAmount = getSpamAmount(event.getPlayer().getName());
            if (spamAmount < 1) {
                String[] parts = message.toLowerCase().trim().split("\\s+");
                String word = parts[RANDOM.nextInt(parts.length)];
                String sentence = getBrain().getSentence(!word.toLowerCase().contains("@" + getName().toLowerCase())
                        ? word : null);
                Bukkit.getScheduler().scheduleAsyncDelayedTask(BotPlugin.INST, () -> {
                    if (!"".equals(sentence)) {
                        Chitchat.sendMessage(getPrefix() + sentence);
                    } else {
                        Chitchat.sendMessage(getPrefix() + "01100010 01100101 01100101 01110000 00101110 00100000 01100010 01101111 01101111 01110000 00101110 00100000 01100010 01100101 01100101 01110000 00101110 00100000");
                    }
                }, 10 * (1 + RANDOM.nextInt(2)));
            } else {
                // Let them know the bot doesn't like spam
                event.setCancelled(true);
                event.getPlayer().sendMessage(event.getFormat());
                if (spamAmount % 5 == 0) {
                    Bukkit.getScheduler().scheduleAsyncDelayedTask(BotPlugin.INST, () -> {
                        event.getPlayer().sendMessage(ChatColor.DARK_GRAY + "PM from" + " <" + ChatColor.DARK_AQUA +
                                getName() + ChatColor.DARK_GRAY + ">: " + ChatColor.GRAY + "Please don't spam me. :C");
                    }, 10);
                }
            }
        } else if (!message.contains("@")) {
            if (listensTo.isEmpty()) {
                getBrain().add(message);
            } else if (listensTo.contains(event.getPlayer().getName())) {
                getBrain().add(message);
            }
        }
    }
}
