package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ContextoTest {

    @Autowired DataSource dataSource;
    @Autowired MongoTemplate mongo;

    @Test
    void arrancaConPostgresYMongo() {
        Integer uno = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
        assertThat(uno).isEqualTo(1);

        Document ping = mongo.getDb().runCommand(new Document("ping", 1));
        assertThat(ping.get("ok", Number.class).intValue()).isEqualTo(1);
    }
}
