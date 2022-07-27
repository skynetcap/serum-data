package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
    private final Map<String, CompletableFuture<Void>> tradeHistoryKeyToFutureMap = new HashMap<>();

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

        return result.stream().toList();
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

        log.info("All Serum markets cached: " + programAccounts.size());
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

    /**
     * Top 20 quote mints have their price calculated on startup / interval, used for subsequent calculations
     *
     * @param quoteMint token mint
     * @return best bid for token mint's USDC market
     */
    private float getQuoteMintPrice(PublicKey quoteMint) {
        // USDC, USDT, USDCet, UXD, soUSDT
        // TODO: USDH
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

    public long getBidContext(PublicKey publicKey) {
        return bidOrderBookMinContextSlot.getOrDefault(publicKey, DEFAULT_MIN_CONTEXT_SLOT);
    }

    public long getAskContext(PublicKey publicKey) {
        return bidOrderBookMinContextSlot.getOrDefault(publicKey, DEFAULT_MIN_CONTEXT_SLOT);
    }
}
