package com.chominjungum.hangul;

import java.util.ArrayList;
import java.util.List;

/**
 * jammin {@code com.jammin.util.HangulUtil} 의 복사본 (로깅 제거, 정적 메서드화).
 *
 * <p><b>계산식과 허용 문자 집합은 한 글자도 바꾸지 않는다.</b>
 * 동치성은 {@code golden/hangul-split.json} 으로 검증한다.
 *
 * <p>원본: jammin/src/main/java/com/jammin/util/HangulUtil.java (수정 금지 저장소)
 */
public final class HangulUtil {

    private static final char[] SPECIAL_TYPE_ARRAY = {' ', '.', ',', '?', '!'};
    private static final char[] NUMBER_TYPE_ARRAY = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0'};

    private HangulUtil() {
    }

    public static List<Hangul> hangulSplit(String s) {
        List<Hangul> hangulList = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            Hangul hangul = new Hangul();
            if (isSpecialType(s.charAt(i)) || isNumberType(s.charAt(i))) {
                hangul.setSpecialFlag(true);
                hangul.setSpecialType(String.valueOf(s.charAt(i)));
                hangul.setSpecialTypeCode(s.charAt(i));
                hangulList.add(hangul);
                continue;
            }
            hangul.setWord(String.valueOf(s.charAt(i)));
            char comVal = (char) (s.charAt(i) - 0xAC00);
            if (comVal >= 0 && comVal <= 11172) {
                char uniVal = comVal;
                char cho = (char) ((((uniVal - (uniVal % 28)) / 28) / 21) + 0x1100);
                char jung = (char) ((((uniVal - (uniVal % 28)) / 28) % 21) + 0x1161);
                char jong = (char) ((uniVal % 28) + 0x11a7);
                if (cho != 4519) {
                    hangul.setChosung(String.valueOf(cho));
                    hangul.setChoCode(cho);
                }
                if (jung != 4519) {
                    hangul.setJungsung(String.valueOf(jung));
                    hangul.setJungCode(jung);
                }
                if (jong != 4519) {
                    hangul.setJongsung(String.valueOf(jong));
                    hangul.setJongCode(jong);
                }
                hangul.setJongsungEmpty(hangul.getJongsung() == null);
                hangul.setErrorFlag(false);
            } else {
                hangul.setErrorFlag(true);
            }
            hangulList.add(hangul);
        }
        return hangulList;
    }

    public static boolean isHangul(String str) {
        boolean checkFlag = true;
        for (char c : str.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || isSpecialType(c)
                    || isNumberType(c)) {
                continue;
            }
            checkFlag = false;
        }
        return checkFlag;
    }

    /** jammin {@code POST /addWord} 와 동일 — 허용 밖 문자가 있으면 빈 목록. */
    public static List<Hangul> addWord(String requestWord) {
        if (!isHangul(requestWord)) {
            return List.of();
        }
        return hangulSplit(requestWord);
    }

    private static boolean isSpecialType(char c) {
        for (char specialType : SPECIAL_TYPE_ARRAY) {
            if (specialType == c) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumberType(char c) {
        for (char numberType : NUMBER_TYPE_ARRAY) {
            if (numberType == c) {
                return true;
            }
        }
        return false;
    }
}
