package searchengine;

import java.util.*;

public class Main {

    private static final String BIG_TEXT = "99 1я а но в не человеку и пауку паук я я. Шелест Федорdron";

    public static void main(String[] args) {
        //String text = LemmaFrequencyAnalyzer.removeHtmlTags(String content);
        LemmaFrequencyAnalyzer frequencyAnalyzer = new LemmaFrequencyAnalyzer();
        Map<String, Integer> frequencyMap = frequencyAnalyzer.frequencyMap(BIG_TEXT);
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            System.out.println(entry.getKey() + " - " + entry.getValue());
        }
    }
}


