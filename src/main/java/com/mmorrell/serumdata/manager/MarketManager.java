package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.MarketBuilder;
import ch.openserum.serum.model.SerumUtils;
import com.mmorrell.serumdata.util.MarketUtil;
import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.ConfirmedTransaction;
import org.p2p.solanaj.rpc.types.Memcmp;
import org.p2p.solanaj.rpc.types.ProgramAccount;
import org.p2p.solanaj.rpc.types.SignatureInformation;
import org.p2p.solanaj.rpc.types.config.Commitment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class MarketManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketManager.class);
    private static final int MARKET_CACHE_TIMEOUT_SECONDS = 40;
    // <tokenMint, List<Market>>
    private final Map<String, List<Market>> marketMapCache = new HashMap<>();
    // <marketId, Builder>
    private final Map<String, MarketBuilder> marketBuilderCache = new HashMap<>();
    private final RpcClient client = new RpcClient(RpcUtil.getPublicEndpoint(), MARKET_CACHE_TIMEOUT_SECONDS);
    private final Map<String, CompletableFuture<Void>> tradeHistoryKeyToFutureMap = new HashMap<>();

    // <concat(marketId, ooa, owner), jupiterTx>
    private final Map<String, Optional<String>> jupiterTxMap = new HashMap<>();
    private final Map<String, Optional<String>> nonJupiterTxMap = new HashMap<>();

    // Jupiter
    private static final PublicKey JUPITER_PROGRAM_ID_V2 =
            PublicKey.valueOf("JUP2jxvXaqu7NQY1GmNF4m1vodw12LVXYxbFL2uJvfo");
    private static final PublicKey JUPITER_PROGRAM_ID_V3 =
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
            SerumUtils.WRAPPED_SOL_MINT
    );

    public MarketManager() {
        updateMarkets();
    }

    public Map<String, List<Market>> getMarketMapCache() {
        return marketMapCache;
    }

    public List<Market> getMarketCache() {
        return marketMapCache.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public List<Market> getMarketsByMint(String tokenMint) {
        return marketMapCache.getOrDefault(tokenMint, new ArrayList<>());
    }

    public void addBuilderToCache(MarketBuilder marketBuilder) {
        marketBuilderCache.put(marketBuilder.getPublicKey().toBase58(), marketBuilder);
    }

    public boolean isBuilderCached(String marketId) {
        return marketBuilderCache.containsKey(marketId);
    }

    public MarketBuilder getBuilderFromCache(String marketId) {
        return marketBuilderCache.get(marketId);
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
                LOGGER.info("Caching: " + quoteMint.toBase58());
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
                    LOGGER.info("Cached: " + quoteMint.toBase58());
                } catch (RpcException e) {
                    throw new RuntimeException(e);
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

            // Get list of existing markets for this base mint. otherwise create a new list and put it there.
            List<Market> existingMarketList = marketMapCache.getOrDefault(market.getBaseMint().toBase58(), new ArrayList<>());
            existingMarketList.add(market);

            if (existingMarketList.size() == 1) {
                marketMapCache.put(market.getBaseMint().toBase58(), existingMarketList);
            }
        }

        LOGGER.info(
                String.format(
                        "Markets cached for quoteMints: %s",
                        quoteMintsToCache.stream().map(PublicKey::toBase58).collect(Collectors.joining(", "))
                )
        );
    }

    public int numMarketsByToken(String tokenMint) {
        return marketMapCache.getOrDefault(tokenMint, new ArrayList<>()).size();
    }

    public Optional<Market> getMarketById(String marketId) {
        return marketMapCache.values().stream()
                .flatMap(Collection::stream)
                .filter(market -> market.getOwnAddress().toBase58().equals(marketId))
                .findAny();
    }

    public Optional<String> getTxForMarketAndOoa(String marketId, String ooa, String owner, float price, float quantity) {
        String uniqueKey = marketId.concat(ooa).concat(owner).concat(String.valueOf(price)).concat(String.valueOf(quantity));
        // bail out if its cached
        if (jupiterTxMap.containsKey(uniqueKey)) {
            // LOGGER.info("HAVE ANSWER: " + uniqueKey);
            return jupiterTxMap.get(uniqueKey);
        } else if (nonJupiterTxMap.containsKey(uniqueKey)) {
            return nonJupiterTxMap.get(uniqueKey);
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
                        PublicKey.valueOf(ooa),
                        15,
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
                    String programId = confirmedTransaction.getTransaction().getMessage().getAccountKeys().get((int) instruction.getProgramIdIndex());
                    boolean isJupiterInstruction = programId.equalsIgnoreCase(JUPITER_PROGRAM_ID_V3.toBase58()) ||
                            programId.equalsIgnoreCase(JUPITER_PROGRAM_ID_V2.toBase58());

                    // if it has OOA and Jup's referrer quote wallet, gg
                    // better to lookup the token account owner, hardcoding top 3 token types for now tho
                    boolean hasReferrer = false, hasOoa = false, hasSrm = false, hasMarket = false;
                    for (long accountIndex : instruction.getAccounts()) {
                        int index = (int) accountIndex;
                        String account = confirmedTransaction.getTransaction().getMessage().getAccountKeys().get(index);
                        if (account.equalsIgnoreCase(SerumUtils.SERUM_PROGRAM_ID_V3.toBase58())) {
                            hasSrm = true;
                        } else if (account.equalsIgnoreCase(ooa)) {
                            hasOoa = true;
                        } else if (account.equalsIgnoreCase(JUPITER_USDC_WALLET.toBase58()) ||
                                account.equalsIgnoreCase(JUPITER_USDT_WALLET.toBase58()) ||
                                account.equalsIgnoreCase(JUPITER_WSOL_WALLET.toBase58())) {
                            hasReferrer = true;
                        } else if (account.equalsIgnoreCase(marketId)) {
                            hasMarket = true;
                        }
                    }

                    if (hasOoa && hasReferrer && hasSrm && hasMarket && isJupiterInstruction) {
                        // LOGGER.info("FOUND->JUP: " + uniqueKey);
                        jupiterTxMap.put(uniqueKey, Optional.of(signatureInformation.getSignature()));
                        return;
                    } else if (hasOoa && !hasReferrer && hasSrm && hasMarket && !isJupiterInstruction) {
                        // Found Serum order tx, not jupiter, still give it to trade history
                        nonJupiterTxMap.put(uniqueKey, Optional.of(signatureInformation.getSignature()));
                        return;
                    }
                }

            }

            // LOGGER.info("FOUND->NOT-JUP: " + uniqueKey);
            jupiterTxMap.put(uniqueKey, Optional.empty());
            nonJupiterTxMap.put(uniqueKey, Optional.empty());
        });
        tradeHistoryKeyToFutureMap.put(uniqueKey, future);
        // LOGGER.info("THREAD START: " + uniqueKey);

        return Optional.empty();
    }

    public boolean isJupiterTx(String marketId, String ooa, String owner, float price, float quantity) {
        String uniqueKey = marketId.concat(ooa).concat(owner).concat(String.valueOf(price)).concat(String.valueOf(quantity));
        return jupiterTxMap.containsKey(uniqueKey);
    }
}
