-- ComicRenderService.renderToPngDataUrl 返回 base64 PNG data URL（约 70-90K 字符），
-- varchar(512) 会抛 "Data too long" 导致整行写入失败，需加宽为 longtext。
alter table comic_episode modify png_url longtext null comment 'PNG 导出地址或 data URL';
