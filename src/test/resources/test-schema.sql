CREATE TABLE IF NOT EXISTS user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  avatar VARCHAR(255),
  nickname VARCHAR(50),
  role VARCHAR(20) DEFAULT 'USER',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS note_folder (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  parent_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_folder_user FOREIGN KEY (user_id) REFERENCES user(id),
  CONSTRAINT fk_folder_parent FOREIGN KEY (parent_id) REFERENCES note_folder(id)
);

CREATE TABLE IF NOT EXISTS note (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  folder_id BIGINT,
  title VARCHAR(200) NOT NULL,
  content CLOB,
  html_content CLOB,
  tags VARCHAR(500),
  is_public TINYINT DEFAULT 0,
  share_token VARCHAR(32),
  share_password VARCHAR(100),
  daily_note_date DATE,
  is_deleted TINYINT DEFAULT 0,
  deleted_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_user FOREIGN KEY (user_id) REFERENCES user(id),
  CONSTRAINT fk_note_folder FOREIGN KEY (folder_id) REFERENCES note_folder(id)
);

CREATE INDEX idx_note_deleted ON note(is_deleted, deleted_time);
CREATE INDEX idx_note_daily ON note(daily_note_date);
CREATE UNIQUE INDEX uk_note_daily_user_date ON note(user_id, daily_note_date);

CREATE TABLE IF NOT EXISTS note_tag (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  tag_name VARCHAR(50) NOT NULL,
  CONSTRAINT fk_note_tag_note FOREIGN KEY (note_id) REFERENCES note(id)
);

CREATE TABLE IF NOT EXISTS note_relation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_note_id BIGINT NOT NULL,
  target_note_id BIGINT NOT NULL,
  relation_type VARCHAR(20) NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_relation_source FOREIGN KEY (source_note_id) REFERENCES note(id),
  CONSTRAINT fk_relation_target FOREIGN KEY (target_note_id) REFERENCES note(id)
);

CREATE TABLE IF NOT EXISTS note_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  version INT NOT NULL DEFAULT 1,
  content_snapshot CLOB NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_version_note FOREIGN KEY (note_id) REFERENCES note(id)
);

CREATE TABLE IF NOT EXISTS note_collaborator (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  permission VARCHAR(20) NOT NULL DEFAULT 'READ',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_note_collaborator UNIQUE (note_id, user_id),
  CONSTRAINT fk_note_collab_note FOREIGN KEY (note_id) REFERENCES note(id),
  CONSTRAINT fk_note_collab_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE TABLE IF NOT EXISTS note_favorite (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_note_favorite UNIQUE (note_id, user_id),
  CONSTRAINT fk_note_favorite_note FOREIGN KEY (note_id) REFERENCES note(id),
  CONSTRAINT fk_note_favorite_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE TABLE IF NOT EXISTS note_attachment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  file_url VARCHAR(500) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  file_type VARCHAR(20) NOT NULL,
  file_size BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_attachment_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE INDEX idx_note_attachment_user ON note_attachment(user_id, create_time);
CREATE INDEX idx_note_attachment_type ON note_attachment(file_type);

CREATE TABLE IF NOT EXISTS note_attachment_reference (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  attachment_id BIGINT NOT NULL,
  note_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_note_attachment_reference UNIQUE (attachment_id, note_id),
  CONSTRAINT fk_note_attachment_reference_attachment FOREIGN KEY (attachment_id) REFERENCES note_attachment(id) ON DELETE CASCADE,
  CONSTRAINT fk_note_attachment_reference_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
);

CREATE INDEX idx_note_attachment_reference_note ON note_attachment_reference(note_id);

CREATE TABLE IF NOT EXISTS note_notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL,
  actor_user_id BIGINT,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(500),
  note_id BIGINT,
  target_url VARCHAR(255),
  read_flag TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  read_time TIMESTAMP,
  CONSTRAINT fk_note_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES user(id),
  CONSTRAINT fk_note_notification_actor FOREIGN KEY (actor_user_id) REFERENCES user(id),
  CONSTRAINT fk_note_notification_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
);

CREATE INDEX idx_note_notification_recipient ON note_notification(recipient_user_id, read_flag, create_time);
CREATE INDEX idx_note_notification_note ON note_notification(note_id);

CREATE TABLE IF NOT EXISTS note_comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_comment_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
  CONSTRAINT fk_note_comment_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE INDEX idx_note_comment_note ON note_comment(note_id, create_time);

CREATE TABLE IF NOT EXISTS note_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  content CLOB,
  html_content CLOB,
  tags VARCHAR(500),
  category VARCHAR(50) DEFAULT 'General',
  is_system TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_template_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  module VARCHAR(50) NOT NULL,
  operation VARCHAR(200) NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
