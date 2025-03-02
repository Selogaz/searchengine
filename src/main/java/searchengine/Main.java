package searchengine;

import java.util.*;

public class Main {

    private static final String BIG_TEXT = "я а но в не человеку и пауку паук я я. Шелест";

    public static void main(String[] args) {
        Map<String, Integer> frequencyMap = LemmaFrequencyAnalyzer.frequencyMap(BIG_TEXT);
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            System.out.println(entry.getKey() + " - " + entry.getValue());
        }
    }
}


