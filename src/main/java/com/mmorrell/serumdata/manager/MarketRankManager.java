package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import com.mmorrell.serumdata.model.Token;
import com.mmorrell.serumdata.util.MarketUtil;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MarketRankManager {

    private static final int RANK_PLACEHOLDER = 9999999;
    private final MarketManager marketManager;
    private final TokenManager tokenManager;

    public MarketRankManager(MarketManager marketManager, TokenManager tokenManager) {
        this.marketManager = marketManager;
        this.tokenManager = tokenManager;
    }

    /**
     * Returns rank of tokenMint, the highest rank is 1, based on # of Serum markets
     * @param tokenMint mint to rank based on # of serum markets
     * @return serum market rank for the given token
     */
    public int getMarketRankOfToken(String tokenMint) {
        Map<String, Integer> marketCounts = new HashMap<>();

        marketManager.getMarketMapCache().forEach((token, markets) -> marketCounts.put(token, markets.size()));

        List<Map.Entry<String, Integer>> list = new ArrayList<>(marketCounts.entrySet());
        list.sort(Map.Entry.comparingByValue((o1, o2) -> o2 - o1));

        Map<String, Integer> marketRanks = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            marketRanks.put(list.get(i).getKey(), i + 1);
        }

        return marketRanks.getOrDefault(tokenMint, RANK_PLACEHOLDER);
    }

    public Optional<Market> getMostActiveMarket(String baseMint) {
        List<Market> markets = marketManager.getMarketsByMint(baseMint);
        if (markets.size() < 1) {
            return Optional.empty();
        }

        // sort by base deposits
        markets.sort(Comparator.comparingLong(Market::getBaseDepositsTotal).reversed());

        // prefer USDC over other pairs if 2 top pairs are XYZ / USDC
        if (markets.size() > 1) {
            Market firstMarket = markets.get(0);
            Market secondMarket = markets.get(1);

            // if first pair isn't USDC quoted, and second pair is, move it to first place
            if (!firstMarket.getQuoteMint().toBase58().equalsIgnoreCase(MarketUtil.USDC_MINT.toBase58()) &&
                    secondMarket.getQuoteMint().toBase58().equalsIgnoreCase(MarketUtil.USDC_MINT.toBase58())) {
                markets.set(0, secondMarket);
            }
        }

        return Optional.ofNullable(markets.get(0));
    }

    public Optional<Market> getMostActiveMarket(String baseMint, String quoteMint) {
        List<Market> markets = marketManager.getMarketsByMint(baseMint);
        if (markets.size() < 1) {
            return Optional.empty();
        }

        // sort by base deposits
        markets.sort(Comparator.comparingLong(Market::getBaseDepositsTotal).reversed());
        for (Market market : markets) {
            if (market.getQuoteMint().toBase58().equalsIgnoreCase(quoteMint)) {
                return Optional.of(market);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns lightweight token (address only) if given symbol has an active serum market.
     * @param symbol e.g. SOL or USDC or RAY
     * @return most active token for given symbol
     */
    public Optional<Token> getMostSerumActiveTokenBySymbol(String symbol) {
        if (symbol.equalsIgnoreCase("SOL")) {
            return Optional.of(new Token(SerumUtils.WRAPPED_SOL_MINT.toBase58()));
        } else if (symbol.equalsIgnoreCase("USDC")) {
            return Optional.of(new Token(MarketUtil.USDC_MINT.toBase58()));
        }

        List<Token> possibleBaseTokens = tokenManager.getTokensBySymbol(symbol);
        List<Market> activeMarkets = new ArrayList<>();

        for(Token baseToken : possibleBaseTokens) {
            // compile list of markets, return one with most fees accrued.
            Optional<Market> optionalMarket = getMostActiveMarket(baseToken.getAddress());
            optionalMarket.ifPresent(activeMarkets::add);
        }
        activeMarkets.sort(Comparator.comparingLong(Market::getQuoteFeesAccrued).reversed());

        if (activeMarkets.size() > 0) {
            return Optional.ofNullable(tokenManager.getTokenByMint(activeMarkets.get(0).getBaseMint().toBase58()));
        } else {
            return Optional.empty();
        }
    }
}
