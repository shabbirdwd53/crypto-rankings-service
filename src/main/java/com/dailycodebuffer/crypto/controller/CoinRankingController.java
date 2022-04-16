package com.dailycodebuffer.crypto.controller;

import com.dailycodebuffer.crypto.model.CoinInfo;
import com.dailycodebuffer.crypto.model.HistoryData;
import com.dailycodebuffer.crypto.service.CoinsDataService;
import com.dailycodebuffer.crypto.utils.Utility;
import io.github.dengliming.redismodule.redistimeseries.Sample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(value = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/coins")
@Slf4j
public class CoinRankingController {

    @Autowired
    private CoinsDataService coinsDataService;

    @GetMapping
    public ResponseEntity<List<CoinInfo>> fetchAllCoins() {
        return  ResponseEntity.ok().body(coinsDataService.fetchAllCoinsFromRedisJSON());
    }

    @GetMapping("/{symbol}/{timePeriod}")
    public List<HistoryData> fetchCoinHistoryPerTimePeriod(@PathVariable String symbol,
                                                            @PathVariable String timePeriod) {
        log.info("Fetching History Data for {} for time period {}", symbol, timePeriod);
        List<Sample.Value> coinsTSData =
                coinsDataService.fetchCoinHistoryPerTimePeriodFromRedisTS(symbol, timePeriod);
        //Convert to Readable format
        List<HistoryData> coinHistory =
                coinsTSData
                        .stream()
                        .map(value -> new HistoryData(Utility.convertUnixTimeToDate(value.getTimestamp()),
                                Utility.round(value.getValue(),2)))
                        .collect(Collectors.toList());
        return coinHistory;

    }
}
