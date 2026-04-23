-- ===== M1 schema (will be extended in M2-M7) =====
-- SQLite

CREATE TABLE IF NOT EXISTS sys_user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    password      TEXT    NOT NULL,                    -- BCrypt hash
    real_name     TEXT,
    user_type     TEXT    NOT NULL,                    -- province | city | enterprise
    region_code   TEXT,                                -- city/enterprise: city or county code
    region_name   TEXT,
    status        INTEGER NOT NULL DEFAULT 1,          -- 1=active, 0=disabled
    created_at    TEXT    DEFAULT (datetime('now','localtime')),
    updated_at    TEXT    DEFAULT (datetime('now','localtime')),
    deleted       INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sys_user_type ON sys_user(user_type);
CREATE INDEX IF NOT EXISTS idx_sys_user_region ON sys_user(region_code);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    dict_type   TEXT    NOT NULL,
    item_code   TEXT    NOT NULL,
    item_name   TEXT    NOT NULL,
    parent_code TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    UNIQUE(dict_type, item_code)
);
CREATE INDEX IF NOT EXISTS idx_sys_dict_type ON sys_dict_item(dict_type);

CREATE TABLE IF NOT EXISTS sys_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER,
    username    TEXT,
    action      TEXT NOT NULL,
    target      TEXT,
    detail      TEXT,
    ip          TEXT,
    created_at  TEXT DEFAULT (datetime('now','localtime'))
);

-- ===== M2 schema =====

-- Survey periods (调查期). 1-3 月生成 6 个半月报 + 4-12 月生成 9 个月报 = 15 期/年
CREATE TABLE IF NOT EXISTS sys_period (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,                            -- 显示名 e.g. "2026年1月上半月"
    year         INTEGER NOT NULL,
    period_type  TEXT    NOT NULL,                            -- HALF_MONTH | MONTH
    seq_in_year  INTEGER NOT NULL,                            -- 1..15
    start_date   TEXT    NOT NULL,                            -- YYYY-MM-DD (所覆盖的数据起始日)
    end_date     TEXT    NOT NULL,                            -- YYYY-MM-DD (所覆盖的数据截止日)
    status       TEXT    NOT NULL DEFAULT 'OPEN',             -- OPEN | CLOSED
    created_at   TEXT    DEFAULT (datetime('now','localtime')),
    UNIQUE(year, seq_in_year)
);
CREATE INDEX IF NOT EXISTS idx_sys_period_year   ON sys_period(year);
CREATE INDEX IF NOT EXISTS idx_sys_period_status ON sys_period(status);

-- Enterprise filing/registration (备案信息). One row per enterprise user.
CREATE TABLE IF NOT EXISTS enterprise_info (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL UNIQUE,
    region_code     TEXT,
    region_name     TEXT,
    org_code        TEXT,                                     -- 组织机构代码
    name            TEXT,                                     -- 企业名称
    nature          TEXT,                                     -- 企业性质 (dict ENT_NATURE)
    industry        TEXT,                                     -- 所属行业 (dict INDUSTRY)
    main_business   TEXT,                                     -- 主要经营业务
    contact         TEXT,                                     -- 联系人
    address         TEXT,                                     -- 联系地址
    postcode        TEXT,                                     -- 邮政编码 (6 digits)
    phone           TEXT,
    fax             TEXT,
    email           TEXT,
    filing_status   TEXT    NOT NULL DEFAULT 'DRAFT',         -- DRAFT | PENDING | APPROVED | REJECTED
    reject_reason   TEXT,
    submitted_at    TEXT,
    reviewed_at     TEXT,
    reviewed_by     TEXT,
    created_at      TEXT    DEFAULT (datetime('now','localtime')),
    updated_at      TEXT    DEFAULT (datetime('now','localtime'))
);
CREATE INDEX IF NOT EXISTS idx_ent_info_status ON enterprise_info(filing_status);
CREATE INDEX IF NOT EXISTS idx_ent_info_region ON enterprise_info(region_code);

-- Monthly / half-monthly reports (上报数据). Unique per (enterprise, period).
CREATE TABLE IF NOT EXISTS report (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    enterprise_id         INTEGER NOT NULL,
    period_id             INTEGER NOT NULL,
    base_count            INTEGER,                            -- 建档期就业人数
    current_count         INTEGER,                            -- 调查期就业人数
    decrease_type         TEXT,                               -- 就业人数减少类型 (dict DECREASE_TYPE)
    primary_reason        TEXT,                               -- 主要原因 (dict DECREASE_REASON)
    primary_reason_text   TEXT,
    secondary_reason      TEXT,
    secondary_reason_text TEXT,
    third_reason          TEXT,
    third_reason_text     TEXT,
    other_reason          TEXT,                               -- 其他原因 (free text)
    status                TEXT    NOT NULL DEFAULT 'DRAFT',   -- DRAFT | 01..07 (per dict REPORT_STATUS)
    city_review_at        TEXT,
    city_reviewer         TEXT,
    city_return_reason    TEXT,
    prov_review_at        TEXT,
    prov_reviewer         TEXT,
    prov_return_reason    TEXT,
    submitted_at          TEXT,
    created_at            TEXT    DEFAULT (datetime('now','localtime')),
    updated_at            TEXT    DEFAULT (datetime('now','localtime')),
    deleted               INTEGER NOT NULL DEFAULT 0,
    UNIQUE(enterprise_id, period_id)
);
CREATE INDEX IF NOT EXISTS idx_report_ent    ON report(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_report_period ON report(period_id);
CREATE INDEX IF NOT EXISTS idx_report_status ON report(status);

-- Each enterprise submission becomes one attempt row (frozen snapshot + outcome).
-- The `report` row above is the *current* state (latest editable / latest active);
-- this table is the immutable audit trail used by 历史查询.
CREATE TABLE IF NOT EXISTS report_attempt (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    report_id             INTEGER NOT NULL,
    enterprise_id         INTEGER NOT NULL,
    period_id             INTEGER NOT NULL,
    attempt_seq           INTEGER NOT NULL,                  -- 1, 2, 3 ... per (report_id)
    base_count            INTEGER,
    current_count         INTEGER,
    decrease_type         TEXT,
    primary_reason        TEXT,
    primary_reason_text   TEXT,
    secondary_reason      TEXT,
    secondary_reason_text TEXT,
    third_reason          TEXT,
    third_reason_text     TEXT,
    other_reason          TEXT,
    submitted_at          TEXT    NOT NULL,                  -- when the enterprise submitted this attempt
    status                TEXT    NOT NULL,                  -- live status of this attempt: 01..07
    city_review_at        TEXT,
    city_reviewer         TEXT,
    city_return_reason    TEXT,
    prov_review_at        TEXT,
    prov_reviewer         TEXT,
    prov_return_reason    TEXT,
    closed_at             TEXT,                              -- set when terminal (returned to ent / finalized to nation)
    UNIQUE(report_id, attempt_seq)
);
CREATE INDEX IF NOT EXISTS idx_report_attempt_report ON report_attempt(report_id);
CREATE INDEX IF NOT EXISTS idx_report_attempt_ent    ON report_attempt(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_report_attempt_period ON report_attempt(period_id);

-- ===== M4 schema =====

-- Province corrections: original `report` row stays untouched; each correction
-- becomes a revision row. Effective values for downstream reads/aggregation =
-- latest revision (or original if none).
CREATE TABLE IF NOT EXISTS report_revision (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    report_id             INTEGER NOT NULL,
    revision_seq          INTEGER NOT NULL,
    base_count            INTEGER,
    current_count         INTEGER,
    decrease_type         TEXT,
    primary_reason        TEXT,
    primary_reason_text   TEXT,
    secondary_reason      TEXT,
    secondary_reason_text TEXT,
    third_reason          TEXT,
    third_reason_text     TEXT,
    other_reason          TEXT,
    before_json           TEXT,                              -- snapshot of effective values BEFORE this revision
    revised_by_id         INTEGER,
    revised_by_name       TEXT,
    reason                TEXT NOT NULL,                     -- 修改原因
    created_at            TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE(report_id, revision_seq)
);
CREATE INDEX IF NOT EXISTS idx_report_revision_report ON report_revision(report_id);

-- ===== M6 schema =====

-- RBAC tables (presentational — primary auth still uses sys_user.user_type;
-- roles are managed for SRS compliance and future per-permission checks).
CREATE TABLE IF NOT EXISTS sys_role (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT    NOT NULL UNIQUE,
    name        TEXT    NOT NULL,
    description TEXT,
    is_builtin  INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id INTEGER NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id   INTEGER NOT NULL,
    perm_code TEXT    NOT NULL,
    PRIMARY KEY (role_id, perm_code)
);

-- National monitoring system upload log (one row per (period × upload attempt)).
CREATE TABLE IF NOT EXISTS nation_report_log (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    period_id          INTEGER NOT NULL,
    period_name        TEXT,
    report_type        TEXT NOT NULL,                       -- MANUAL | AUTO
    status             TEXT NOT NULL,                       -- SENDING | SUCCESS | FAILED
    data_count         INTEGER NOT NULL DEFAULT 0,          -- # of reports submitted in this attempt
    request_summary    TEXT,                                -- brief request payload summary
    response_summary   TEXT,                                -- brief response payload summary
    receipt_no         TEXT,                                -- 国家系统回执编号（成功时）
    error_code         TEXT,
    error_message      TEXT,
    retry_count        INTEGER NOT NULL DEFAULT 0,          -- 0 for first attempt; +1 per retry
    parent_log_id      INTEGER,                             -- links retries to original
    operator_id        INTEGER,
    operator_name      TEXT,
    started_at         TEXT,
    finished_at        TEXT
);
CREATE INDEX IF NOT EXISTS idx_nation_log_period ON nation_report_log(period_id);
CREATE INDEX IF NOT EXISTS idx_nation_log_status ON nation_report_log(status);
CREATE INDEX IF NOT EXISTS idx_nation_log_started ON nation_report_log(started_at);

-- ===== M3 schema =====

-- Notices published by city or province; visible to enterprises (and city users for province notices).
CREATE TABLE IF NOT EXISTS notice (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    title                  TEXT    NOT NULL,
    content                TEXT    NOT NULL,
    publisher_id           INTEGER NOT NULL,
    publisher_username     TEXT,
    publisher_real_name    TEXT,
    publisher_type         TEXT    NOT NULL,    -- city | province
    publisher_region_code  TEXT,                -- city: own region; province: NULL
    publisher_region_name  TEXT,
    valid_until            TEXT,                -- YYYY-MM-DD or NULL
    created_at             TEXT    DEFAULT (datetime('now','localtime')),
    updated_at             TEXT    DEFAULT (datetime('now','localtime')),
    deleted                INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_notice_publisher ON notice(publisher_id);
CREATE INDEX IF NOT EXISTS idx_notice_type      ON notice(publisher_type);
CREATE INDEX IF NOT EXISTS idx_notice_region    ON notice(publisher_region_code);
