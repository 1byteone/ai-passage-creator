alter table comic_episode add column year_month char(7) null comment '章节所属月册 (YYYY-MM)，兼容历史数据为 null';
