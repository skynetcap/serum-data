package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.mmorrell.serumdata.util.MarketUtil;
import lombok.extern.slf4j.Slf4j;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.*;
import org.p2p.solanaj.rpc.types.config.Commitment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MarketManager {

    private static final int ORDER_BOOK_CACHE_DURATION_SECONDS = 1;
    private static final int EVENT_QUEUE_CACHE_DURATION_MS = 2500;

    private final RpcClient client;
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

    // Solana Context
    private final long DEFAULT_MIN_CONTEXT_SLOT = 0L;
    private final Map<PublicKey, Long> askOrderBookMinContextSlot = new HashMap<>();
    private final Map<PublicKey, Long> bidOrderBookMinContextSlot = new HashMap<>();
    private final Map<PublicKey, Long> eventQueueMinContextSlot = new HashMap<>();

    // Caching for individual bid and asks orderbooks.
    final LoadingCache<PublicKey, OrderBook> bidOrderBookLoadingCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(ORDER_BOOK_CACHE_DURATION_SECONDS, TimeUnit.SECONDS)
            .build(
                    new CacheLoader<>() {
                        @Override
                        public OrderBook load(PublicKey marketPubkey) {
                            try {
                                Market cachedMarket = marketCache.get(marketPubkey);
                                long slotToUse = bidOrderBookMinContextSlot.getOrDefault(marketPubkey, DEFAULT_MIN_CONTEXT_SLOT);

                                AccountInfo accountInfo = client.getApi()
                                        .getAccountInfo(
                                                cachedMarket.getBids(),
                                                Map.of(
                                                        "minContextSlot",
                                                        slotToUse,
                                                        "commitment",
                                                        Commitment.CONFIRMED
                                                )
                                        );

                                bidOrderBookMinContextSlot.put(marketPubkey, accountInfo.getContext().getSlot());

                                return buildOrderBook(
                                        Base64.getDecoder().decode(
                                                accountInfo.getValue()
                                                        .getData()
                                                        .get(0)
                                        ),
                                        cachedMarket
                                );
                            } catch (RpcException ex) {
                                return bidOrderBookLoadingCache.asMap().get(marketPubkey);
                            }
                        }
                    });

    // Caching for individual bid and asks orderbooks.
    final LoadingCache<PublicKey, OrderBook> askOrderBookLoadingCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(ORDER_BOOK_CACHE_DURATION_SECONDS, TimeUnit.SECONDS)
            .build(
                    new CacheLoader<>() {
                        @Override
                        public OrderBook load(PublicKey marketPubkey) {
                            try {
                                Market cachedMarket = marketCache.get(marketPubkey);
                                long slotToUse = askOrderBookMinContextSlot.getOrDefault(marketPubkey, DEFAULT_MIN_CONTEXT_SLOT);

                                AccountInfo accountInfo = client.getApi()
                                        .getAccountInfo(
                                                cachedMarket.getAsks(),
                                                Map.of(
                                                        "minContextSlot",
                                                        slotToUse,
                                                        "commitment",
                                                        Commitment.CONFIRMED
                                                )
                                        );

                                askOrderBookMinContextSlot.put(marketPubkey, accountInfo.getContext().getSlot());

                                return buildOrderBook(
                                        Base64.getDecoder().decode(
                                                accountInfo.getValue()
                                                        .getData()
                                                        .get(0)
                                        ),
                                        cachedMarket
                                );
                            } catch (RpcException ex) {
                                return askOrderBookLoadingCache.asMap().get(marketPubkey);
                            }
                        }
                    });

    final LoadingCache<PublicKey, EventQueue> eventQueueLoadingCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(EVENT_QUEUE_CACHE_DURATION_MS, TimeUnit.MILLISECONDS)
            .build(
                    new CacheLoader<>() {
                        @Override
                        public EventQueue load(PublicKey marketPubkey) {
                            try {
                                Market cachedMarket = marketCache.get(marketPubkey);
                                long slotToUse = eventQueueMinContextSlot.getOrDefault(marketPubkey, DEFAULT_MIN_CONTEXT_SLOT);

                                AccountInfo accountInfo = client.getApi()
                                        .getAccountInfo(
                                                cachedMarket.getEventQueueKey(),
                                                Map.of(
                                                        "minContextSlot",
                                                        slotToUse,
                                                        "commitment",
                                                        Commitment.CONFIRMED
                                                )
                                        );

                                eventQueueMinContextSlot.put(marketPubkey, accountInfo.getContext().getSlot());

                                return EventQueue.readEventQueue(
                                        Base64.getDecoder().decode(
                                                accountInfo.getValue().getData().get(0)
                                        ),
                                        cachedMarket.getBaseDecimals(),
                                        cachedMarket.getQuoteDecimals(),
                                        cachedMarket.getBaseLotSize(),
                                        cachedMarket.getQuoteLotSize()
                                );
                            } catch (RpcException ex) {
                                return eventQueueLoadingCache.asMap().get(marketPubkey);
                            }
                        }
                    });

    public MarketManager(final TokenManager tokenManager, final RpcClient rpcClient) {
        this.tokenManager = tokenManager;
        this.client = rpcClient;
        updateMarkets();
    }

    public List<Market> getMarketCache() {
        return new ArrayList<>(marketCache.values());
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
        final List<ProgramAccount> programAccounts;

        try {
            programAccounts = new ArrayList<>(
                    client.getApi().getProgramAccounts(
                            SerumUtils.SERUM_PROGRAM_ID_V3,
                            Collections.emptyList(),
                            SerumUtils.MARKET_ACCOUNT_SIZE
                    )
            );
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }

        for (ProgramAccount programAccount : programAccounts) {
            Market market = Market.readMarket(programAccount.getAccount().getDecodedData());

            // Ignore fake/erroneous market accounts
            if (market.getOwnAddress().equals(new PublicKey("11111111111111111111111111111111"))) {
                continue;
            }

            market.setBaseDecimals(
                    (byte) tokenManager.getDecimals(
                            market.getBaseMint()
                    )
            );
            market.setQuoteDecimals(
                    (byte) tokenManager.getDecimals(
                            market.getQuoteMint()
                    )
            );
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
        Map<PublicKey, Optional<AccountInfo.Value>> accountData = new HashMap<>();
        Collection<PublicKey> bidOrderBooks = mintToBidOrderBook.values();
        List<List<PublicKey>> accountsToSearchList = Lists.partition(bidOrderBooks.stream().toList(), 100);
        for (List<PublicKey> publicKeys : accountsToSearchList) {
            try {
                accountData.putAll(client.getApi().getMultipleAccountsMap(
                                new ArrayList<>(publicKeys)
                        )
                );
            } catch (RpcException e) {
                throw new RuntimeException(e);
            }
        }

        quoteMintsToPrice.forEach(mintToPrice -> {
            if (accountData.containsKey(mintToBidOrderBook.get(mintToPrice))) {
                Optional<AccountInfo.Value> data = accountData.get(mintToBidOrderBook.get(mintToPrice));
                if (data.isPresent()) {
                    Market market = mintToUsdcMarketPubkey.get(mintToPrice);
                    byte[] decoded = Base64.getDecoder().decode(data.get().getData().get(0));
                    OrderBook bidOrderbook = OrderBook.readOrderBook(decoded);
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

        log.info("All Serum markets cached: " + programAccounts.size());
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

    public long getBidContext(PublicKey publicKey) {
        return bidOrderBookMinContextSlot.getOrDefault(publicKey, DEFAULT_MIN_CONTEXT_SLOT);
    }

    public long getAskContext(PublicKey publicKey) {
        return bidOrderBookMinContextSlot.getOrDefault(publicKey, DEFAULT_MIN_CONTEXT_SLOT);
    }
}
