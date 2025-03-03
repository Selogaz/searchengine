package searchengine;

import java.util.*;

public class Main {

    private static final String BIG_TEXT = "99 1я а но в не человеку и пауку паук я я. Шелест фd";

    public static void main(String[] args) {
        //String text = LemmaFrequencyAnalyzer.removeHtmlTags(String content);
        Map<String, Integer> frequencyMap = LemmaFrequencyAnalyzer.frequencyMap(BIG_TEXT);
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            System.out.println(entry.getKey() + " - " + entry.getValue());
        }
    }
}


