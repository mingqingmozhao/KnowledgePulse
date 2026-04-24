package com.ahy.knowledgepulse.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (!tableExists("note")) {
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_favorite (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    note_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_note_favorite UNIQUE (note_id, user_id),
                    CONSTRAINT fk_note_favorite_note FOREIGN KEY (note_id) REFERENCES note(id),
                    CONSTRAINT fk_note_favorite_user FOREIGN KEY (user_id) REFERENCES user(id)
                )
                """);

        if (!columnExists("note", "daily_note_date")) {
            jdbcTemplate.execute("ALTER TABLE note ADD COLUMN daily_note_date DATE DEFAULT NULL AFTER share_password");
        }

        if (!indexExists("note", "idx_note_daily")) {
            jdbcTemplate.execute("CREATE INDEX idx_note_daily ON note (daily_note_date)");
        }

        if (!indexExists("note", "uk_note_daily_user_date")) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_note_daily_user_date ON note (user_id, daily_note_date)");
        }

        ensureNotificationSchema();
        ensureNoteCommentSchema();
        ensureNoteAttachmentSchema();
        ensureNoteTemplateSchema();
        seedSystemTemplates();
    }

    private void ensureNotificationSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_notification (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    recipient_user_id BIGINT NOT NULL,
                    actor_user_id BIGINT DEFAULT NULL,
                    type VARCHAR(40) NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    content VARCHAR(500) DEFAULT NULL,
                    note_id BIGINT DEFAULT NULL,
                    target_url VARCHAR(255) DEFAULT NULL,
                    read_flag TINYINT(1) DEFAULT 0,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    read_time DATETIME DEFAULT NULL,
                    INDEX idx_note_notification_recipient (recipient_user_id, read_flag, create_time),
                    INDEX idx_note_notification_note (note_id),
                    CONSTRAINT fk_note_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES user(id),
                    CONSTRAINT fk_note_notification_actor FOREIGN KEY (actor_user_id) REFERENCES user(id),
                    CONSTRAINT fk_note_notification_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
                )
                """);
    }

    private void ensureNoteCommentSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_comment (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    note_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    content VARCHAR(1000) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_note_comment_note (note_id, create_time),
                    CONSTRAINT fk_note_comment_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
                    CONSTRAINT fk_note_comment_user FOREIGN KEY (user_id) REFERENCES user(id)
                )
                """);
    }

    private void ensureNoteAttachmentSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_attachment (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    original_name VARCHAR(255) NOT NULL,
                    stored_name VARCHAR(255) NOT NULL,
                    storage_path VARCHAR(500) NOT NULL,
                    file_url VARCHAR(500) NOT NULL,
                    content_type VARCHAR(120) NOT NULL,
                    file_type VARCHAR(20) NOT NULL,
                    file_size BIGINT NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_note_attachment_user (user_id, create_time),
                    INDEX idx_note_attachment_type (file_type),
                    CONSTRAINT fk_note_attachment_user FOREIGN KEY (user_id) REFERENCES user(id)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_attachment_reference (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    attachment_id BIGINT NOT NULL,
                    note_id BIGINT NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_note_attachment_reference UNIQUE (attachment_id, note_id),
                    INDEX idx_note_attachment_reference_note (note_id),
                    CONSTRAINT fk_note_attachment_reference_attachment FOREIGN KEY (attachment_id) REFERENCES note_attachment(id) ON DELETE CASCADE,
                    CONSTRAINT fk_note_attachment_reference_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
                )
                """);
    }

    private void ensureNoteTemplateSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_template (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT DEFAULT NULL,
                    name VARCHAR(100) NOT NULL,
                    description VARCHAR(500) DEFAULT NULL,
                    content LONGTEXT,
                    html_content LONGTEXT,
                    tags VARCHAR(500) DEFAULT NULL,
                    category VARCHAR(50) DEFAULT 'General',
                    is_system TINYINT(1) DEFAULT 0,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_note_template_user (user_id),
                    INDEX idx_note_template_system (is_system, category),
                    CONSTRAINT fk_note_template_user FOREIGN KEY (user_id) REFERENCES user(id)
                )
                """);
    }

    private void seedSystemTemplates() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_template WHERE is_system = 1",
                Integer.class
        );

        if (count != null && count > 0) {
            return;
        }

        insertSystemTemplate(
                "\u9879\u76ee\u590d\u76d8",
                "\u6574\u7406\u9879\u76ee\u80cc\u666f\u3001\u7ed3\u679c\u3001\u95ee\u9898\u548c\u4e0b\u4e00\u6b65\u884c\u52a8\u3002",
                "\u590d\u76d8",
                "# \u9879\u76ee\u590d\u76d8\n\n## \u80cc\u666f\u4e0e\u76ee\u6807\n- \n\n## \u5df2\u5b8c\u6210\n- \n\n## \u95ee\u9898\u4e0e\u539f\u56e0\n- \n\n## \u4e0b\u4e00\u6b65\u884c\u52a8\n- \n",
                "\u590d\u76d8,\u9879\u76ee,\u884c\u52a8\u9879"
        );
        insertSystemTemplate(
                "\u4f1a\u8bae\u7eaa\u8981",
                "\u8bb0\u5f55\u4f1a\u8bae\u8bae\u9898\u3001\u8ba8\u8bba\u7ed3\u8bba\u3001\u8d1f\u8d23\u4eba\u548c\u622a\u6b62\u65f6\u95f4\u3002",
                "\u534f\u4f5c",
                "# \u4f1a\u8bae\u7eaa\u8981\n\n## \u57fa\u672c\u4fe1\u606f\n- \u65f6\u95f4\uff1a\n- \u53c2\u4e0e\u4eba\uff1a\n- \u4e3b\u9898\uff1a\n\n## \u8ba8\u8bba\u8981\u70b9\n- \n\n## \u51b3\u8bae\n- \n\n## \u5f85\u529e\n- [ ] \n",
                "\u4f1a\u8bae,\u534f\u4f5c,\u5f85\u529e"
        );
        insertSystemTemplate(
                "\u8bfb\u4e66\u7b14\u8bb0",
                "\u6c89\u6dc0\u4e66\u7c4d\u3001\u6587\u7ae0\u6216\u8bfe\u7a0b\u91cc\u7684\u5173\u952e\u89c2\u70b9\u3002",
                "\u5b66\u4e60",
                "# \u8bfb\u4e66\u7b14\u8bb0\n\n## \u6765\u6e90\n- \u4e66\u540d / \u6587\u7ae0\uff1a\n- \u4f5c\u8005\uff1a\n\n## \u6838\u5fc3\u89c2\u70b9\n- \n\n## \u89e6\u52a8\u6211\u7684\u6bb5\u843d\n> \n\n## \u53ef\u8fc1\u79fb\u7684\u65b9\u6cd5\n- \n\n## \u540e\u7eed\u884c\u52a8\n- \n",
                "\u9605\u8bfb,\u5b66\u4e60,\u65b9\u6cd5"
        );
        insertSystemTemplate(
                "\u95ee\u9898\u6392\u67e5",
                "\u8bb0\u5f55 bug\u3001\u7ebf\u4e0a\u95ee\u9898\u6216\u6280\u672f\u6545\u969c\u7684\u6392\u67e5\u8fc7\u7a0b\u3002",
                "\u5de5\u7a0b",
                "# \u95ee\u9898\u6392\u67e5\n\n## \u73b0\u8c61\n- \n\n## \u5f71\u54cd\u8303\u56f4\n- \n\n## \u5df2\u9a8c\u8bc1\u7ebf\u7d22\n- \n\n## \u6839\u56e0\n- \n\n## \u4fee\u590d\u65b9\u6848\n- \n\n## \u9632\u56de\u5f52\u68c0\u67e5\n- [ ] \n",
                "\u6392\u969c,\u5de5\u7a0b,\u590d\u76d8"
        );
    }

    private void insertSystemTemplate(
            String name,
            String description,
            String category,
            String content,
            String tags
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO note_template
                    (user_id, name, description, content, html_content, tags, category, is_system)
                VALUES
                    (NULL, ?, ?, ?, '', ?, ?, 1)
                """,
                name,
                description,
                content,
                tags,
                category
        );
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                Integer.class,
                tableName,
                indexName
        );
        return count != null && count > 0;
    }
}
