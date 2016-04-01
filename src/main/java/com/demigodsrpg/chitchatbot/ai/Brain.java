package com.demigodsrpg.chitchatbot.ai;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Brain implements Serializable {
    public transient int LIMIT;
    
    // These are valid chars for words. Anything else is treated as punctuation.
    public static final String WORD_CHARS = "abcdefghijklmnopqrstuvwxyz" +
                                            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                                            "0123456789";
    public static final String END_CHARS = ".!?⏎";
    public static final List<String> COMMON_WORDS = Arrays.asList(
            "the", "of", "to", "and", "a", "in", "is", "it", "you", "that", "he", "was", "for", "on", "are", "with",
            "as", "I", "his", "they", "be", "at", "one", "have", "this", "from", "or", "had", "by", "no", "but", "some",
            "what", "there", "we", "can", "out", "other", "were", "all", "your", "when", "up", "use", "yes", "hot"
    );

    // Map of challenges to other players
    private transient Cache<String, List<String>> CHALLENGES;

    // This maps an Id to a Quad
    Map<String, Quad> QUADS = new ConcurrentHashMap<>();

    // This maps a single word to a Set of all the Quads it is in.
    Map<String, Set<String>> WORDS = new ConcurrentHashMap<>();

    // This maps a Quad onto a Set of Strings that may come next.
    Map<String, Set<String>> NEXT = new ConcurrentHashMap<>();

    // This maps a Quad onto a Set of Strings that may come before it.
    Map<String, Set<String>> PREVIOUS = new ConcurrentHashMap<>();

    // Random
    private transient Random RANDOM;

    // Multi-line stuff
    private transient String lastPlayer;
    private transient Long lastTime;
    private transient String lastMessage;
    
    /**
     * Construct an empty brain.
     */
    @SuppressWarnings("ConstantConditions")
    public Brain(int wordLimit) {
        refresh(wordLimit);
    }
    
    /**
     * Adds an entire documents to the 'brain'.  Useful for feeding in
     * stray theses, but be careful not to put too much in, or you may
     * run out of memory!
     */
    public void addDocument(String uri) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(uri).openStream()));
        String str = "";
        int ch;
        while ((ch = reader.read()) != -1) {
            str += ch;
            if (END_CHARS.indexOf((char) ch) >= 0) {
                String sentence = str;
                sentence = sentence.replace('\r', ' ');
                sentence = sentence.replace('\n', ' ');
                add(sentence);
                str = "";
            }
        }
        add(str);
        reader.close();
    }
    
    /**
     * Adds a new sentence to the 'brain'
     */
    public List<String> add(String sentence) {
        if(WORDS.size() >= LIMIT) {
            return null;
        }

        List<String> quads = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        sentence = sentence.trim();
        char[] chars = sentence.toCharArray();

        boolean punctuation = false;
        String str = "";

        for (char ch : chars) {
            if ((WORD_CHARS.indexOf(ch) >= 0) == punctuation) {
                punctuation = !punctuation;
                if (str.length() > 0) {
                    parts.add(str);
                }
                str = "";
            }
            str += ch;
        }
        if (str.length() > 0) {
            parts.add(str);
        }

        if (parts.size() >= 4) {
            for (int i = 0; i < parts.size() - 3; i++) {
                Quad quad = new Quad(parts.get(i), parts.get(i + 1), parts.get(i + 2), parts.get(i + 3));
                if (QUADS.containsKey(quad.getId())) {
                    quad = QUADS.get(quad.getId());
                } else if (quad.isValid()) {
                    QUADS.put(quad.getId(), quad);
                } else {
                    continue;
                }
                quads.add(quad.getId());

                if (i == 0) {
                    quad.setCanStart(true);
                }
                //else if (i == parts.size() - 4) {
                if (i == parts.size() - 4) {
                    quad.setCanEnd(true);
                }
                
                for (int n = 0; n < 4; n++) {
                    String token = parts.get(i + n);
                    if (!WORDS.containsKey(token)) {
                        WORDS.put(token, new HashSet<>(1));
                    }
                    WORDS.get(token).add(quad.getId());
                }
                
                if (i > 0) {
                    String previousToken = parts.get(i - 1);
                    if (!PREVIOUS.containsKey(quad.getId())) {
                        PREVIOUS.put(quad.getId(), new HashSet<>(1));
                    }
                    PREVIOUS.get(quad.getId()).add(previousToken);
                }
                
                if (i < parts.size() - 4) {
                    String nextToken = parts.get(i + 4);
                    if (!NEXT.containsKey(quad.getId())) {
                        NEXT.put(quad.getId(), new HashSet<>(1));
                    }
                    NEXT.get(quad.getId()).add(nextToken);
                }
            }
        }
        return quads;
    }

    /**
     * Generate a statement to challenge a response from a given name, from the brain.
     */
    public List<String> challenge(String name) {
        List<List<String>> pack = getSentence();
        CHALLENGES.put(name, pack.get(0));
        List<String> sentence = pack.get(1);
        sentence.set(0, "@" + name + " " + sentence.get(0));
        return sentence;
    }

    /**
     * Generate a reply sentence from the brain.
     */
    public List<String> getReply(String name, String statement, boolean learn) {
        if (statement != null) {
            List<Quad> given = searchForQuads(statement).stream().map(QUADS::get).collect(Collectors.toList());
            List<String> quadIds = new ArrayList<>();
            LinkedList<String> parts = new LinkedList<>();

            if (given.size() < 2) {
                return getSentence().get(1);
            }

            List<Quad> quads = new ArrayList<>(getRelated(given));
            boolean tooSmall = quads.size() < 4;
            if (tooSmall) {
                quads = new ArrayList<>(QUADS.values());
            }

            if (quads.isEmpty()) {
                return new ArrayList<>();
            }

            Quad middleQuad = quads.get(RANDOM.nextInt(quads.size()));
            Quad quad = middleQuad;

            for (int i = 0; i < 4; i++) {
                parts.add(quad.getToken(i));
            }

            while (!quad.canEnd()) {
                quadIds.add(quad.getId());
                List<String> nextTokens = new ArrayList<>(NEXT.get(quad.getId()));
                String nextToken = nextTokens.get(RANDOM.nextInt(nextTokens.size()));
                quad = QUADS.get(new Quad(quad.getToken(1), quad.getToken(2), quad.getToken(3), nextToken).getId());
                parts.add(nextToken);
            }

            quad = middleQuad;
            while (!quad.canStart()) {
                quadIds.add(quad.getId());
                List<String> previousTokens = new ArrayList<>(PREVIOUS.get(quad.getId()));
                String previousToken = previousTokens.get(RANDOM.nextInt(previousTokens.size()));
                quad = QUADS.get(new Quad(previousToken, quad.getToken(0), quad.getToken(1), quad.getToken(2)).getId());
                parts.addFirst(previousToken);
            }

            List<String> sentence = new ArrayList<>();
            String part = "@" + name + " ";
            for (String token : parts) {
                part += token;
                if (token.contains("⏎")) {
                    sentence.add(part.trim());
                    part = "";
                }
            }
            if (!"".equals(part)) {
                sentence.add(part);
            }

            if (learn) {
                // Handle challenges
                if (!tooSmall && CHALLENGES.asMap().containsKey(name)) {
                    List<String> related = CHALLENGES.asMap().get(name);
                    for (String relatedId : related) {
                        Quad relatedQuad = QUADS.get(relatedId);
                        for (Quad found : given) {
                            relatedQuad.addRelated(found.getId());
                            found.addRelated(relatedQuad.getId());
                            QUADS.put(relatedId, relatedQuad);
                            QUADS.put(found.getId(), found);
                        }
                    }
                    CHALLENGES.put(name, quadIds);
                } else {
                    add(statement);
                    CHALLENGES.put(name, quadIds);
                }
            }

            return sentence;
        }

        // If all else fails, return a random sentence
        return getSentence().get(1);
    }
    
    /**
     * Generate a random sentence from the brain.
     */
    public List<List<String>> getSentence() {
        return getSentence(null);
    }
    
    /**
     * Generate a sentence that includes (if possible) the specified word.
     */
    public List<List<String>> getSentence(String word) {
        if (word != null && COMMON_WORDS.contains(word.toLowerCase())) {
            return getSentence();
        }

        List<String> quadIds = new ArrayList<>();
        LinkedList<String> parts = new LinkedList<>();

        List<Quad> quads;
        if (word != null && WORDS.containsKey(word)) {
            quads = new ArrayList<>(WORDS.get(word).stream().map(QUADS::get).collect(Collectors.toList()));
        }
        else {
            quads = new ArrayList<>(QUADS.values());
        }

        if (quads.isEmpty()) {
            return new ArrayList<>();
        }

        Quad middleQuad = quads.get(RANDOM.nextInt(quads.size()));
        Quad quad = middleQuad;
        
        for (int i = 0; i < 4; i++) {
            parts.add(quad.getToken(i));
        }
        
        while (!quad.canEnd()) {
            quadIds.add(quad.getId());
            List<String> nextTokens = new ArrayList<>(NEXT.get(quad.getId()));
            String nextToken = nextTokens.get(RANDOM.nextInt(nextTokens.size()));
            quad = QUADS.get(new Quad(quad.getToken(1), quad.getToken(2), quad.getToken(3), nextToken).getId());
            parts.add(nextToken);
        }
        
        quad = middleQuad;
        while (!quad.canStart()) {
            quadIds.add(quad.getId());
            List<String> previousTokens = new ArrayList<>(PREVIOUS.get(quad.getId()));
            String previousToken = previousTokens.get(RANDOM.nextInt(previousTokens.size()));
            quad = QUADS.get(new Quad(previousToken, quad.getToken(0), quad.getToken(1), quad.getToken(2)).getId());
            parts.addFirst(previousToken);
        }

        List<String> sentence = new ArrayList<>();
        String part = "";
        for (String token : parts) {
            part += token;
            if (token.contains("⏎")) {
                sentence.add(part.trim());
                part = "";
            }
        }
        if (!"".equals(part)) {
            sentence.add(part);
        }

        List<List<String>> pack = new ArrayList<>();
        pack.add(quadIds);
        pack.add(sentence);
        return pack;
    }

    /**
     * Attempt to find learned quads from a given sentence.
     */
    public List<String> searchForQuads(String sentence) {
        List<String> quads = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        sentence = sentence.trim();
        char[] chars = sentence.toCharArray();

        boolean punctuation = false;
        String str = "";

        for (char ch : chars) {
            if ((WORD_CHARS.indexOf(ch) >= 0) == punctuation) {
                punctuation = !punctuation;
                if (str.length() > 0) {
                    parts.add(str);
                }
                str = "";
            }
            str += ch;
        }
        if (str.length() > 0) {
            parts.add(str);
        }

        if (parts.size() >= 4) {
            for (int i = 0; i < parts.size() - 3; i++) {
                Quad quad = new Quad(parts.get(i), parts.get(i + 1), parts.get(i + 2), parts.get(i + 3));
                Optional<Quad> existing = QUADS.values().stream().filter(quad::nearMatch).findAny();
                if (existing.isPresent()) {
                    quads.add(existing.get().getId());
                }
            }
        }
        return quads;
    }

    public Long getLastTime() {
        return lastTime;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getLastPlayer() {
        return lastPlayer;
    }

    public void setLastTime(Long lastTime) {
        this.lastTime = lastTime;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public void setLastPlayer(String lastPlayer) {
        this.lastPlayer = lastPlayer;
    }

    /**
     * Purge the brain of all data.
     */
    public void purge() {
        WORDS.clear();
        QUADS.clear();
        NEXT.clear();
        PREVIOUS.clear();
    }

    public void refresh(int wordLimit) {
        LIMIT = wordLimit;
        CHALLENGES = CacheBuilder.newBuilder().expireAfterWrite(20, TimeUnit.SECONDS).build();
        RANDOM = new Random();
        lastPlayer = "";
        lastTime = 0L;
        lastMessage = "";
    }

    public List<Quad> getRelated(List<Quad> start) {
        Set<String> quads = new HashSet<>(start.stream().map(Quad::getId).collect(Collectors.toSet()));
        for (Quad quad : start) { // TODO This may be very slow
            quads.addAll(quad.getRelated());
            for (String related : quad.getRelated()) {
                Quad relatedQuad = QUADS.get(related);
                quads.addAll(relatedQuad.getRelated());
            }
        }
        return quads.stream().map(QUADS::get).collect(Collectors.toList());
    }
}