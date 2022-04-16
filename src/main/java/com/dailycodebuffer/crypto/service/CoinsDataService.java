package com.dailycodebuffer.crypto.service;

import com.dailycodebuffer.crypto.model.*;
import com.dailycodebuffer.crypto.utils.HttpUtils;
import io.github.dengliming.redismodule.redisjson.RedisJSON;
import io.github.dengliming.redismodule.redisjson.args.GetArgs;
import io.github.dengliming.redismodule.redisjson.args.SetArgs;
import io.github.dengliming.redismodule.redisjson.utils.GsonUtils;
import io.github.dengliming.redismodule.redistimeseries.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CoinsDataService {

    public static final String GET_COINS_API = "https://coinranking1.p.rapidapi.com/coins?referenceCurrencyUuid=yhjMzLPhuIDl&timePeriod=24h&tiers=1&orderBy=marketCap&orderDirection=desc&limit=50&offset=0";
    public static final String GET_COIN_HISTORY_API = "https://coinranking1.p.rapidapi.com/coin/";
    public static final String COIN_HISTORY_TIME_PERIOD_PARAM = "/history?timePeriod=";
    public static final List<String> timePeriods = List.of("24h", "7d", "30d", "3m", "1y", "3y", "5y");
    public static final String REDIS_KEY_COINS = "coins";
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    RedisJSON redisJSON;

    @Autowired
    RedisTimeSeries redisTimeSeries;

    public void fetchCoins() {
        log.info("Inside fetchCoins()");
        ResponseEntity<Coins> coinsEntity =
                restTemplate.exchange(GET_COINS_API,
                        HttpMethod.GET,
                        HttpUtils.getHttpEntity(),
                        Coins.class);

        storeCoinsToRedisJSON(coinsEntity.getBody());
        log.info("All Coins Data Saved to Redis");
    }

    public void fetchCoinsHistory() {
        log.info("Inside fetchCoinsHistory()");
        List<CoinInfo> allCoins = getAllCoinsFromRedisJSON();

        allCoins.forEach(coinInfo -> {
            timePeriods.forEach(s -> {
                try {
                    fetchCoinHistoryForTimePeriod(coinInfo, s);
                    Thread.sleep(200); // To Avoid Rate Limit of rapid API of 5 Request/Sec
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        });

    }

    public List<CoinInfo> fetchAllCoinsFromRedisJSON() {
        return getAllCoinsFromRedisJSON();
    }

    public List<Sample.Value> fetchCoinHistoryPerTimePeriodFromRedisTS(String symbol, String timePeriod) {
        Map<String, Object> tsInfo = fetchTSInfoForSymbol(symbol, timePeriod);
        Long firstTimestamp = Long.valueOf(tsInfo.get("firstTimestamp").toString());
        Long lastTimestamp = Long.valueOf(tsInfo.get("lastTimestamp").toString());
        List<Sample.Value> coinsTSData =
                fetchTSDataForCoin(symbol, timePeriod, firstTimestamp, lastTimestamp);
        return coinsTSData;
    }

    private List<Sample.Value> fetchTSDataForCoin(String symbol, String timePeriod, Long firstTimestamp, Long lastTimestamp) {
        String key = symbol + ":" + timePeriod;
        List<Sample.Value> coinTSData = redisTimeSeries.range(key,firstTimestamp,lastTimestamp);
        return coinTSData;
    }

    private Map<String, Object> fetchTSInfoForSymbol(String symbol, String timePeriod) {
        return redisTimeSeries.info(symbol + ":" + timePeriod);
    }

    private void fetchCoinHistoryForTimePeriod(CoinInfo coinInfo, String timePeriod) {
        log.info("Fetching Coin History of {} for Time Period {}", coinInfo.getName(), timePeriod);
        String url = GET_COIN_HISTORY_API + coinInfo.getUuid() + COIN_HISTORY_TIME_PERIOD_PARAM + timePeriod;
        ResponseEntity<CoinPriceHistory> coinPriceHistoryResponseEntity =
                restTemplate.exchange(url,
                        HttpMethod.GET,
                        HttpUtils.getHttpEntity(),
                        CoinPriceHistory.class);

        log.info("Data Fetched From API for Coin History of {} for Time Period {}", coinInfo.getName(), timePeriod);

        storeCoinHistoryToRedisTS(coinPriceHistoryResponseEntity.getBody(), coinInfo.getSymbol(), timePeriod);
    }

    private void storeCoinHistoryToRedisTS(CoinPriceHistory coinPriceHistory, String symbol, String timePeriod) {
        log.info("Storing Coin History of {} for Time Period {} into Redis TS", symbol, timePeriod);
        List<CoinPriceHistoryExchangeRate> coinExchangeRateData =
                coinPriceHistory.getData().getHistory();
        coinExchangeRateData.stream()
                .filter(ch -> ch.getPrice() != null && ch.getTimestamp() != null)
                .forEach(ch -> {
                    redisTimeSeries.add(new Sample(symbol + ":" + timePeriod, Sample.Value.of(Long.valueOf(ch.getTimestamp()),
                            Double.valueOf(ch.getPrice()))), new TimeSeriesOptions()
                            .unCompressed()
                            .duplicatePolicy(DuplicatePolicy.LAST));
                           // .labels(new Label(symbol, timePeriod)));
                });
        log.info("Complete: Stored Coin History of {} for Time Period {} into Redis TS", symbol, timePeriod);
    }

    private List<CoinInfo> getAllCoinsFromRedisJSON() {
        CoinData coinData =
                redisJSON.get(REDIS_KEY_COINS,
                        CoinData.class,
                        new GetArgs().path(".data").indent("\t").newLine("\n").space(" "));
        log.info("allCoins: " + coinData);
        return coinData.getCoins();
    }

    private void storeCoinsToRedisJSON(Coins coins) {
        redisJSON.set(REDIS_KEY_COINS, SetArgs
                .Builder.create(".", GsonUtils.toJson(coins)));
    }

    public void demo() {
        var data = redisTimeSeries.info("BTC");
        log.info(data.toString());
    }
}
