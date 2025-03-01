package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class Main {

    private static final String BIG_TEXT = "я а но в не человеку и пауку паук я я";

    private static final Set<String> FUNCTIONAL_PART_OF_SPEECH = Set.of(
            "МС","МЕЖД","СОЮЗ", "ПРЕДЛ", "ЧАСТ"
    );

    public static void main(String[] args) {
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            List<String> wordBaseForms = new ArrayList<>();
            List<String> result = SequentialWordsNumbers.sequentialWordsNumbers(BIG_TEXT);
            for (String s : result) {
                if (isIndependentPartOfSpeech(luceneMorph.getMorphInfo(s))) {
                    wordBaseForms.add(luceneMorph.getNormalForms(s).toString());
                }
            }

            Map<String, Integer> frequencyMap = countFrequency(wordBaseForms);
            for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
                System.out.println(entry.getKey() + " - " + entry.getValue());
            }
        } catch (IOException e) {
            System.out.println("Ошибочка");
        } catch (NullPointerException e) {
            System.out.println("NPE");
        }

    }

    private static boolean isIndependentPartOfSpeech(List<String> word) {
        String lowerWord = word.toString();
        return FUNCTIONAL_PART_OF_SPEECH.stream().noneMatch(lowerWord::contains);
    }

    public static Map<String, Integer> countFrequency(Collection<String> collection) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String item : collection) {
            frequencyMap.merge(item.substring(1,item.indexOf(']')), 1, Integer::sum);
        }
        return frequencyMap;
    }
}


