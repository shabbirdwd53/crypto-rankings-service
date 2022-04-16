package com.dailycodebuffer.crypto.config;

import io.github.dengliming.redismodule.redisjson.RedisJSON;
import io.github.dengliming.redismodule.redisjson.client.RedisJSONClient;
import io.github.dengliming.redismodule.redistimeseries.RedisTimeSeries;
import io.github.dengliming.redismodule.redistimeseries.client.RedisTimeSeriesClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Value("${redis.url}")
    private String redisURL;

    @Bean
    public Config config() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redisURL);
        //.setPassword("rpZP7FuQHBRYShkVGfZnIzTNEmFU1FdG").setConnectionPoolSize(2).setConnectionMinimumIdleSize(1);
        return config;
    }

    @Bean
    public RedisTimeSeriesClient redisTimeSeriesClient(Config config) {
        return new RedisTimeSeriesClient(config);
    }

    @Bean
    public RedisTimeSeries redisTimeSeries(RedisTimeSeriesClient redisTimeSeriesClient) {
        return redisTimeSeriesClient.getRedisTimeSeries();
    }

    @Bean
    public RedisJSONClient redisJSONClient(Config config) {
        return new RedisJSONClient(config);
    }

    @Bean
    public RedisJSON redisJSON(RedisJSONClient redisJSONClient) {
        return redisJSONClient.getRedisJSON();
    }
}
