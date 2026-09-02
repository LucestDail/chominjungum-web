-- 초민정음 종합 서버 초기 스키마
-- 설계 근거: PLAN.md §6

create extension if not exists "pgcrypto";

-- ── 계정·조직 ────────────────────────────────────────────────
create table teacher (
    id            uuid primary key default gen_random_uuid(),
    email         varchar(255) not null unique,
    password_hash varchar(255) not null,
    display_name  varchar(100) not null,
    created_at    timestamptz  not null default now()
);

create table classroom (
    id          uuid primary key default gen_random_uuid(),
    teacher_id  uuid         not null references teacher (id) on delete cascade,
    name        varchar(100) not null,
    grade       int,
    school_year int,
    join_code   varchar(12)  not null unique,
    created_at  timestamptz  not null default now()
);

create index idx_classroom_teacher on classroom (teacher_id);

-- 학생은 계정이 아니다. 표시명·번호만 두어 개인정보를 최소화한다(PLAN §6.1).
create table student (
    id           uuid primary key default gen_random_uuid(),
    classroom_id uuid         not null references classroom (id) on delete cascade,
    display_name varchar(50)  not null,
    student_no   int,
    created_at   timestamptz  not null default now()
);

create index idx_student_classroom on student (classroom_id);

-- 교실 LAN 제출은 익명 기기 ID 로 들어온다. 교사가 명단과 연결한다.
create table student_device (
    device_binding_id varchar(100) primary key,
    student_id        uuid        not null references student (id) on delete cascade,
    bound_at          timestamptz not null default now()
);

create index idx_student_device_student on student_device (student_id);

-- ── 콘텐츠 ──────────────────────────────────────────────────
create table dictation_item (
    id                uuid primary key default gen_random_uuid(),
    owner_teacher_id  uuid         not null references teacher (id) on delete cascade,
    expected_text     varchar(64)  not null,
    glyphs_json       jsonb        not null,
    content_hash      varchar(64)  not null,
    grade             int,
    unit              varchar(100),
    created_at        timestamptz  not null default now(),
    unique (owner_teacher_id, content_hash)
);

create index idx_item_owner on dictation_item (owner_teacher_id);

-- 학습지 템플릿 — 가리기 설정까지 저장한다(jammin 은 저장이 불가능했다).
create table worksheet (
    id         uuid primary key default gen_random_uuid(),
    teacher_id uuid         not null references teacher (id) on delete cascade,
    title      varchar(150) not null,
    profile    varchar(20)  not null default 'editor',
    hide_rules jsonb        not null default '{"mode":"ja","codes":[]}'::jsonb,
    item_ids   uuid[]       not null default '{}',
    created_at timestamptz  not null default now()
);

create index idx_worksheet_teacher on worksheet (teacher_id);

-- ── 시험·제출 ───────────────────────────────────────────────
create table assignment (
    id           uuid primary key default gen_random_uuid(),
    classroom_id uuid         not null references classroom (id) on delete cascade,
    title        varchar(150) not null,
    mode         varchar(16)  not null default 'ONLINE', -- ONLINE | CLASSROOM
    opened_at    timestamptz,
    closed_at    timestamptz,
    created_at   timestamptz  not null default now()
);

create index idx_assignment_classroom on assignment (classroom_id);

create table assignment_item (
    assignment_id uuid not null references assignment (id) on delete cascade,
    item_id       uuid not null references dictation_item (id) on delete cascade,
    order_no      int  not null,
    primary key (assignment_id, item_id)
);

create table exam_session (
    id            uuid primary key,          -- 교사앱의 허브 세션 ID 를 그대로 받는다
    assignment_id uuid references assignment (id) on delete set null,
    classroom_id  uuid        not null references classroom (id) on delete cascade,
    source        varchar(16) not null,      -- LAN | WEB
    started_at    timestamptz,
    ended_at      timestamptz,
    synced_at     timestamptz,
    created_at    timestamptz not null default now()
);

create index idx_session_classroom on exam_session (classroom_id);

create table attempt (
    id                uuid primary key,      -- attemptId = 멱등 키
    session_id        uuid         not null references exam_session (id) on delete cascade,
    student_id        uuid references student (id) on delete set null,
    device_binding_id varchar(100),
    item_id           uuid         not null references dictation_item (id) on delete cascade,
    raw_answer        varchar(64)  not null,
    correct_count     int          not null,
    total_count       int          not null,
    input_kind        varchar(20)  not null default 'keyboard',
    glyph_matches     jsonb,
    submitted_at      timestamptz  not null,
    created_at        timestamptz  not null default now(),
    -- 같은 세션에서 같은 기기가 같은 문항을 다시 내면 최신 것으로 대체한다
    unique (session_id, device_binding_id, item_id)
);

create index idx_attempt_session on attempt (session_id);
create index idx_attempt_student on attempt (student_id);
create index idx_attempt_item on attempt (item_id);
