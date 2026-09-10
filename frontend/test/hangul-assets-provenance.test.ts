import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * 학습지 자산이 **jammin 원본 그대로임을 강제**한다.
 *
 * ## 왜 필요한가 (2026-09-09에 앱에서 실제로 당했다)
 *
 * 초민정음 앱은 자모 SVG 83개를 "원본을 준용한다"고 적어 두고 실제로는 전부 다시
 * 그려 쓰고 있었다 — 좌표가 밀려(ㄱ 기준 2.5%) **같은 문항인데 앱과 웹의 학습지가
 * 달랐다.** 아무도 몰랐던 이유는 자산의 출처를 확인하는 장치가 없었기 때문이다.
 *
 * 웹은 원본을 그대로 복사해 써서 그때는 멀쩡했다. 하지만 "지금 맞다"와 "앞으로도
 * 맞다"는 다르다 — 누군가 최적화한다고 SVG 를 다시 저장하거나(에디터가 좌표를
 * 반올림한다) 로고를 손보면 조용히 갈라진다. 그 순간을 이 테스트가 잡는다.
 *
 * 자모 **분해 로직**에는 골든 벡터(`golden/hangul-split.json`)가 있어 세 이식본이
 * 어긋나면 즉시 깨진다. 이건 **자산**에 대한 같은 장치다.
 *
 * ⚠️jammin 은 별도 저장소라 항상 옆에 있지 않다. 없으면 건너뛴다.
 */

const JAMMIN = '/Users/seunghyun.oh/Workspace/Private/jammin/src/main/resources/static'
const PUBLIC = join(__dirname, '..', 'public')

const sha = (p: string) => createHash('sha256').update(readFileSync(p)).digest('hex')

describe('학습지 자산 출처 — jammin 원본과 동일해야 한다', () => {
  const available = existsSync(JAMMIN)
  const only = available ? it : it.skip

  only('자모 SVG 83개가 원본과 바이트까지 같다', () => {
    const dir = join(PUBLIC, 'hangul')
    const files = readdirSync(dir).filter((f) => f.endsWith('.svg')).sort()

    expect(files).toHaveLength(83)

    const differ: string[] = []
    const missing: string[] = []
    for (const f of files) {
      const origin = join(JAMMIN, 'hangul', f)
      if (!existsSync(origin)) {
        missing.push(f)
        continue
      }
      if (sha(join(dir, f)) !== sha(origin)) differ.push(f)
    }

    expect(missing, '원본에 없는 자산 — 손으로 만든 것이다').toEqual([])
    expect(differ, '원본과 다른 자산 — 다시 그렸거나 다시 저장했다').toEqual([])
  })

  only('상단 로고가 원본과 같다', () => {
    // 앱은 이 파일을 손으로 그린 SVG 재현물로 갖고 있었다(2026-09-09 교체).
    expect(sha(join(PUBLIC, 'navbar-logo.png'))).toBe(
      sha(join(JAMMIN, 'img', 'navbar-logo.png')),
    )
  })

  if (!available) {
    it('jammin 원본이 없어 자산 출처 검사를 건너뛴다', () => {
      expect(available).toBe(false)
    })
  }
})
