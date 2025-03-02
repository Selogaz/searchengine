package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SequentialWordsNumbers {
    private static final Set<Character> PUNCTUATION_MARKS = Set.of(
            '!', ',', '.', ':', ';', '?',   // Базовые знаки
            '\'', '"',                       // Кавычки
            '(', ')', '[', ']', '{', '}',    // Скобки
            '-', '–', '—',                   // Дефис, тире среднее и длинное
            '«', '»', '“', '”', '‘', '’',   // Разные типы кавычек
            '/', '\\', '|',                  // Слэши и черта
            '…'                              // Многоточие (один символ Unicode)
    );

    public static List<String> sequentialWordsNumbers(String text){
//        LuceneMorphology luceneMorph = null;
//        try {
//            luceneMorph = new RussianLuceneMorphology();
//        } catch (IOException e) {
//            System.out.println("Ошибочка");
//        }
//        assert luceneMorph != null;
//        List<String> wordBaseForms =
//                luceneMorph.getNormalForms("леса");
//        wordBaseForms.forEach(System.out::println);
//
//        for (String word : wordBaseForms) {
//
//        }
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
}


