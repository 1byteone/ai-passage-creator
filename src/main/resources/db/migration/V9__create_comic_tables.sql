create table comic_book (
    id bigint auto_increment primary key,
    user_id bigint not null,
    book_name varchar(64) not null,
    default_style varchar(32) not null default 'powder',
    is_delete tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    constraint uq_comic_book_user_name unique (user_id, book_name)
) comment '漫画手帐档案';

create table comic_episode (
    id bigint auto_increment primary key,
    book_id bigint not null,
    episode_no int not null,
    title varchar(128) null,
    input_type varchar(16) not null default 'daily',
    input_summary varchar(512) null,
    style varchar(32) not null default 'powder',
    route_result longtext null,
    storyboard_result longtext null,
    image_prompts longtext null,
    layout_result longtext null,
    page_html longtext null,
    png_url varchar(512) null,
    is_delete tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    index idx_comic_episode_book (book_id)
) comment '漫画手帐章节';

create table comic_monthly_volume (
    id bigint auto_increment primary key,
    book_id bigint not null,
    year_month char(7) not null,
    episode_count int default 0 not null,
    index_html longtext null,
    cover_title varchar(128) null,
    is_delete tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    constraint uq_comic_monthly_book_ym unique (book_id, year_month)
) comment '漫画手帐月册索引';
