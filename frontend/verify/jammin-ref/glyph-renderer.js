import { PROFILES } from "./profiles.js";

function escapeAttr(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function specialHeight(code, profile) {
  const key = String(code);
  const m = profile.specialHeights;
  if (m[key] != null) return m[key];
  return m.default;
}

function renderGlyphBody(item, profile) {
  if (item.specialFlag) {
    const h = specialHeight(item.specialTypeCode, profile);
    const st = `height: ${h}px;position: absolute;top: 2px;left: 2px;`;
    return `<div class='special' style='position:absolute;'><img class='hangul kccdodamdodma-font' src='hangul/${item.specialTypeCode}.svg' style='${st}'></div>`;
  }
  const cho = profile.cho;
  const jung = profile.jung;
  const jong = profile.jong;
  let html = `<div class='cho'><img class='hangul kccdodamdodma-font cho ${item.choCode}' src='hangul/${item.choCode}.svg' style='height: ${cho.height}px;position:absolute;top: ${cho.top}px;z-index: ${cho.zIndex};' data-hw-action='toggle-glyph'></div>`;
  html += `<div class='jung'><img class='hangul kccdodamdodma-font jung ${item.jungCode}' src='hangul/${item.jungCode}.svg' style='height: ${jung.height}px;position:absolute;top: ${jung.top}px;' data-hw-action='toggle-glyph'></div>`;
  if (item.jongsung) {
    html += `<div class='jong'><img class='hangul kccdodamdodma-font jong ${item.jongCode}' src='hangul/${item.jongCode}.svg' style='height: ${jong.height}px;position:absolute;top: ${jong.top}px;' data-hw-action='toggle-glyph'></div>`;
  }
  return html;
}

function renderBackground(profile) {
  const b = profile.background;
  return `<div class='hangul-background'><img class='hangul-background-element' src='${b.src}' style='height: ${b.height}px;position:absolute;top: ${b.top}px;left: ${b.left}px;'></div>`;
}

function renderToolbar(toolbar) {
  const orderUpExtra = toolbar === "fullIndented" ? " style='margin-left:40px;'" : "";
  let html = "";
  if (toolbar === "full" || toolbar === "fullIndented") {
    html += `<button class='btn rounded-0 btn-orderup' type='button' title='Up' data-hw-action='order-up'${orderUpExtra}><i class='fas fa-arrow-up'></i></button>`;
    html += `<button class='btn rounded-0 btn-orderdown' type='button' title='Down' data-hw-action='order-down'><i class='fas fa-arrow-down'></i></button>`;
  }
  html += `<button class='btn rounded-0 btn-edit' type='button' title='Edit' data-bs-toggle='modal' data-bs-target='#editWord' data-hw-action='edit-ready'><i class='fa fa-edit'></i></button>`;
  html += `<button class='btn rounded-0 btn-delete' type='button' title='Delete' data-hw-action='delete-row'><i class='fa fa-trash'></i></button>`;
  return html;
}

export function buildHangulWrapperHtml(opts) {
  const { jsonArray, fullWord, ordering, profileKey, toolbar, rowWrapperStyle } = opts;
  const profile = PROFILES[profileKey];
  if (!profile) throw new Error("Unknown profile: " + profileKey);

  const lb = profile.lineBreakCount;
  const rw = rowWrapperStyle !== undefined ? rowWrapperStyle : "width:100%;";

  let html = `<div class='hangulWrapper d-flex' style='width:100%;' ordering='${ordering}'>`;
  html += `<input class='fullWord' type='hidden' value='${escapeAttr(fullWord)}'>`;
  html += `<div class='hangul-row-Wrapper' style='${rw}'>`;

  for (let i = 0; i < jsonArray.length; i++) {
    if (i % lb === 0) {
      html += `<div class='row hangul-row' style='height:${profile.rowHeight}px;'>`;
    }
    html += `<div class='hangulSet' style='width:${profile.cellWidth}px; height:${profile.cellHeight}px; position: relative;'>`;
    html += renderBackground(profile);
    html += renderGlyphBody(jsonArray[i], profile);
    html += `<div class='hangulFullWord' style='width:70px; height:100px;'></div>`;
    html += `</div>`;
    if (i % lb === lb - 1 || i === jsonArray.length - 1) {
      html += `</div>`;
    }
  }
  html += `</div>`;
  html += renderToolbar(toolbar);
  html += `</div>`;
  return html;
}
