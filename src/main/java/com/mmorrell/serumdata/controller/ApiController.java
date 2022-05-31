package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.MarketBuilder;
import com.mmorrell.serumdata.manager.IdentityManager;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.SerumOrder;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ApiController {

    private final RpcClient orderBookClient = new RpcClient("https://ssc-dao.genesysgo.net/");
    private final TokenManager tokenManager;
    private final MarketManager marketManager;
    private final IdentityManager identityManager;


    // Called on startup, loads our caches first etc
    // Auto-injected beans created by Component annotation
    public ApiController(TokenManager tokenManager, MarketManager marketManager, IdentityManager identityManager) {
        this.tokenManager = tokenManager;
        this.marketManager = marketManager;
        this.identityManager = identityManager;
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

        final Market marketWithOrderBooks = new MarketBuilder()
                .setClient(orderBookClient)
                .setPublicKey(PublicKey.valueOf(marketId))
                .setRetrieveOrderBooks(true)
                .build();

        Market market = marketFromCache.get();
        result.put("id", market.getOwnAddress().toBase58());
        result.put("baseName", tokenManager.getTokenByMint(market.getBaseMint().toBase58()));
        result.put("baseMint", market.getBaseMint().toBase58());
        result.put("quoteName", tokenManager.getTokenByMint(market.getQuoteMint().toBase58()));
        result.put("quoteMint", market.getQuoteMint().toBase58());

        List<SerumOrder> bids = marketWithOrderBooks.getBidOrderBook().getOrders().stream()
                .map(order -> {
                            SerumOrder serumOrder = new SerumOrder();
                            serumOrder.setPrice(order.getFloatPrice());
                            serumOrder.setQuantity(order.getFloatQuantity());
                            serumOrder.setOwner(order.getOwner().toBase58());
                            return serumOrder;
                })
                .sorted((o1, o2) -> Float.compare(o2.getPrice(), o1.getPrice()))
                .collect(Collectors.toList());

        List<SerumOrder> asks = marketWithOrderBooks.getAskOrderBook().getOrders().stream()
                .map(order -> {
                    SerumOrder serumOrder = new SerumOrder();
                    serumOrder.setPrice(order.getFloatPrice());
                    serumOrder.setQuantity(order.getFloatQuantity());
                    serumOrder.setOwner(order.getOwner().toBase58());
                    return serumOrder;
                })
                .sorted((o1, o2) -> Float.compare(o1.getPrice(), o2.getPrice()))
                .collect(Collectors.toList());

        result.put("bids", bids);
        result.put("asks", asks);

        System.out.println("Result = " + result);
        return result;
    }

    @GetMapping(value = "/api/serum/market/{marketId}/spreads")
    public Map<String, Integer> getMarketMakerSpreads(@PathVariable String marketId) {
        // 9wFFyRfZBsuAha4YcuxcXLKwMxJR43S7fPfQLusDBzvT
        final Market marketWithOrderBooks = new MarketBuilder()
                .setClient(orderBookClient)
                .setPublicKey(PublicKey.valueOf(marketId))
                .setRetrieveEventQueue(true)
                .build();


        // make map of top open orders
        Map<String, Integer> counter = new HashMap<>();
        marketWithOrderBooks.getEventQueue().getEvents().forEach(tradeEvent -> {
            counter.put(tradeEvent.getOpenOrders().toBase58(), counter.getOrDefault(tradeEvent.getOpenOrders().toBase58(), 0) + 1);
        });

        LinkedHashMap<String, Integer> reverseSortedMap = new LinkedHashMap<>();
        counter.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));

        // cache all their true accounts from market offset

        System.out.println("Market: " + marketId);
        System.out.println("Market Makers: " + reverseSortedMap);
        System.out.println();

        reverseSortedMap.forEach((mm, count) -> {
            PublicKey owner = identityManager.lookupAndAddOwnerToCache(PublicKey.valueOf(mm));
            String ownerName = identityManager.getNameByOwner(owner);
            System.out.printf(
                    "MM: %s, # Trades: %d%s%n",
                    owner.toBase58(),
                    count,
                    ownerName != null ? ", Identified as (" + ownerName + ")" : ""
            );
        });

        return reverseSortedMap;
    }

    @GetMapping("/")
    public ModelAndView passParametersWithModelAndView() {
        ModelAndView modelAndView = new ModelAndView("index");
        modelAndView.addObject("testVal", "Hi, it worked!");
        modelAndView.addObject("tokens", tokenManager.getRegistry());
        return modelAndView;
    }
}