package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import com.mmorrell.serumdata.util.MarketUtil;
import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.Memcmp;
import org.p2p.solanaj.rpc.types.ProgramAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MarketManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketManager.class);
    private static final int MARKET_CACHE_TIMEOUT_SECONDS = 40;
    private final Map<String, List<Market>> marketMapCache = new HashMap<>();
    private final RpcClient client = new RpcClient(RpcUtil.getPublicEndpoint(), MARKET_CACHE_TIMEOUT_SECONDS);

    // Cache USDC and SOL quoted markets.
    public final Set<PublicKey> quoteMintsToCache = Set.of(
            MarketUtil.USDC_MINT,
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

    /**
     * Update marketCache with the latest markets
     */
    public void updateMarkets() {
        LOGGER.info(
                String.format(
                        "Caching markets for quoteMints: %s",
                        quoteMintsToCache.stream().map(PublicKey::toBase58).collect(Collectors.joining(","))
                )
        );

        marketMapCache.clear();
        final List<ProgramAccount> programAccounts = new ArrayList<>();

        try {
            for (PublicKey quoteMint : quoteMintsToCache) {
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
            }
        } catch (RpcException e) {
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
                        quoteMintsToCache.stream().map(PublicKey::toBase58).collect(Collectors.joining(","))
                )
        );
    }

    public int numMarketsByToken(String tokenMint) {
        return marketMapCache.getOrDefault(tokenMint, new ArrayList<>()).size();
    }
}
