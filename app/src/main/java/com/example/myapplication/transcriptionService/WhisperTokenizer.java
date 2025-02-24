package com.example.myapplication.transcriptionService;

import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class WhisperTokenizer {
    private static final String TAG = "SimpleWhisperTokenizer";

    private Map<String, Integer> vocab;
    private Map<Integer, String> reverseVocab;
    private Set<Integer> specialTokensIds;
    private JSONObject normalizerConfig;
    private List<Pair<String, String>> bpeMerges;
    private Map<Pair<String, String>, Integer> bpeRanks;

    public WhisperTokenizer(String tokenizerPath) throws IOException, JSONException {
        // Initialize collections
        vocab = new HashMap<>();
        reverseVocab = new HashMap<>();
        specialTokensIds = new HashSet<>();
        bpeRanks = new HashMap<>();

        // Load vocabulary
        File vocabFile = new File(tokenizerPath, "vocab.json");
        JSONObject vocabJson = new JSONObject(readFile(vocabFile));
        Iterator<String> keys = vocabJson.keys();
        while (keys.hasNext()) {
            String token = keys.next();
            int tokenId = vocabJson.getInt(token);
            vocab.put(token, tokenId);
            reverseVocab.put(tokenId, token);
        }

        // Load special tokens
        File specialTokensFile = new File(tokenizerPath, "special_tokens_map.json");
        JSONObject specialTokensMap = new JSONObject(readFile(specialTokensFile));
        Iterator<String> tokenTypes = specialTokensMap.keys();
        while (tokenTypes.hasNext()) {
            String tokenType = tokenTypes.next();
            Object tokenValue = specialTokensMap.get(tokenType);

            if (tokenValue instanceof String) {
                Integer tokenId = vocab.get(tokenValue);
                if (tokenId != null) {
                    specialTokensIds.add(tokenId);
                }
            } else if (tokenValue instanceof JSONObject) {
                JSONObject tokenObj = (JSONObject) tokenValue;
                if (tokenObj.has("content")) {
                    String content = tokenObj.getString("content");
                    Integer tokenId = vocab.get(content);
                    if (tokenId != null) {
                        specialTokensIds.add(tokenId);
                    }
                }
            }
        }

        // Load normalizer config
        try {
            File normalizerFile = new File(tokenizerPath, "normalizer.json");
            normalizerConfig = new JSONObject(readFile(normalizerFile));
        } catch (IOException e) {
            Log.w(TAG, "Normalizer file not found. Using basic normalization.");
            normalizerConfig = null;
        }

        // Load BPE merges
        try {
            File mergesFile = new File(tokenizerPath, "merges.txt");
            List<String> mergesRaw = readLines(mergesFile);
            bpeMerges = new ArrayList<>();

            // Skip the first line and process the rest
            for (int i = 1; i < mergesRaw.size(); i++) {
                String line = mergesRaw.get(i).trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split("\\s+");
                    if (parts.length == 2) {
                        Pair<String, String> mergePair = new Pair<>(parts[0], parts[1]);
                        bpeMerges.add(mergePair);
                        bpeRanks.put(mergePair, i - 1);
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Merges file not found. Assuming non-BPE tokenization.");
            bpeMerges = null;
        }
    }

    public String decode(List<Integer> tokenIds, boolean skipSpecialTokens) {
        List<String> detokenizedTokens = new ArrayList<>();

        for (Integer tokenId : tokenIds) {
            String token = reverseVocab.get(tokenId);
            if (token != null) {
                if (skipSpecialTokens && specialTokensIds.contains(tokenId)) {
                    continue;
                }
                detokenizedTokens.add(token);
            } else {
                detokenizedTokens.add(String.format("[UNK_ID:%d]", tokenId));
            }
        }

        // Handle spaces - replace 'Ġ' with a space
        String text = String.join("", detokenizedTokens).replace("Ġ", " ").trim();

        // Apply BPE reverse merges if available
        if (bpeMerges != null) {
            String[] words = text.split(" ");
            for (int i = 0; i < words.length; i++) {
                words[i] = bpeDecode(words[i]);
            }
            text = String.join(" ", words);
        }

        // Basic cleaning
        return text.replaceAll("\\s+", " ").trim();
    }

    private String bpeDecode(String token) {
        List<String> word = new ArrayList<>();
        for (char c : token.toCharArray()) {
            word.add(String.valueOf(c));
        }

        while (true) {
            Set<Pair<String, String>> pairs = getPairs(word);
            if (pairs.isEmpty()) {
                break;
            }

            int minRank = Integer.MAX_VALUE;
            Pair<String, String> bestPair = null;

            for (Pair<String, String> pair : pairs) {
                Integer rank = bpeRanks.get(pair);
                if (rank != null && rank < minRank) {
                    minRank = rank;
                    bestPair = pair;
                }
            }

            if (bestPair == null || minRank == Integer.MAX_VALUE) {
                break;
            }

            // Apply the merge
            List<String> newWord = new ArrayList<>();
            int i = 0;
            while (i < word.size()) {
                int j = indexOf(word, bestPair.first, i);
                if (j == -1) {
                    newWord.addAll(word.subList(i, word.size()));
                    break;
                }
                newWord.addAll(word.subList(i, j));
                if (j < word.size() - 1 && word.get(j + 1).equals(bestPair.second)) {
                    newWord.add(bestPair.first + bestPair.second);
                    i = j + 2;
                } else {
                    newWord.add(bestPair.first);
                    i = j + 1;
                }
            }
            word = newWord;
        }

        return String.join("", word);
    }

    private Set<Pair<String, String>> getPairs(List<String> word) {
        Set<Pair<String, String>> pairs = new HashSet<>();
        for (int i = 0; i < word.size() - 1; i++) {
            pairs.add(new Pair<>(word.get(i), word.get(i + 1)));
        }
        return pairs;
    }

    private int indexOf(List<String> list, String element, int start) {
        for (int i = start; i < list.size(); i++) {
            if (list.get(i).equals(element)) {
                return i;
            }
        }
        return -1;
    }

    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }

    private List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // Simple Pair class implementation
    private static class Pair<F, S> {
        public final F first;
        public final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return Objects.equals(first, pair.first) &&
                    Objects.equals(second, pair.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }
}