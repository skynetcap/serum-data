package com.mmorrell.serumdata.manager;

import com.mmorrell.serum.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mmorrell.serumdata.client.AccountInfoRow;
import com.mmorrell.serumdata.client.SerumDbClient;
import com.mmorrell.serumdata.util.MarketUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class MarketManager {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final SerumDbClient serumDbClient;
    // Managers
    private final TokenManager tokenManager;

    // <marketPubkey, Market>
    private final Map<PublicKey, Market> marketCache = new HashMap<>();
    // <baseMint, List<Market>>
    private final Map<PublicKey, List<Market>> marketMapCache = new HashMap<>();
    // <quoteMint, List<Market>>
    private final Map<PublicKey, List<Market>> marketMapQuoteMintCache = new HashMap<>();

    // Price cache for notional calculations
    // <marketId, bestBid>
    private static final int MINIMUM_REQUIRED_MARKETS_FOR_PRICING = 2;
    private final Map<PublicKey, Float> priceCache = new HashMap<>();

    // Caching
    private static final int GEYSER_CACHE_DURATION_MS = 200;

    private long currentSlot = 0;

    // Caching for individual bid and asks orderbooks.
    final LoadingCache<PublicKey, OrderBook> bidOrderBookLoadingCache = CacheBuilder.newBuilder()
            .expireAfterWrite(GEYSER_CACHE_DURATION_MS, TimeUnit.MILLISECONDS)
            .build(
                    new CacheLoader<>() {
                        @Override
                        public OrderBook load(PublicKey marketPubkey) {
                            Market cachedMarket = marketCache.get(marketPubkey);
                            Request request = SerumDbClient.buildGetAccountInfoSerumDbRequest(cachedMarket.getBids());

                            try (Response response = okHttpClient.newCall(request).execute()) {
                                ResponseBody responseBody = response.body();
                                byte[] data = responseBody.bytes();

                                AccountInfoRow accountInfoRow = objectMapper.readValue(
                                        data,
                                        AccountInfoRow.class
                                );

                                long slot = accountInfoRow.getSlot();
                                if (slot > currentSlot) {
                                    currentSlot = slot;
                                }

                                return buildOrderBook(
                                        Base64.getDecoder().decode(accountInfoRow.getData()),
                                        cachedMarket
                                );

                            } catch (Exception ex) {
                                // Case: HTTP exception
                                log.error(ex.getMessage());
                            }

                            // fall-back to old entry
                            return bidOrderBookLoadingCache.asMap().get(marketPubkey);
                        }
                    });

    // Caching for individual bid and asks orderbooks.
    final LoadingCache<PublicKey, OrderBook> askOrderBookLoadingCache = CacheBuilder.newBuilder()
            .expireAfterWrite(GEYSER_CACHE_DURATION_MS, TimeUnit.MILLISECONDS)
            .build(
                    new CacheLoader<>() {
                        @Override
                        public OrderBook load(PublicKey marketPubkey) {
                            Market cachedMarket = marketCache.get(marketPubkey);
                            Request request = SerumDbClient.buildGetAccountInfoSerumDbRequest(cachedMarket.getAsks());

                            try (Response response = okHttpClient.newCall(request).execute()) {
                                ResponseBody responseBody = response.body();
                                byte[] data = responseBody.bytes();

                                AccountInfoRow accountInfoRow = objectMapper.readValue(
                                        data,
                                        AccountInfoRow.class
                                );

                                long slot = accountInfoRow.getSlot();
                                if (slot > currentSlot) {
                                    currentSlot = slot;
                                }

                                return buildOrderBook(
                                        Base64.getDecoder().decode(accountInfoRow.getData()),
                                        cachedMarket
                                );

                            } catch (Exception ex) {
                                // Case: HTTP exception
                                log.error(ex.getMessage());
                            }

                            // fall-back to old entry
                            return askOrderBookLoadingCache.asMap().get(marketPubkey);
                        }
                    });

    final LoadingCache<PublicKey, EventQueue> eventQueueLoadingCache = CacheBuilder.newBuilder()
            .expireAfterWrite(GEYSER_CACHE_DURATION_MS, TimeUnit.MILLISECONDS)
            .build(
                    new CacheLoader<>() {
                        @Override
                        public EventQueue load(PublicKey marketPubkey) {
                            Market cachedMarket = marketCache.get(marketPubkey);
                            Request request = SerumDbClient.buildGetAccountInfoSerumDbRequest(cachedMarket.getEventQueueKey());

                            try (Response response = okHttpClient.newCall(request).execute()) {
                                ResponseBody responseBody = response.body();
                                byte[] data = responseBody.bytes();
                                AccountInfoRow accountInfoRow = objectMapper.readValue(
                                        data,
                                        AccountInfoRow.class
                                );

                                long slot = accountInfoRow.getSlot();
                                if (slot > currentSlot) {
                                    currentSlot = slot;
                                }

                                return EventQueue.readEventQueue(
                                        Base64.getDecoder().decode(accountInfoRow.getData()),
                                        cachedMarket.getBaseDecimals(),
                                        cachedMarket.getQuoteDecimals(),
                                        cachedMarket.getBaseLotSize(),
                                        cachedMarket.getQuoteLotSize()
                                );

                            } catch (Exception ex) {
                                // Case: HTTP exception
                                log.error(ex.getMessage());
                            }
                            return eventQueueLoadingCache.asMap().get(marketPubkey);
                        }
                    });

    public MarketManager(final TokenManager tokenManager,
                         final OkHttpClient okHttpClient,
                         final ObjectMapper objectMapper,
                         final SerumDbClient serumDbClient) {
        this.tokenManager = tokenManager;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.serumDbClient = serumDbClient;
        updateMarkets();
    }

    public List<Market> getMarketCache() {
        return new ArrayList<>(marketCache.values());
    }

    public List<Market> getMarketsByBaseMint(PublicKey tokenMint) {
        final Set<Market> result = new HashSet<>(marketMapCache.getOrDefault(tokenMint, new ArrayList<>()));
        return new ArrayList<>(result);
    }

    public List<Market> getMarketsByTokenMint(PublicKey tokenMint) {
        final Set<Market> result = new HashSet<>();

        result.addAll(marketMapCache.getOrDefault(tokenMint, new ArrayList<>()));
        result.addAll(marketMapQuoteMintCache.getOrDefault(tokenMint, new ArrayList<>()));

        return new ArrayList<>(result);
    }

    /**
     * Update marketCache with the latest markets
     */
    public void updateMarkets() {
        log.info("Caching all Serum markets.");
        final List<AccountInfoRow> accountInfoRows = serumDbClient.getAllMarkets();

        for (AccountInfoRow accountInfoRow : accountInfoRows) {
            Market market = Market.readMarket(accountInfoRow.getDecodedData());

            // Ignore fake/erroneous market accounts
            if (market.getOwnAddress().equals(new PublicKey("11111111111111111111111111111111"))) {
                continue;
            }

            market.setBaseDecimals((byte) tokenManager.getDecimals(market.getBaseMint()));
            market.setQuoteDecimals((byte) tokenManager.getDecimals(market.getQuoteMint()));
            marketCache.put(market.getOwnAddress(), market);

            // marketMapCache is a baseMint to List<Market> map which powers the token search.
            // Get list of existing markets for this base mint. otherwise create a new list and put it there.
            Set<Market> existingMarketList = new HashSet<>(marketMapCache.getOrDefault(market.getBaseMint(),
                    new ArrayList<>()));
            existingMarketList.add(market);

            // put it as a market for quote mint (as a base)
            Set<Market> existingMarketQuoteList = new HashSet<>(marketMapQuoteMintCache.getOrDefault(market.getQuoteMint(),
                    new ArrayList<>()));
            existingMarketQuoteList.add(market);

            marketMapCache.put(market.getBaseMint(), existingMarketList.stream().toList());
            marketMapQuoteMintCache.put(market.getQuoteMint(), existingMarketQuoteList.stream().toList());
        }

        final Map<PublicKey, Integer> marketsToPriceMap = new HashMap<>();
        for (Market market : marketCache.values()) {
            int marketCount = marketsToPriceMap.getOrDefault(market.getQuoteMint(), 0);
            marketsToPriceMap.put(market.getQuoteMint(), marketCount + 1);
        }

        final List<PublicKey> quoteMintsToPrice =
                marketsToPriceMap.entrySet().stream()
                        .filter(entry -> entry.getValue() >= MINIMUM_REQUIRED_MARKETS_FOR_PRICING)
                        .map(Map.Entry::getKey)
                        .toList();

        log.info("Pricing markets...");
        final Map<PublicKey, Market> mintToUsdcMarketPubkey = new HashMap<>();
        final Map<PublicKey, PublicKey> mintToBidOrderBook = new HashMap<>();

        // Get best known USDC market for quote mint
        quoteMintsToPrice.forEach(publicKey -> {
            List<Market> existing = marketMapCache.getOrDefault(publicKey, marketMapQuoteMintCache.get(publicKey));
            if (existing != null) {
                List<Market> baseMarkets = new ArrayList<>(existing);
                baseMarkets.sort(Comparator.comparingLong(Market::getQuoteDepositsTotal).reversed());
                for (Market baseMarket : baseMarkets) {
                    if (baseMarket.getQuoteMint().equals(MarketUtil.USDC_MINT) ||
                            baseMarket.getQuoteMint().equals(MarketUtil.USDT_MINT)) {
                        mintToUsdcMarketPubkey.put(publicKey, baseMarket);
                        mintToBidOrderBook.put(publicKey, baseMarket.getBids());
                        break;
                    }
                }
            }
        });

        // Request data for all bid orderbooks, for best usdc markets, for token mints with > 3 markets
        Collection<PublicKey> bidOrderBooks = mintToBidOrderBook.values();
        List<PublicKey> accountsToSearch = bidOrderBooks.stream().toList();
        Map<PublicKey, ByteBuffer> accountData = serumDbClient.getMultipleAccounts(accountsToSearch);

        quoteMintsToPrice.forEach(mintToPrice -> {
            if (accountData.containsKey(mintToBidOrderBook.get(mintToPrice))) {
                ByteBuffer buffer = accountData.get(mintToBidOrderBook.get(mintToPrice));
                if (buffer != null && buffer.hasArray() && buffer.array().length > 0) {
                    Market market = mintToUsdcMarketPubkey.get(mintToPrice);
                    OrderBook bidOrderbook = OrderBook.readOrderBook(buffer.array());

                    bidOrderbook.setBaseDecimals(market.getBaseDecimals());
                    bidOrderbook.setQuoteDecimals(market.getQuoteDecimals());
                    bidOrderbook.setBaseLotSize(market.getBaseLotSize());
                    bidOrderbook.setQuoteLotSize(market.getQuoteLotSize());

                    // getOrders is slightly inefficient, need to cache better
                    if (bidOrderbook.getOrders().size() > 0 && bidOrderbook.getSlab().getSlabNodes().get(0) != null) {
                        priceCache.put(mintToPrice, bidOrderbook.getBestBid().getFloatPrice());
                        log.info(mintToPrice + ": Price: " + priceCache.get(mintToPrice));
                    } else {
                        log.info(mintToPrice + ": No bids found..");
                    }
                }
            }
        });

        log.info("All Serum markets cached: " + accountInfoRows.size());
    }

    public int numMarketsByToken(PublicKey tokenMint) {
        return marketMapCache.getOrDefault(tokenMint, new ArrayList<>()).size();
    }

    public Optional<Market> getMarketById(String marketId) {
        return Optional.ofNullable(marketCache.get(PublicKey.valueOf(marketId)));
    }

    // note: stablecoin values are hardcoded since most liquidity is on saber/mercurial
    public float getQuoteNotional(Market market, int quoteDecimals) {
        float price = getQuoteMintPrice(market.getQuoteMint());
        if (price == 0.0f) {
            // check serum if not hardcoded (e.g. not a stablecoin)
            price = priceCache.getOrDefault(market.getQuoteMint(), 0.0f);
        }
        float totalQuantity = (float) ((double) market.getQuoteDepositsTotal() / SerumUtils.getQuoteSplTokenMultiplier((byte) quoteDecimals));
        return price * totalQuantity;
    }

    /**
     * Top 20 quote mints have their price calculated on startup / interval, used for subsequent calculations
     *
     * @param quoteMint token mint
     * @return best bid for token mint's USDC market
     */
    private float getQuoteMintPrice(PublicKey quoteMint) {
        // USDC, USDT, USDCet, UXD, soUSDT, USDH, soUSDC, PAI
        if (quoteMint.equals(MarketUtil.USDC_MINT) ||
                quoteMint.equals(MarketUtil.USDT_MINT) ||
                quoteMint.equals(PublicKey.valueOf("A9mUU4qviSctJVPJdBJWkb28deg915LYJKrzQ19ji3FM")) ||
                quoteMint.equals(PublicKey.valueOf("7kbnvuGBxxj8AG9qp8Scn56muWGaRaFqxg1FsRp3PaFT")) ||
                quoteMint.equals(PublicKey.valueOf("BQcdHdAQW1hczDbBi9hiegXAR7A98Q9jx3X3iBBBDiq4")) ||
                quoteMint.equals(PublicKey.valueOf("USDH1SM1ojwWUga67PGrgFWUHibbjqMvuMaDkRJTgkX")) ||
                quoteMint.equals(PublicKey.valueOf("BXXkv6z8ykpG1yuvUDPgh732wzVHB69RnB9YgSYh3itW")) ||
                quoteMint.equals(PublicKey.valueOf("Ea5SjE2Y6yvCeW5dYTn7PYMuW5ikXkvbGdcmSnXeaLjS"))
        ) {
            return 1f;
        }

        return 0;
    }

    public Optional<OrderBook> getCachedBidOrderBook(PublicKey marketPubkey) {
        try {
            return Optional.of(bidOrderBookLoadingCache.get(marketPubkey));
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }

    public Optional<OrderBook> getCachedAskOrderBook(PublicKey marketPubkey) {
        try {
            return Optional.of(askOrderBookLoadingCache.get(marketPubkey));
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }

    public Optional<EventQueue> getCachedEventQueue(PublicKey marketPubkey) {
        try {
            return Optional.of(eventQueueLoadingCache.get(marketPubkey));
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }

    private OrderBook buildOrderBook(byte[] data, Market market) {
        OrderBook orderBook = OrderBook.readOrderBook(data);
        orderBook.setBaseDecimals(market.getBaseDecimals());
        orderBook.setQuoteDecimals(market.getQuoteDecimals());
        orderBook.setBaseLotSize(market.getBaseLotSize());
        orderBook.setQuoteLotSize(market.getQuoteLotSize());

        return orderBook;
    }

    public long getCurrentSlot() {
        return currentSlot;
    }
}
