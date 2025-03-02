package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class Main {

    private static final String BIG_TEXT = "я а но в не человеку и пауку паук я я. Шелест";

    private static final Set<String> FUNCTIONAL_PART_OF_SPEECH = Set.of(
            "МС","МЕЖД","СОЮЗ", "ПРЕДЛ", "ЧАСТ"
    );

    public static void main(String[] args) {
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            List<String> wordBaseForms = new ArrayList<>();
            List<String> wordsList = SequentialWordsNumbers.sequentialWordsNumbers(BIG_TEXT);
            for (String word : wordsList) {
                String normalFormStr = luceneMorph.getNormalForms(word).toString();
                if (isIndependentPartOfSpeech(luceneMorph.getMorphInfo(word))) {
                    wordBaseForms.add(normalFormStr.substring(1, normalFormStr.indexOf(']')));
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

    private static boolean isIndependentPartOfSpeech(List<String> wordList) {
        String wordStr = wordList.toString();
        return FUNCTIONAL_PART_OF_SPEECH.stream().noneMatch(wordStr::contains);
    }

    public static Map<String, Integer> countFrequency(Collection<String> collection) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String item : collection) {
            frequencyMap.merge(item, 1, Integer::sum);
        }
        return frequencyMap;
    }
}


