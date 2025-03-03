package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.*;

public class LemmaFrequencyAnalyzer {
    private static final Set<Character> PUNCTUATION_MARKS = Set.of(
            '!', ',', '.', ':', ';', '?', '\'', '"', '(', ')',
            '[', ']', '{', '}', '-', '–', '—', '«', '»', '“', '”',
            '‘', '’', '/', '\\', '|', '…'
    );
    private static final Set<String> FUNCTIONAL_PART_OF_SPEECH = Set.of(
            "МС", "МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ", "CONJ",
            "INT", "PREP", "ARTICLE", "PART"
    );

    public static String removeHtmlTags(String html) {
        return Jsoup.parse(html).text();
    }

    public static Map<String, Integer> frequencyMap(String text) {
        List<String> wordBaseForms = new ArrayList<>();
        try {
            LuceneMorphology rusLuceneMorph = new RussianLuceneMorphology();
            LuceneMorphology engLuceneMorph = new EnglishLuceneMorphology();
            List<String> wordsList = createWordList(text);
            for (String word : wordsList) {
                if (word.isEmpty()) continue;
                if (isDigit(word)) {
                    wordBaseForms.add(word);
                } else if (isCyrillic(word)) {
                    processWordWithMorphology(word, rusLuceneMorph, wordBaseForms);
                } else {
                    processWordWithMorphology(word, engLuceneMorph, wordBaseForms);
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка при инициализации морфологического анализатора");
        }
        return countFrequency(wordBaseForms);
    }

    private static void processWordWithMorphology(String word, LuceneMorphology morphology, List<String> wordBaseForms) {
        try {
            List<String> morphInfo = morphology.getMorphInfo(word);
            if (isIndependentPartOfSpeech(morphInfo)) {
                String normalForm = morphology.getNormalForms(word).get(0);
                wordBaseForms.add(normalForm);
            }
        } catch (Exception e) {
            // Игнорируем слова, которые не могут быть обработаны анализатором
        }
    }

    private static boolean isCyrillic(String word) {
        return word.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC);
    }

    private static boolean isDigit(String word) {
        return !word.isEmpty() && word.chars().allMatch(Character::isDigit);
    }

    private static List<String> createWordList(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        CharType lastLangType = null;

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (currentChar == ' ' || PUNCTUATION_MARKS.contains(currentChar)) {
                if (!currentWord.isEmpty()) {
                    words.add(currentWord.toString().toLowerCase());
                    currentWord.setLength(0);
                    lastLangType = null;
                }
                continue;
            }

            CharType currentType = getCharType(currentChar);
            CharType currentLangType = (currentType == CharType.RUSSIAN || currentType == CharType.ENGLISH) ? currentType : null;

            if (currentLangType != null) {
                if (lastLangType != null && lastLangType != currentLangType) {
                    words.add(currentWord.toString().toLowerCase());
                    currentWord.setLength(0);
                }
                lastLangType = currentLangType;
                currentWord.append(currentChar);
            } else {
                if (lastLangType != null) {
                    currentWord.append(currentChar);
                }
            }
        }

        if (!currentWord.isEmpty()) {
            words.add(currentWord.toString().toLowerCase());
        }

        return words;
    }

    private static CharType getCharType(char c) {
        if (Character.isDigit(c)) {
            return CharType.DIGIT;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (block == Character.UnicodeBlock.CYRILLIC) {
            return CharType.RUSSIAN;
        } else if (block == Character.UnicodeBlock.BASIC_LATIN) {
            return CharType.ENGLISH;
        } else {
            return CharType.OTHER;
        }
    }

    private static boolean isIndependentPartOfSpeech(List<String> morphInfo) {
        return morphInfo.stream()
                .noneMatch(info -> FUNCTIONAL_PART_OF_SPEECH.stream().anyMatch(info::contains));
    }

    private static Map<String, Integer> countFrequency(Collection<String> collection) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String item : collection) {
            frequencyMap.put(item, frequencyMap.getOrDefault(item, 0) + 1);
        }
        return frequencyMap;
    }

    private enum CharType {
        RUSSIAN, ENGLISH, DIGIT, OTHER
    }
}