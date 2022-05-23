package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.Market;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.TokenManager;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ApiController {

    private final TokenManager tokenManager;
    private final MarketManager marketManager;

    // Called on startup, loads our caches first etc
    // Auto-injected beans created by Component annotation
    public ApiController(TokenManager tokenManager, MarketManager marketManager) {
        this.tokenManager = tokenManager;
        this.marketManager = marketManager;
    }


    @GetMapping(value = "/api/test/{testValue}")
    public Map<String, Integer> getTest(@PathVariable Integer testValue) {
        Map<String, Integer> testMap = new HashMap<>();
        testMap.put("Test Key", testValue);
        return testMap;
    }

    @GetMapping(value = "/api/test/list")
    public List<Double> getListTest() {
        List<Double> result = new ArrayList<>();
        for(int i = 0; i < 10; i++) {
            result.add(Math.random());
        }
        return result;
    }

    /**
     *
     * @return
     * @throws RpcException
     */
    @GetMapping(value = "/api/serum/allMarkets")
    public List<String> getSerumMarkets() throws RpcException {
//        for(ProgramAccount programAccount : programAccounts) {
//            Market market = Market.readMarket(programAccount.getAccount().getDecodedData());
//            System.out.printf("Market: %s / USDC, ", tokenManager.getTokenByMint(market.getBaseMint().toBase58()));
//            System.out.printf("Market ID: %s", market.getOwnAddress().toBase58());
//            System.out.println();
//        }

        return marketManager.getMarketCache().stream()
                .map(Market::getOwnAddress)
                .map(PublicKey::toBase58)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/serum/market/{marketId}")
    public Map<String, Object> getMarket(@PathVariable String marketId) {
        Map<String, Object> result = new HashMap<>();
        Optional<Market> marketFromCache = marketManager.getMarketCache().stream()
                .filter(market -> market.getOwnAddress().toBase58().equalsIgnoreCase(marketId))
                .findFirst();

        if (marketFromCache.isEmpty()) {
            // make this an error, nothing was found
            return result;
        }

        Market market = marketFromCache.get();
        result.put("id", market.getOwnAddress().toBase58());
        result.put("baseName", tokenManager.getTokenByMint(market.getBaseMint().toBase58()));
        result.put("baseMint", market.getBaseMint().toBase58());
        result.put("quoteName", tokenManager.getTokenByMint(market.getQuoteMint().toBase58()));
        result.put("quoteMint", market.getQuoteMint().toBase58());

        // bids array
        // asks array
        // are todo

        return result;
    }

}