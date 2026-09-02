package com.chominjungum.web;

import com.chominjungum.hangul.Hangul;
import com.chominjungum.hangul.HangulUtil;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * jammin {@code POST /addWord} 호환 엔드포인트.
 *
 * <p>웹·앱은 평소 온디바이스로 분해하므로 이 API 를 부르지 않는다.
 * 서버 검증·도구·호환성을 위해 남겨둔다.
 */
@RestController
@RequestMapping("/api/hangul")
public class HangulController {

    @PostMapping("/split")
    public List<Map<String, Object>> split(@RequestBody Map<String, Object> body) {
        Object requestWord = body.get("requestWord");
        if (requestWord == null) {
            return List.of();
        }
        List<Hangul> glyphs = HangulUtil.addWord(requestWord.toString());
        return glyphs.stream().map(Hangul::toMap).toList();
    }
}
