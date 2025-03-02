package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmaFrequencyAnalyzer {
    private static final Set<Character> PUNCTUATION_MARKS = Set.of(
            '!', ',', '.', ':', ';', '?',   // Базовые знаки
            '\'', '"',                       // Кавычки
            '(', ')', '[', ']', '{', '}',    // Скобки
            '-', '–', '—',                   // Дефис, тире среднее и длинное
            '«', '»', '“', '”', '‘', '’',   // Разные типы кавычек
            '/', '\\', '|',                  // Слэши и черта
            '…'                              // Многоточие (один символ Unicode)
    );
    private static final Set<String> FUNCTIONAL_PART_OF_SPEECH = Set.of(
            "МС","МЕЖД","СОЮЗ", "ПРЕДЛ", "ЧАСТ"
    );

    public static Map<String, Integer> frequencyMap(String text) {
        List<String> wordBaseForms = new ArrayList<>();
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> wordsList = LemmaFrequencyAnalyzer.createWordList(text);
            for (String word : wordsList) {
                String normalFormStr = luceneMorph.getNormalForms(word).toString();
                if (isIndependentPartOfSpeech(luceneMorph.getMorphInfo(word))) {
                    wordBaseForms.add(normalFormStr.substring(1, normalFormStr.indexOf(']')));
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибочка");
        } catch (NullPointerException e) {
            System.out.println("NPE");
        }
        return countFrequency(wordBaseForms);
    }

    private static List<String> createWordList(String text){
        List<String> words = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char nextChar = text.charAt(i);
            if (nextChar == ' ') {
                words.add(stringBuilder.toString());
                stringBuilder = new StringBuilder();
            } else if (!PUNCTUATION_MARKS.contains(nextChar)) {
                stringBuilder.append(nextChar);
            }
        }
        words.add(stringBuilder.toString().toLowerCase());
        return words;
    }

    private static boolean isIndependentPartOfSpeech(List<String> wordList) {
        String wordStr = wordList.toString();
        return FUNCTIONAL_PART_OF_SPEECH.stream().noneMatch(wordStr::contains);
    }

    private static Map<String, Integer> countFrequency(Collection<String> collection) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String item : collection) {
            frequencyMap.merge(item, 1, Integer::sum);
        }
        return frequencyMap;
    }
}


