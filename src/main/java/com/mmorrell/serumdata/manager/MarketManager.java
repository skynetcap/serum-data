package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.Memcmp;
import org.p2p.solanaj.rpc.types.ProgramAccount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MarketManager {

    private final Map<String, List<Market>> marketMapCache = new HashMap<>();

    public MarketManager() {
        updateMarkets();
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
        marketMapCache.clear();
        RpcClient client = new RpcClient("https://ssc-dao.genesysgo.net/");
        List<ProgramAccount> programAccounts = new ArrayList<>();
        try {
            programAccounts = client.getApi().getProgramAccounts(
                    SerumUtils.SERUM_PROGRAM_ID_V3,
                    List.of(
                            new Memcmp(
                                    85,
                                    "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
                            )
                    ),
                    388
            );
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }

        // also cache SOL quoted markets for better coverage.
        try {
            programAccounts.addAll(
                client.getApi().getProgramAccounts(
                    SerumUtils.SERUM_PROGRAM_ID_V3,
                    List.of(
                            new Memcmp(
                                    85,
                                    "So11111111111111111111111111111111111111112"
                            )
                    ),
                    388
                )
            );
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }

        for(ProgramAccount programAccount : programAccounts) {
            Market market = Market.readMarket(programAccount.getAccount().getDecodedData());

            // Get list of existing markets for this base mint. otherwise create a new list and put it there.
            List<Market> existingMarketList = marketMapCache.getOrDefault(market.getBaseMint().toBase58(), new ArrayList<>());
            existingMarketList.add(market);

            if (existingMarketList.size() == 1) {
                marketMapCache.put(market.getBaseMint().toBase58(), existingMarketList);
            }
        }

        System.out.println("Cached markets.");
    }
}
