package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.MarketBuilder;
import ch.openserum.serum.model.TradeEvent;
import com.google.common.collect.ImmutableMap;
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

    /**
     *
     * @return
     * @throws RpcException
     */
    @GetMapping(value = "/api/serum/allMarkets")
    public List<String> getSerumMarkets() throws RpcException {
        return marketManager.getMarketCache().stream()
                .map(Market::getOwnAddress)
                .map(PublicKey::toBase58)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/serum/token/{tokenId}")
    public List<Map<String, Object>> getMarketsByBaseMint(@PathVariable String tokenId) {
        // return a list of Maps, similar to getMarket, instead of a direct list of Markets.
        List<Map<String, Object>> results = new ArrayList<>();
        List<Market> markets = marketManager.getMarketsByMint(tokenId);

        for (Market market : markets) {
            results.add(convertMarketToMap(market));
        }

        return results;
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

        // process the bids and asks objects, replace each "owner" with a reverse looked up owner if we have it.
        // otherwise, add it to a list, lookup all the missing owners on list, cache it,
        // and update the field with the new info
        identityManager.reverseOwnerLookup(bids, asks);

        result.put("bids", bids);
        result.put("asks", asks);

        // System.out.println("Result = " + result);
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
        modelAndView.addObject("tokens", tokenManager.getRegistry());
        return modelAndView;
    }

    private Map<String, Object> convertMarketToMap(Market market) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", market.getOwnAddress().toBase58());
        result.put("baseName", tokenManager.getTokenByMint(market.getBaseMint().toBase58()));
        result.put("baseMint", market.getBaseMint().toBase58());
        result.put("quoteName", tokenManager.getTokenByMint(market.getQuoteMint().toBase58()));
        result.put("quoteMint", market.getQuoteMint().toBase58());
        return result;
    }

    @GetMapping(value = "/api/serum/market/{marketId}/tradeHistory")
    public List<Map<String, Object>> getMarketTradeHistory(@PathVariable String marketId) {
        final ArrayList<Map<String, Object>> result = new ArrayList<>();
        final Market marketWithEventQueue = new MarketBuilder()
                .setClient(orderBookClient)
                .setPublicKey(PublicKey.valueOf(marketId))
                .setRetrieveEventQueue(true)
                .build();

        List<TradeEvent> tradeEvents = marketWithEventQueue.getEventQueue().getEvents();
        for (int i = 0; i < tradeEvents.size(); i++) {
            Map<String, Object> tradeEventEntry = new HashMap<>();
            TradeEvent event = tradeEvents.get(i);

            tradeEventEntry.put("index", i);
            tradeEventEntry.put("price", event.getFloatPrice());
            tradeEventEntry.put("quantity", event.getFloatQuantity());
            tradeEventEntry.put("owner", event.getOpenOrders().toBase58());
            tradeEventEntry.put("flags", ImmutableMap.of(
                    "fill", event.getEventQueueFlags().isFill(),
                    "out", event.getEventQueueFlags().isOut(),
                    "bid", event.getEventQueueFlags().isBid(),
                    "maker", event.getEventQueueFlags().isMaker()
            ));

            result.add(tradeEventEntry);
            //System.out.printf("Event: %s%n", tradeEvents.get(i).toString());
        }

        return result;
    }
}