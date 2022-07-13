package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mmorrell.serumdata.util.MarketUtil;
import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.*;
import org.p2p.solanaj.rpc.types.config.Commitment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class MarketManager {

    private static final int MARKET_CACHE_TIMEOUT_SECONDS = 20;
    private final RpcClient client = new RpcClient(RpcUtil.getPublicEndpoint(), MARKET_CACHE_TIMEOUT_SECONDS);
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketManager.class);

    // Managers
    private final TokenManager tokenManager;

    // <marketPubkey, Market>
    private final Map<PublicKey, Market> marketCache = new HashMap<>();
    // <tokenMint, List<Market>>
    private final Map<PublicKey, List<Market>> marketMapCache = new HashMap<>();
    private final Map<String, CompletableFuture<Void>> tradeHistoryKeyToFutureMap = new HashMap<>();
    private static final Boolean SKIP_CACHE_DELAY = System.getenv("SKIP_CACHE_DELAY") != null && Boolean.parseBoolean(System.getenv("SKIP_CACHE_DELAY"));

    // Solana Context
    private final long DEFAULT_MIN_CONTEXT_SLOT = 0L;
    private final Map<PublicKey, Long> askOrderBookMinContextSlot = new HashMap<>();
    private final Map<PublicKey, Long> bidOrderBookMinContextSlot = new HashMap<>();
    private final Map<PublicKey, Long> eventQueueMinContextSlot = new HashMap<>();

    // Caching for individual bid and asks orderbooks.
    final LoadingCache<PublicKey, OrderBook> bidOrderBookLoadingCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(1, TimeUnit.SECONDS)
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

                                // LOGGER.info("BID new context! " + accountInfo.getContext().getSlot());
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
                                // LOGGER.info("bids exception: returning map");
                                return bidOrderBookLoadingCache.asMap().get(marketPubkey);
                            }
                        }
                    });

    // Caching for individual bid and asks orderbooks.
    final LoadingCache<PublicKey, OrderBook> askOrderBookLoadingCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(1, TimeUnit.SECONDS)
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

                                // LOGGER.info("ASK new context! " + accountInfo.getContext().getSlot());
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
                                // LOGGER.info("asks exception: returning map");
                                return askOrderBookLoadingCache.asMap().get(marketPubkey);
                            }
                        }
                    });

    final LoadingCache<PublicKey, EventQueue> eventQueueLoadingCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(2500, TimeUnit.MILLISECONDS)
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

                                // LOGGER.info("EQ new context! " + accountInfo.getContext().getSlot());
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
                                // LOGGER.info("EQ context error, using map cache.");
                                return eventQueueLoadingCache.asMap().get(marketPubkey);
                            }
                        }
                    });

    // <concat(marketId, ooa, owner), jupiterTx>
    private final Map<String, Optional<String>> jupiterTxMap = new HashMap<>();

    // Jupiter
    private static final PublicKey JUPITER_PROGRAM_ID =
            PublicKey.valueOf("JUP3c2Uh3WA4Ng34tw6kPd2G4C5BB21Xo36Je1s32Ph");
    private static final PublicKey JUPITER_USDC_WALLET =
            PublicKey.valueOf("H5sizxhR6ssXrX2YNDoYaUv93PU34VzyRaVaUHuo5eFk");
    private static final PublicKey JUPITER_USDT_WALLET =
            PublicKey.valueOf("FVKG6bkrQ4rksme6GT1FN7PgvZf9cNmupyWfN5kJj8Fx");
    private static final PublicKey JUPITER_WSOL_WALLET =
            PublicKey.valueOf("61CjGbapEVoyCC51x5tPZGZHCYsgtPSSssCatHEEUWeG");

    // Cache USDC and SOL quoted markets.
    public final Set<PublicKey> quoteMintsToCache = Set.of(
            MarketUtil.USDC_MINT,
            MarketUtil.USDT_MINT,
            SerumUtils.WRAPPED_SOL_MINT,
            PublicKey.valueOf("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So"), // MSOL
            PublicKey.valueOf("A9mUU4qviSctJVPJdBJWkb28deg915LYJKrzQ19ji3FM"), // USDCet
            PublicKey.valueOf("7dHbWXmci3dT8UFYWYZweBLXgycu7Y3iL6trKn1Y7ARj"), // stSOL
            PublicKey.valueOf("4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R"), // RAY
            PublicKey.valueOf("7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs"), // ETH (Portal)
            PublicKey.valueOf("7kbnvuGBxxj8AG9qp8Scn56muWGaRaFqxg1FsRp3PaFT"), // UXD
            PublicKey.valueOf("2FPyTwcZLUg1MDrwsyoP4D6s1tM7hAkHYRjkNb5w6Pxk"), // soETH
            PublicKey.valueOf("9n4nbM75f5Ui33ZbPYXn59EwSgE8CGsHtAeTH5YFeJ9E"), // BTC (Sollet)
            PublicKey.valueOf("SRMuApVNdxXokk5GT7XD5cUUgXMBCoAz2LHeuAoKWRt"), // SRM
            PublicKey.valueOf("BQcdHdAQW1hczDbBi9hiegXAR7A98Q9jx3X3iBBBDiq4"), // soUSDT
            PublicKey.valueOf("ATLASXmbPQxBUYbxPsV97usA3fPQYEqzQBUHgiFCUsXx"), // ATLAS
            PublicKey.valueOf("9vMJfxuKxXBoEa7rM12mYLMwTacLMLDJqHozw96WQL8i") // UST (Portal)
    );

    public MarketManager(final TokenManager tokenManager) {
        this.tokenManager = tokenManager;
        updateMarkets();
    }

    public List<Market> getMarketCache() {
        return marketMapCache.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public List<Market> getMarketsByMint(PublicKey tokenMint) {
        return marketMapCache.getOrDefault(tokenMint, new ArrayList<>());
    }

    /**
     * Update marketCache with the latest markets
     */
    public void updateMarkets() {
        LOGGER.info(
                String.format(
                        "Caching markets for quoteMints: %s",
                        quoteMintsToCache.stream().map(PublicKey::toBase58).collect(Collectors.joining(", "))
                )
        );

        marketMapCache.clear();
        final Collection<ProgramAccount> programAccounts = new ConcurrentLinkedQueue<>();
        final List<CompletableFuture<Void>> marketCacheThreads = new ArrayList<>();

        for (PublicKey quoteMint : quoteMintsToCache) {
            // Create each thread
            final CompletableFuture<Void> marketCacheThread = CompletableFuture.supplyAsync(() -> {
                if (!SKIP_CACHE_DELAY) {
                    int delay = new Random().nextInt(12000);

                    // Random delay to not get rate limited.
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    LOGGER.info("Requesting: " + quoteMint.toBase58());
                    programAccounts.addAll(
                            client.getApi().getProgramAccounts(
                                    SerumUtils.SERUM_PROGRAM_ID_V3,
                                    List.of(
                                            new Memcmp(
                                                    SerumUtils.QUOTE_MINT_OFFSET,
                                                    quoteMint.toBase58()
                                            )
                                    ),
                                    SerumUtils.MARKET_ACCOUNT_SIZE
                            )
                    );
                    LOGGER.info("Cached: " + quoteMint.toBase58());
                } catch (RpcException e) {
                    LOGGER.info("Requesting: " + quoteMint.toBase58());
                    try {
                        programAccounts.addAll(
                                client.getApi().getProgramAccounts(
                                        SerumUtils.SERUM_PROGRAM_ID_V3,
                                        List.of(
                                                new Memcmp(
                                                        SerumUtils.QUOTE_MINT_OFFSET,
                                                        quoteMint.toBase58()
                                                )
                                        ),
                                        SerumUtils.MARKET_ACCOUNT_SIZE
                                )
                        );
                    } catch (RpcException ex) {
                        throw new RuntimeException(ex);
                    }
                    LOGGER.info("Cached: " + quoteMint.toBase58());
                }
                return null;
            });
            marketCacheThreads.add(marketCacheThread);
        }

        final CompletableFuture<Void> combinedFutures =
                CompletableFuture.allOf(marketCacheThreads.toArray(new CompletableFuture[0]));
        try {
            // Wait for all threads to complete.
            combinedFutures.get();
            LOGGER.info("Market caching threads complete.");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        for (ProgramAccount programAccount : programAccounts) {
            Market market = Market.readMarket(programAccount.getAccount().getDecodedData());
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

            // Get list of existing markets for this base mint. otherwise create a new list and put it there.
            List<Market> existingMarketList = marketMapCache.getOrDefault(market.getBaseMint(), new ArrayList<>());
            existingMarketList.add(market);

            if (existingMarketList.size() == 1) {
                marketMapCache.put(market.getBaseMint(), existingMarketList);
            }
        }

        LOGGER.info(
                String.format(
                        "Markets cached for quoteMints: %s",
                        quoteMintsToCache.stream().map(PublicKey::toBase58).collect(Collectors.joining(", "))
                )
        );
    }

    public int numMarketsByToken(PublicKey tokenMint) {
        return marketMapCache.getOrDefault(tokenMint, new ArrayList<>()).size();
    }

    public Optional<Market> getMarketById(String marketId) {
        return Optional.ofNullable(marketCache.get(PublicKey.valueOf(marketId)));
    }

    public Optional<String> getJupiterTxForMarketAndOoa(
            PublicKey marketId,
            PublicKey ooa,
            PublicKey owner,
            float price,
            float quantity
    ) {
        String uniqueKey = marketId.toString().concat(ooa.toString()).concat(owner.toString()).concat(String.valueOf(price)).concat(String.valueOf(quantity));

        // bail out if its cached
        if (jupiterTxMap.containsKey(uniqueKey)) {
            // LOGGER.info("HAVE ANSWER: " + uniqueKey);
            return jupiterTxMap.get(uniqueKey);
        }

        // bail if were already working on it
        if (tradeHistoryKeyToFutureMap.containsKey(uniqueKey)) {
            if (!tradeHistoryKeyToFutureMap.get(uniqueKey).isDone()) {
                // LOGGER.info("WORKING: " + uniqueKey);
                return Optional.empty();
            }
        }


        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            List<SignatureInformation> confirmedSignatures;
            try {
                confirmedSignatures = client.getApi().getSignaturesForAddress(
                        owner,
                        10,
                        Commitment.CONFIRMED
                );
            } catch (RpcException e) {
                throw new RuntimeException(e);
            }

            for (SignatureInformation signatureInformation : confirmedSignatures) {
                ConfirmedTransaction confirmedTransaction;
                try {
                    confirmedTransaction = client.getApi().getTransaction(signatureInformation.getSignature(), Commitment.CONFIRMED);
                } catch (RpcException e) {
                    throw new RuntimeException(e);
                }
                if (confirmedTransaction.getTransaction() == null || confirmedTransaction.getTransaction().getMessage() == null) {
                    break;
                }
                for (ConfirmedTransaction.Instruction instruction : confirmedTransaction.getTransaction().getMessage().getInstructions()) {
                    final PublicKey programId = new PublicKey(
                            confirmedTransaction.getTransaction().getMessage().getAccountKeys().get(
                                    (int) instruction.getProgramIdIndex()
                            )
                    );

                    if (programId.equals(JUPITER_PROGRAM_ID)) {
                        // if it has OOA and Jup's referrer quote wallet, gg
                        // better to lookup the token account owner, hardcoding top 3 token types for now tho
                        boolean hasReferrer = false, hasOoa = false, hasSrm = false, hasMarket = false;
                        for (long accountIndex : instruction.getAccounts()) {
                            int index = (int) accountIndex;
                            PublicKey account = new PublicKey(
                                    confirmedTransaction.getTransaction().getMessage().getAccountKeys().get(index)
                            );

                            if (account.equals(SerumUtils.SERUM_PROGRAM_ID_V3)) {
                                hasSrm = true;
                            } else if (account.equals(ooa)) {
                                hasOoa = true;
                            } else if (account.equals(JUPITER_USDC_WALLET) ||
                                    account.equals(JUPITER_USDT_WALLET) ||
                                    account.equals(JUPITER_WSOL_WALLET)) {
                                hasReferrer = true;
                            } else if (account.equals(marketId)) {
                                hasMarket = true;
                            }
                        }

                        if (hasOoa && hasReferrer && hasSrm && hasMarket) {
                            // LOGGER.info("FOUND->JUP: " + uniqueKey);
                            jupiterTxMap.put(uniqueKey, Optional.of(signatureInformation.getSignature()));
                            return;
                        }
                    }
                }

            }

            // LOGGER.info("FOUND->NOT-JUP: " + uniqueKey);
            jupiterTxMap.put(uniqueKey, Optional.empty());
        });
        tradeHistoryKeyToFutureMap.put(uniqueKey, future);
        // LOGGER.info("THREAD START: " + uniqueKey);

        return Optional.empty();
    }

    public float getQuoteNotional(Market market, int quoteDecimals) {
        float price = getQuoteMintPrice(market.getQuoteMint());
        float totalQuantity = (float) ((double) market.getQuoteDepositsTotal() / SerumUtils.getQuoteSplTokenMultiplier((byte) quoteDecimals));
        return price * totalQuantity;
    }

    // TODO: get real price, for now just use for *rough* sorting of the top markets
    private float getQuoteMintPrice(PublicKey quoteMint) {
        // USDC, USDT, USDCet, UXD, soUSDT
        if (quoteMint.equals(MarketUtil.USDC_MINT) ||
                quoteMint.equals(MarketUtil.USDT_MINT) ||
                quoteMint.equals(PublicKey.valueOf("A9mUU4qviSctJVPJdBJWkb28deg915LYJKrzQ19ji3FM")) ||
                quoteMint.equals(PublicKey.valueOf("7kbnvuGBxxj8AG9qp8Scn56muWGaRaFqxg1FsRp3PaFT")) ||
                quoteMint.equals(PublicKey.valueOf("BQcdHdAQW1hczDbBi9hiegXAR7A98Q9jx3X3iBBBDiq4"))
        ) {
            return 1f;
        }

        // SOL, mSOL, stSOL
        if (quoteMint.equals(SerumUtils.WRAPPED_SOL_MINT) ||
                quoteMint.equals(PublicKey.valueOf("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So")) ||
                quoteMint.equals(PublicKey.valueOf("7dHbWXmci3dT8UFYWYZweBLXgycu7Y3iL6trKn1Y7ARj"))
        ) {
            return 39f;
        }

        // RAY
        if (quoteMint.equals(PublicKey.valueOf("4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R"))) {
            return 0.74f;
        }

        // SRM
        if (quoteMint.equals(PublicKey.valueOf("SRMuApVNdxXokk5GT7XD5cUUgXMBCoAz2LHeuAoKWRt"))) {
            return 0.88f;
        }

        // ETH (Portal), soETH
        if (quoteMint.equals(PublicKey.valueOf("7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs")) ||
                quoteMint.equals(PublicKey.valueOf("2FPyTwcZLUg1MDrwsyoP4D6s1tM7hAkHYRjkNb5w6Pxk"))) {
            return 1250f;
        }

        // BTC
        if (quoteMint.equals(PublicKey.valueOf("9n4nbM75f5Ui33ZbPYXn59EwSgE8CGsHtAeTH5YFeJ9E"))) {
            return 22000f;
        }

        // ATLAS
        if (quoteMint.equals(PublicKey.valueOf("ATLASXmbPQxBUYbxPsV97usA3fPQYEqzQBUHgiFCUsXx"))) {
            return 0.00689177f;
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
}
