export {
  addWord,
  hangulSplit,
  isHangul,
  WORKSHEET_LIMITS,
  type HangulGlyph,
} from './hangul-util.js';

export {
  score,
  scorePercent,
  summarizeMatches,
  type DictationScoreResult,
  type GlyphMatch,
  type GlyphMatchSummary,
} from './dictation-compare.js';

export {
  JAMO_GROUPS,
  optionsOf,
  type JamoGroup,
  type JamoOption,
} from './jamo-table.js';

export {
  allCodesForMode,
  changeMode,
  EMPTY_HIDE_RULE,
  hiddenPartsOf,
  HIDE_MODE_LABELS,
  isHidableInMode,
  jamoKindOf,
  parseCheckboxValue,
  toggleCodes,
  type HideMode,
  type HideRule,
  type JamoKind,
} from './hide-rules.js';
