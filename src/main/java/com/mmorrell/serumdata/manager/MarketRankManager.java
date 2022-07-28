package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import com.mmorrell.serumdata.model.MarketListing;
import com.mmorrell.serumdata.model.Token;
import com.mmorrell.serumdata.util.MarketUtil;
import lombok.extern.slf4j.Slf4j;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MarketRankManager {

    private static final int RANK_PLACEHOLDER = 9999999;

    // Top tokens list, for quicker resolution from symbol.
    private static final Map<String, Token> TOP_TOKENS = Map.of(
            "SOL", Token.builder()
                    .publicKey(SerumUtils.WRAPPED_SOL_MINT)
                    .address(SerumUtils.WRAPPED_SOL_MINT.toBase58())
                    .build(),
            "USDC", Token.builder()
                    .publicKey(MarketUtil.USDC_MINT)
                    .address(MarketUtil.USDC_MINT.toBase58())
                    .build(),
            "USDT", Token.builder()
                    .publicKey(MarketUtil.USDT_MINT)
                    .address(MarketUtil.USDT_MINT.toBase58())
                    .build()
    );

    private final MarketManager marketManager;
    private final TokenManager tokenManager;
    private List<MarketListing> marketListings;

    public MarketRankManager(MarketManager marketManager, TokenManager tokenManager) {
        this.marketManager = marketManager;
        this.tokenManager = tokenManager;

        updateCachedMarketListings();

        log.info("Caching token images.");
        tokenManager.cacheAllTokenImages(
                marketListings.stream()
                        .map(MarketListing::getBaseMint)
                        .toList()
        );
        log.info("Successfully cached token images: " + marketListings.size());
    }

    @Scheduled(initialDelay = 5L, fixedRate = 5L, timeUnit = TimeUnit.MINUTES)
    public void updateMarketsScheduled() {
        marketManager.updateMarkets();
        updateCachedMarketListings();
    }

    /**
     * Returns rank of tokenMint, the highest rank is 1, based on # of Serum markets`
     * NOTE: Don't delete, used at the Thymeleaf layer
     *
     * @param tokenMint mint to rank based on # of serum markets
     * @return serum market rank for the given token
     */
    public int getMarketRankOfToken(PublicKey tokenMint) {
        for (int i = 0; i < marketListings.size(); i++) {
            if (marketListings.get(i).getBaseMint().equals(tokenMint)) {
                return i;
            }
        }

        return RANK_PLACEHOLDER;
    }

    // Used in Thymeleaf. Needs better solution.
    public String getImage(String tokenMint) {
        return "/api/serum/token/" + tokenMint + "/icon";
    }

    public Optional<Market> getMostActiveMarket(PublicKey baseMint) {
        List<Market> markets = marketManager.getMarketsByTokenMint(baseMint);
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
            if (!firstMarket.getQuoteMint().equals(MarketUtil.USDC_MINT) &&
                    secondMarket.getQuoteMint().equals(MarketUtil.USDC_MINT)) {
                markets.set(0, secondMarket);
                markets.set(1, firstMarket);
            }
        }

        return Optional.ofNullable(markets.get(0));
    }

    public Optional<Market> getMostActiveMarket(PublicKey baseMint, PublicKey quoteMint) {
        List<Market> markets = marketManager.getMarketsByTokenMint(baseMint);
        if (markets.size() < 1) {
            return Optional.empty();
        }

        // sort by base deposits
        markets.sort(Comparator.comparingLong(Market::getBaseDepositsTotal).reversed());
        for (Market market : markets) {
            if (market.getQuoteMint().equals(quoteMint)) {
                return Optional.of(market);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns lightweight token (address only) if given symbol has an active serum market.
     *
     * @param symbol e.g. SOL or USDC or RAY
     * @return most active token for given symbol
     */
    public Optional<Token> getMostSerumActiveTokenBySymbol(String symbol) {
        if (TOP_TOKENS.containsKey(symbol)) {
            return Optional.of(TOP_TOKENS.get(symbol));
        }

        List<Token> possibleBaseTokens = tokenManager.getTokensBySymbol(symbol);
        List<Market> activeMarkets = new ArrayList<>();

        for (Token baseToken : possibleBaseTokens) {
            // compile list of markets, return one with most fees accrued.
            Optional<Market> optionalMarket = getMostActiveMarket(baseToken.getPublicKey());
            optionalMarket.ifPresent(activeMarkets::add);
        }
        activeMarkets.sort(Comparator.comparingLong(Market::getQuoteFeesAccrued).reversed());

        if (activeMarkets.size() > 0) {
            return tokenManager.getTokenByMint(activeMarkets.get(0).getBaseMint());
        } else {
            return Optional.empty();
        }
    }

    public List<MarketListing> getMarketListings() {
        return marketListings;
    }

    private void updateCachedMarketListings() {
        marketListings = marketManager.getMarketCache().stream()
                .map(market -> {
                    // base and quote decimals
                    Optional<Token> baseToken = tokenManager.getTokenByMint(market.getBaseMint());
                    Optional<Token> quoteToken = tokenManager.getTokenByMint(market.getQuoteMint());

                    int baseDecimals = 0, quoteDecimals = 0;
                    if (baseToken.isPresent()) {
                        baseDecimals = baseToken.get().getDecimals();
                    }

                    if (quoteToken.isPresent()) {
                        quoteDecimals = quoteToken.get().getDecimals();
                    }

                    PublicKey baseMint = baseToken.isPresent() ?
                            baseToken.get().getPublicKey() :
                            MarketUtil.USDC_MINT;

                    return new MarketListing(
                            tokenManager.getMarketNameByMarket(market),
                            market.getOwnAddress(),
                            market.getQuoteDepositsTotal(),
                            marketManager.getQuoteNotional(market, quoteDecimals),
                            baseDecimals,
                            quoteDecimals,
                            baseMint,
                            tokenManager.getTokenLogoByMint(market.getBaseMint()),
                            tokenManager.getTokenLogoByMint(market.getQuoteMint())
                    );
                })
                .sorted((o1, o2) -> (int) (o2.getQuoteNotional() - o1.getQuoteNotional()))
                .toList();
    }
}
