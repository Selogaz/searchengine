package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SequentialWordsNumbers {

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
            } else {
                stringBuilder.append(nextChar);
            }
        }
        words.add(stringBuilder.toString());
        return words;
    }
}


