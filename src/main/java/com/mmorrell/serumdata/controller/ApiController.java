package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.MarketBuilder;
import ch.openserum.serum.model.Order;
import ch.openserum.serum.model.TradeEvent;
import com.google.common.collect.ImmutableMap;
import com.mmorrell.serumdata.manager.IdentityManager;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.SerumOrder;
import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ApiController {

    private final RpcClient orderBookClient = new RpcClient(RpcUtil.getPublicEndpoint());
    private final TokenManager tokenManager;
    private final MarketManager marketManager;
    private final IdentityManager identityManager;

    // Called on startup, loads our caches first etc
    // Auto-injected beans created by Component annotation
    public ApiController(TokenManager tokenManager,
                         MarketManager marketManager,
                         IdentityManager identityManager) {
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
    public List<String> getSerumMarkets() {
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
        result = convertMarketToMap(market);

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
        }

        return result;
    }

    /**
     * Retrieves the best bid and best ask price for the given marketId.
     *
     * @param marketId serum market id
     * @return best bid and best ask
     */
    @GetMapping(value = "/api/serum/market/{marketId}/spread")
    public Map<String, Object> getMarketSpread(@PathVariable String marketId) {
        final Market market = new MarketBuilder()
                .setClient(orderBookClient)
                .setPublicKey(PublicKey.valueOf(marketId))
                .setRetrieveEventQueue(false)
                .setRetrieveOrderBooks(true)
                .build();

        final Map<String, Object> result = new HashMap<>(convertMarketToMap(market));

        final Order bestBid = market.getBidOrderBook().getBestBid();
        final Order bestAsk = market.getAskOrderBook().getBestAsk();

        result.put("bidPrice", bestBid.getFloatPrice());
        result.put("bidQuantity", bestBid.getFloatQuantity());
        result.put("askPrice", bestAsk.getFloatPrice());
        result.put("askQuantity", bestAsk.getFloatQuantity());

        // Remove logo values for bandwidth efficiency. Client can look up themselves.
        result.remove("baseLogo");
        result.remove("quoteLogo");

        return result;
    }

    private Map<String, Object> convertMarketToMap(Market market) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", market.getOwnAddress().toBase58());
        result.put("baseName", tokenManager.getTokenByMint(market.getBaseMint().toBase58()));
        result.put("baseMint", market.getBaseMint().toBase58());
        result.put("baseSymbol", tokenManager.getTokenSymbolByMint(market.getBaseMint().toBase58()));
        result.put("baseLogo", tokenManager.getTokenLogoByMint(market.getBaseMint().toBase58()));
        result.put("quoteName", tokenManager.getTokenByMint(market.getQuoteMint().toBase58()));
        result.put("quoteMint", market.getQuoteMint().toBase58());
        result.put("quoteSymbol", tokenManager.getTokenSymbolByMint(market.getQuoteMint().toBase58()));
        result.put("quoteLogo", tokenManager.getTokenLogoByMint(market.getQuoteMint().toBase58()));
        return result;
    }
}