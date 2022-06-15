package com.mmorrell.serumdata.manager;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MarketRankManager {

    private static final int RANK_PLACEHOLDER = 9999999;
    private final MarketManager marketManager;

    public MarketRankManager(MarketManager marketManager) {
        this.marketManager = marketManager;
    }

    /**
     * Returns rank of tokenMint, the highest rank is 1, based on # of Serum markets
     * @param tokenMint mint to rank based on # of serum markets
     * @return serum market rank for the given token
     */
    public int getMarketRankOfToken(String tokenMint) {
        Map<String, Integer> marketCounts = new HashMap<>();

        marketManager.getMarketMapCache().forEach((token, markets) -> {
            marketCounts.put(token, markets.size());
        });

        List<Map.Entry<String, Integer>> list = new ArrayList<>(marketCounts.entrySet());
        list.sort(Map.Entry.comparingByValue((o1, o2) -> o2 - o1));

        Map<String, Integer> marketRanks = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            marketRanks.put(list.get(i).getKey(), i + 1);
        }

        return marketRanks.getOrDefault(tokenMint, RANK_PLACEHOLDER);
    }
}
