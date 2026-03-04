package com.mudosa.musinsa.config.datasource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.datasource")
public class DataSourceRoutingProperties {

    private final Routing routing = new Routing();
    private final Replica replica = new Replica();

    @Getter
    @Setter
    public static class Routing {
        /**
         * Master/Replica 라우팅 전체 스위치.
         */
        private boolean enabled = false;

        /**
         * readOnly 트랜잭션을 replica로 보낼지 여부.
         */
        private boolean readOnlyToReplicaEnabled = true;

        /**
         * replica 헬스체크 주기(ms).
         */
        private long replicaHealthCheckIntervalMillis = 5000L;

        /**
         * Connection#isValid timeout(sec).
         */
        private int replicaValidationTimeoutSeconds = 2;
    }

    @Getter
    @Setter
    public static class Replica {
        /**
         * replica datasource 빈 생성 여부.
         */
        private boolean enabled = false;

        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private final Hikari hikari = new Hikari();
    }

    @Getter
    @Setter
    public static class Hikari {
        private int maximumPoolSize = 50;
        private int minimumIdle = 10;
        private long connectionTimeout = 3000L;
        private long maxLifetime = 270000L;
        private long keepaliveTime = 240000L;
    }
}
