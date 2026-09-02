package com.chominjungum.hangul;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * jammin {@code com.jammin.util.Hangul} 의 복사본.
 *
 * <p>원본은 json-simple 에 의존하지만 여기서는 {@link Map} 을 돌려주고 Jackson 이 직렬화한다.
 * <b>키 구성과 조건부 생략 규칙은 원본과 동일해야 한다</b> — 골든 벡터로 검증한다.
 *
 * <p>원본: jammin/src/main/java/com/jammin/util/Hangul.java (수정 금지 저장소)
 */
public class Hangul {

    private String word;
    private String chosung;
    private int choCode;
    private String jungsung;
    private int jungCode;
    private String jongsung;
    private int jongCode;
    private String specialType;
    private Boolean errorFlag = false;
    private Boolean emptyJongsung = true;
    private Boolean specialFlag = false;
    private int specialTypeCode;

    public void setErrorFlag(Boolean errorFlag) {
        this.errorFlag = errorFlag;
    }

    public void setSpecialFlag(Boolean specialFlag) {
        this.specialFlag = specialFlag;
    }

    public void setSpecialType(String specialType) {
        this.specialType = specialType;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public void setChosung(String chosung) {
        this.chosung = chosung;
    }

    public void setJungsung(String jungsung) {
        this.jungsung = jungsung;
    }

    public void setJongsung(String jongsung) {
        this.jongsung = jongsung;
    }

    public void setChoCode(int choCode) {
        this.choCode = choCode;
    }

    public void setJungCode(int jungCode) {
        this.jungCode = jungCode;
    }

    public void setJongCode(int jongCode) {
        this.jongCode = jongCode;
    }

    public void setJongsungEmpty(Boolean jongsungFlag) {
        this.emptyJongsung = jongsungFlag;
    }

    public void setSpecialTypeCode(int specialTypeCode) {
        this.specialTypeCode = specialTypeCode;
    }

    public String getWord() {
        return this.word;
    }

    public String getChosung() {
        return this.chosung;
    }

    public String getJungsung() {
        return this.jungsung;
    }

    public String getJongsung() {
        return this.jongsung;
    }

    public int getChoCode() {
        return this.choCode;
    }

    public int getJungCode() {
        return this.jungCode;
    }

    public int getJongCode() {
        return this.jongCode;
    }

    public int getSpecialTypeCode() {
        return this.specialTypeCode;
    }

    public String getSpecialType() {
        return this.specialType;
    }

    public Boolean getSpecialFlag() {
        return this.specialFlag;
    }

    public Boolean getEmptyJongsung() {
        return this.emptyJongsung;
    }

    public Boolean getErrorFlag() {
        return this.errorFlag;
    }

    /** jammin {@code toJSON()} 과 동일한 키 구성. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("specialFlag", this.specialFlag);
        if (this.specialFlag) {
            map.put("specialType", this.specialType);
            map.put("specialTypeCode", this.specialTypeCode);
        } else {
            map.put("word", this.word);
            map.put("chosung", this.chosung);
            map.put("choCode", this.choCode);
            map.put("jungsung", this.jungsung);
            map.put("jungCode", this.jungCode);
            if (!this.emptyJongsung) {
                map.put("jongsung", this.jongsung);
                map.put("jongCode", this.jongCode);
            }
        }
        map.put("emptyJongsung", this.emptyJongsung);
        map.put("errorFlag", this.errorFlag);
        return map;
    }
}
