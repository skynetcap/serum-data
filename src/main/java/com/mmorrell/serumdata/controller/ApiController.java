package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.*;
import com.google.common.collect.ImmutableMap;
import com.mmorrell.serumdata.manager.IdentityManager;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.SerumOrder;
import com.mmorrell.serumdata.util.MarketUtil;
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
        return convertOrdersAndLookup(market, marketWithOrderBooks.getBidOrderBook(), marketWithOrderBooks.getAskOrderBook());
    }

    @GetMapping(value = "/api/serum/market/{marketId}/cached")
    public Map<String, Object> getMarketCached(@PathVariable String marketId) {
        MarketBuilder builder;
        Market market;

        // check builder map
        boolean isBuilderCached = marketManager.isBuilderCached(marketId);
        if (isBuilderCached) {
            builder = marketManager.getBuilderFromCache(marketId);
            market = builder.reload();
        } else {
            builder = new MarketBuilder()
                    .setClient(orderBookClient)
                    .setPublicKey(PublicKey.valueOf(marketId))
                    .setRetrieveOrderBooks(true)
                    .setOrderBookCacheEnabled(true);
            market = builder.build();

            // add to cache, saves decimal cache and initial account poll
            marketManager.addBuilderToCache(builder);
        }

        return convertOrdersAndLookup(market, market.getBidOrderBook(), market.getAskOrderBook());
    }

    private Map<String, Object> convertOrdersAndLookup(Market market, OrderBook bidOrderBook, OrderBook askOrderBook) {
        Map<String, Object> result = convertMarketToMap(market);

        List<SerumOrder> bids = MarketUtil.convertOrderBookToSerumOrders(bidOrderBook, true);
        List<SerumOrder> asks = MarketUtil.convertOrderBookToSerumOrders(askOrderBook, false);

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

            PublicKey taker = identityManager.lookupAndAddOwnerToCache(event.getOpenOrders());
            String owner = identityManager.hasReverseLookup(taker) ?
                    identityManager.getEntityNameByOwner(taker) :
                    taker.toBase58();

            tradeEventEntry.put("index", i);
            tradeEventEntry.put("price", event.getFloatPrice());
            tradeEventEntry.put("quantity", event.getFloatQuantity());
            tradeEventEntry.put("owner", owner);
            tradeEventEntry.put("flags", ImmutableMap.of(
                            "fill", event.getEventQueueFlags().isFill(),
                            "out", event.getEventQueueFlags().isOut(),
                            "bid", event.getEventQueueFlags().isBid(),
                            "maker", event.getEventQueueFlags().isMaker()
                    )
            );
            tradeEventEntry.put("icon", identityManager.getEntityIconByOwner(identityManager.lookupAndAddOwnerToCache(event.getOpenOrders())));

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
    // TODO - cache these builders with different params somehow
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

        // Treemap for alphabetical sorting.
        return new TreeMap<>(result);
    }

    // todo - refactor + dedupe
    @GetMapping(value = "/api/serum/market/{marketId}/depth")
    public Map<String, Object> getMarketDepth(@PathVariable String marketId) {
        final Map<String, Object> result = new HashMap<>();
        MarketBuilder builder;
        Market market;

        // check builder map
        boolean isBuilderCached = marketManager.isBuilderCached(marketId);
        if (isBuilderCached) {
            builder = marketManager.getBuilderFromCache(marketId);
            market = builder.reload();
        } else {
            builder = new MarketBuilder()
                    .setClient(orderBookClient)
                    .setPublicKey(PublicKey.valueOf(marketId))
                    .setRetrieveOrderBooks(true)
                    .setOrderBookCacheEnabled(true);
            market = builder.build();

            // add to cache, saves decimal cache and initial account poll
            marketManager.addBuilderToCache(builder);
        }

        List<SerumOrder> bids = MarketUtil.convertOrderBookToSerumOrders(market.getBidOrderBook(), false);
        List<SerumOrder> asks = MarketUtil.convertOrderBookToSerumOrders(market.getAskOrderBook(), false);
        float bestBid = market.getBidOrderBook().getBestBid().getFloatPrice();
        float bestAsk = market.getAskOrderBook().getBestAsk().getFloatPrice();
        float midPoint = (bestBid + bestAsk) / 2;
        float aggregateBidQuantity = 0.0f, aggregateAskQuantity = 0.0f;

        for (SerumOrder bid : bids) {
            aggregateBidQuantity += bid.getQuantity();
        }

        final List<float[]> bidList = new ArrayList<>();
        for (SerumOrder bid : bids) {
            // outlier removal: bid price must be greater than 1/3 of the best bid
            if (bid.getPrice() >= bestBid / 2.0) {
                bidList.add(new float[]{bid.getPrice(), aggregateBidQuantity});
            }
            aggregateBidQuantity -= bid.getQuantity();
        }

        float[][] floatBids = bidList.toArray(new float[0][0]);

        // outlier removal: ask price must be less than 3 times the best ask
        final List<float[]> askList = new ArrayList<>();
        for (SerumOrder ask : asks) {
            aggregateAskQuantity += ask.getQuantity();
            if (ask.getPrice() <= bestAsk * 2.0) {
                askList.add(new float[]{ask.getPrice(), aggregateAskQuantity});
            }
        }

        float[][] floatAsks = askList.toArray(new float[0][0]);

        result.put(
                "chartTitle",
                tokenManager.getMarketNameByMarket(market) + " Price"
        );
        result.put(
                "bids",
                floatBids
        );
        result.put(
                "asks",
                floatAsks
        );
        result.put(
                "midpoint",
                midPoint
        );

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