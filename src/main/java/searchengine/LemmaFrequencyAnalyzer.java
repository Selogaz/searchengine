package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;

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
            "МС","МЕЖД","СОЮЗ", "ПРЕДЛ", "ЧАСТ","CONJ",
            "INT", "PREP", "ARTICLE", "PART"
    );

    public static String removeHtmlTags(String html) {
        return Jsoup.parse(html).text();
    }

    public static Map<String, Integer> frequencyMap(String text) {
        List<String> wordBaseForms = new ArrayList<>();
        try {
            String normalFormStr;
            LuceneMorphology rusLuceneMorph = new RussianLuceneMorphology();
            LuceneMorphology engLuceneMorph = new EnglishLuceneMorphology();
            List<String> wordsList = createWordList(text);
            for (String word : wordsList) {
                if (isRussian(word)) {
                    normalFormStr = rusLuceneMorph.getNormalForms(word).toString();
                    if (isIndependentPartOfSpeech(rusLuceneMorph.getMorphInfo(word))) {
                        wordBaseForms.add(normalFormStr.substring(1, normalFormStr.indexOf(']')));
                    }
                } else if (isDigit(word)) {
                    wordBaseForms.add(word);
                } else {
                    normalFormStr = engLuceneMorph.getNormalForms(word).toString();
                    if (isIndependentPartOfSpeech(engLuceneMorph.getMorphInfo(word))) {
                        wordBaseForms.add(normalFormStr.substring(1, normalFormStr.indexOf(']')));
                    }
                }

            }
        } catch (IOException e) {
            System.out.println("Ошибочка");
        } catch (NullPointerException e) {
            System.out.println("NPE");
        }
        return countFrequency(wordBaseForms);
    }

    private static boolean isRussian(String word) {
        return word.chars()
                .mapToObj(Character.UnicodeBlock::of)
                .anyMatch(Character.UnicodeBlock.CYRILLIC::equals);
    }

    private static boolean isDigit(String word) {
        return word.chars()
                .anyMatch(Character::isDigit);
    }

    private static List<String> createWordList(String text){
        List<String> words = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char nextChar = text.charAt(i);
            if (nextChar == ' ' && !stringBuilder.isEmpty()) {
                words.add(stringBuilder.toString().toLowerCase());
                stringBuilder = new StringBuilder();
            } else if (Character.isDigit(nextChar)) {
                stringBuilder.append(nextChar);
                words.add(stringBuilder.toString());
                stringBuilder = new StringBuilder();
            }
            else if (!PUNCTUATION_MARKS.contains(nextChar)) {
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


