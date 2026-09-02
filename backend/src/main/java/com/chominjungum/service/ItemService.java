package com.chominjungum.service;

import com.chominjungum.domain.DictationItem;
import com.chominjungum.hangul.Hangul;
import com.chominjungum.hangul.HangulUtil;
import com.chominjungum.repo.DictationItemRepository;
import com.chominjungum.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {

    /** jammin 학습지 제약 — 한 문장 16글자 (worksheet-app.js:157) */
    public static final int MAX_GLYPHS = 16;

    private final DictationItemRepository items;
    private final ObjectMapper objectMapper;

    public ItemService(DictationItemRepository items, ObjectMapper objectMapper) {
        this.items = items;
        this.objectMapper = objectMapper;
    }

    /**
     * 문항 등록. 같은 교사가 같은 문장을 다시 넣으면 기존 문항을 돌려준다(contentHash 기준).
     */
    @Transactional
    public DictationItem create(UUID teacherId, String expectedText, Integer grade, String unit) {
        String text = expectedText == null ? "" : expectedText.trim();
        if (text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "문장을 입력하세요.");
        }

        List<Hangul> glyphs = HangulUtil.addWord(text);
        if (glyphs.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "한글이 아니거나, 완성된 한글 단어가 아닌 문자가 포함되어있습니다.");
        }
        if (glyphs.size() > MAX_GLYPHS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MAX_GLYPHS + "글자 이상의 문장은 추가할 수 없습니다.");
        }

        String hash = sha256(text);
        return items.findByOwnerTeacherIdAndContentHash(teacherId, hash)
                .orElseGet(() -> {
                    DictationItem item = new DictationItem(teacherId, text, toJson(glyphs), hash);
                    item.setGrade(grade);
                    item.setUnit(unit);
                    return items.save(item);
                });
    }

    public List<DictationItem> listByTeacher(UUID teacherId) {
        return items.findByOwnerTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    public DictationItem getOwned(UUID teacherId, UUID itemId) {
        DictationItem item = items.findById(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."));
        if (!item.getOwnerTeacherId().equals(teacherId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "다른 교사의 문항입니다.");
        }
        return item;
    }

    private String toJson(List<Hangul> glyphs) {
        try {
            List<Map<String, Object>> maps = glyphs.stream().map(Hangul::toMap).toList();
            return objectMapper.writeValueAsString(maps);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "문항 직렬화에 실패했습니다.");
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
