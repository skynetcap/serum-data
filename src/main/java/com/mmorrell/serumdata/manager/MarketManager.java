package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.Memcmp;
import org.p2p.solanaj.rpc.types.ProgramAccount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarketManager {

    private List<Market> marketCache = new ArrayList<>();

    public MarketManager() {
        updateMarkets();
    }

    public void setMarketCache(final List<Market> markets) {
        this.marketCache = markets;
    }

    public List<Market> getMarketCache() {
        return marketCache;
    }

    /**
     * Update marketCache with the latest markets
     */
    public void updateMarkets() {
        marketCache.clear();
        RpcClient client = new RpcClient("https://ssc-dao.genesysgo.net/");
        List<ProgramAccount> programAccounts = null;
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

        for(ProgramAccount programAccount : programAccounts) {
            Market market = Market.readMarket(programAccount.getAccount().getDecodedData());
            marketCache.add(market);
        }

        System.out.println("Cached markets.");
    }
}
