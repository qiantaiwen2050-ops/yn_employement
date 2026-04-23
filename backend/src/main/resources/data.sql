-- Seed data: dictionaries (idempotent via INSERT OR IGNORE)
-- Seed users are created via DataSeeder.java so passwords are BCrypt-hashed at runtime.

-- Region (Yunnan major cities, partial sample)
INSERT OR IGNORE INTO sys_dict_item (dict_type, item_code, item_name, sort_order) VALUES
  ('REGION', '530100', '昆明市', 1),
  ('REGION', '530300', '曲靖市', 2),
  ('REGION', '530400', '玉溪市', 3),
  ('REGION', '530500', '保山市', 4),
  ('REGION', '530600', '昭通市', 5);

-- Enterprise nature (two-level — first level only here)
INSERT OR IGNORE INTO sys_dict_item (dict_type, item_code, item_name, sort_order) VALUES
  ('ENT_NATURE', 'STATE', '国有企业', 1),
  ('ENT_NATURE', 'PRIVATE', '民营企业', 2),
  ('ENT_NATURE', 'FOREIGN', '外商投资企业', 3),
  ('ENT_NATURE', 'COLLECTIVE', '集体企业', 4),
  ('ENT_NATURE', 'OTHER', '其他', 99);

-- Industry (top-level categories — sample)
INSERT OR IGNORE INTO sys_dict_item (dict_type, item_code, item_name, sort_order) VALUES
  ('INDUSTRY', 'A', '农、林、牧、渔业', 1),
  ('INDUSTRY', 'B', '采矿业', 2),
  ('INDUSTRY', 'C', '制造业', 3),
  ('INDUSTRY', 'F', '批发和零售业', 4),
  ('INDUSTRY', 'I', '信息传输、软件和信息技术服务业', 5),
  ('INDUSTRY', 'P', '教育', 6);

-- Decrease type (employment headcount decrease classification)
INSERT OR IGNORE INTO sys_dict_item (dict_type, item_code, item_name, sort_order) VALUES
  ('DECREASE_TYPE', '01', '关闭破产', 1),
  ('DECREASE_TYPE', '02', '停业整顿', 2),
  ('DECREASE_TYPE', '03', '经济性裁员', 3),
  ('DECREASE_TYPE', '04', '业务转移', 4),
  ('DECREASE_TYPE', '05', '自然减员', 5),
  ('DECREASE_TYPE', '06', '正常解除或终止劳动合同', 6),
  ('DECREASE_TYPE', '07', '国际因素变化影响', 7),
  ('DECREASE_TYPE', '08', '自然灾害', 8),
  ('DECREASE_TYPE', '09', '重大事件影响', 9),
  ('DECREASE_TYPE', '99', '其他', 99);

-- Decrease reason
-- NOTE: SRS §4.5.3.5 lists "自然减员" in both DECREASE_TYPE and DECREASE_REASON.
-- This is redundant — "自然减员" is the *type* of headcount loss (a category),
-- while 原因 captures the *economic/operational driver*. We drop R07 here and
-- also delete any stale row a previous init may have left (see cleanup below).
INSERT OR IGNORE INTO sys_dict_item (dict_type, item_code, item_name, sort_order) VALUES
  ('DECREASE_REASON', 'R01', '产业结构调整', 1),
  ('DECREASE_REASON', 'R02', '重大技术改革', 2),
  ('DECREASE_REASON', 'R03', '节能减排/淘汰落后产能', 3),
  ('DECREASE_REASON', 'R04', '订单不足', 4),
  ('DECREASE_REASON', 'R05', '原材料涨价', 5),
  ('DECREASE_REASON', 'R06', '工资及社保等用工成本上升', 6),
  ('DECREASE_REASON', 'R08', '经营资金困难', 8),
  ('DECREASE_REASON', 'R09', '税收政策变化', 9),
  ('DECREASE_REASON', 'R10', '季节性用工', 10),
  ('DECREASE_REASON', 'R11', '自行离职', 11),
  ('DECREASE_REASON', 'R12', '工作调动/企业内部调剂', 12),
  ('DECREASE_REASON', 'R13', '劳动关系转移/劳务派遣', 13),
  ('DECREASE_REASON', 'R99', '其他', 99);

DELETE FROM sys_dict_item WHERE dict_type='DECREASE_REASON' AND item_code='R07';

-- Report status (SRS §4.6.1)
INSERT OR IGNORE INTO sys_dict_item (dict_type, item_code, item_name, sort_order) VALUES
  ('REPORT_STATUS', 'DRAFT', '草稿', 0),
  ('REPORT_STATUS', '01', '待市级审核', 1),
  ('REPORT_STATUS', '02', '市级已通过', 2),
  ('REPORT_STATUS', '03', '市级退回', 3),
  ('REPORT_STATUS', '04', '待省级审核', 4),
  ('REPORT_STATUS', '05', '省级已通过', 5),
  ('REPORT_STATUS', '06', '省级退回', 6),
  ('REPORT_STATUS', '07', '已汇总上报', 7);
