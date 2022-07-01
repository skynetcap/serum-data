package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.*;
import com.mmorrell.serumdata.manager.IdentityManager;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.SerumOrder;
import com.mmorrell.serumdata.model.TradeHistoryEvent;
import com.mmorrell.serumdata.util.MarketUtil;
import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ApiController {

    private final RpcClient orderBookClient = new RpcClient(RpcUtil.getPublicEndpoint());
    private final TokenManager tokenManager;
    private final MarketManager marketManager;
    private final IdentityManager identityManager;

    // Cache headers
    private final static String CACHE_HEADER_NAME = "Cloudflare-CDN-Cache-Control";
    private final static String CACHE_CONTROL_HEADER_NAME = "Cache-Control";
    private final static String CACHE_HEADER_VALUE = "max-age=";
    private final static int CACHE_MAX_DURATION_SECONDS = 1;
    private final static String CACHE_HEADER_VALUE_FORMATTED = String.format(
            "%s%d",
            CACHE_HEADER_VALUE,
            CACHE_MAX_DURATION_SECONDS
    );

    // Called on startup, loads our caches first etc
    // Auto-injected beans created by Component annotation
    public ApiController(TokenManager tokenManager,
                         MarketManager marketManager,
                         IdentityManager identityManager) {
        this.tokenManager = tokenManager;
        this.marketManager = marketManager;
        this.identityManager = identityManager;
    }

    @GetMapping(value = "/api/serum/token/{tokenId}")
    public List<Map<String, Object>> getMarketsByBaseMint(@PathVariable String tokenId) {
        // return a list of Maps, similar to getMarket, instead of a direct list of Markets.
        List<Map<String, Object>> results = new ArrayList<>();
        PublicKey tokenKey = new PublicKey(tokenId);
        List<Market> markets = marketManager.getMarketsByMint(tokenKey);

        // get total base deposits, for percentage ranking
        long totalBaseDeposits = 0;
        for (Market market : markets) {
            totalBaseDeposits += market.getBaseDepositsTotal();
        }

        // sort by base deposits
        markets.sort(Comparator.comparingLong(Market::getBaseDepositsTotal).reversed());
        for (Market market : markets) {
            Map<String, Object> marketMap = convertMarketToMap(market);
            marketMap.put("percentage", (float) market.getBaseDepositsTotal() / (float) totalBaseDeposits);
            results.add(marketMap);
        }

        return results;
    }

    @GetMapping(value = "/api/serum/market/{marketId}")
    public Map<String, Object> getMarket(@PathVariable String marketId) {
        Map<String, Object> result = new HashMap<>();
        PublicKey marketKey = new PublicKey(marketId);
        Optional<Market> marketFromCache = marketManager.getMarketCache().stream()
                .filter(market -> market.getOwnAddress().equals(marketKey))
                .findFirst();

        if (marketFromCache.isEmpty()) {
            // make this an error, nothing was found
            return result;
        }

        final Market marketWithOrderBooks = new MarketBuilder()
                .setClient(orderBookClient)
                .setPublicKey(marketKey)
                .setRetrieveOrderBooks(true)
                .build();

        Market market = marketFromCache.get();
        return convertOrdersAndLookup(market, marketWithOrderBooks.getBidOrderBook(), marketWithOrderBooks.getAskOrderBook());
    }

    @GetMapping(value = "/api/serum/market/{marketId}/cached")
    public Map<String, Object> getMarketCached(@PathVariable String marketId, HttpServletResponse response) {
        response.addHeader(CACHE_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);
        response.addHeader(CACHE_CONTROL_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);

        MarketBuilder builder;
        Market market;
        PublicKey marketKey = new PublicKey(marketId);

        // check builder map
        boolean isBuilderCached = marketManager.isBuilderCached(marketKey);
        if (isBuilderCached) {
            builder = marketManager.getBuilderFromCache(marketKey);
            market = builder.reload();
        } else {
            builder = new MarketBuilder()
                    .setClient(orderBookClient)
                    .setPublicKey(marketKey)
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
    public List<TradeHistoryEvent> getMarketTradeHistory(@PathVariable String marketId, HttpServletResponse response) {
        response.addHeader(CACHE_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);
        response.addHeader(CACHE_CONTROL_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);

        PublicKey marketKey = new PublicKey(marketId);

        final List<TradeHistoryEvent> result = new ArrayList<>();
        final Market marketWithEventQueue = new MarketBuilder()
                .setClient(orderBookClient)
                .setPublicKey(marketKey)
                .setRetrieveEventQueue(true)
                .build();

        List<TradeEvent> tradeEvents = marketWithEventQueue.getEventQueue().getEvents();
        Map<PublicKey, Optional<PublicKey>> takers = identityManager.lookupAndAddOwnersToCache(
                tradeEvents.stream()
                        .map(TradeEvent::getOpenOrders)
                        .collect(Collectors.toList())
        );

        for (int i = 0; i < tradeEvents.size(); i++) {
            TradeEvent event = tradeEvents.get(i);
            Optional<PublicKey> owner = takers.getOrDefault(event.getOpenOrders(), Optional.empty());
            PublicKey taker = owner.orElseGet(event::getOpenOrders);

            final TradeHistoryEvent tradeHistoryEvent = new TradeHistoryEvent(
                    i,
                    event.getFloatPrice(),
                    event.getFloatQuantity(),
                    taker
            );

            // Known entity e.g. Wintermute
            boolean isKnownTaker = identityManager.hasReverseLookup(taker);
            if (isKnownTaker) {
                tradeHistoryEvent.setEntityName(identityManager.getEntityNameByOwner(taker));
                tradeHistoryEvent.setEntityIcon(identityManager.getEntityIconByOwner(taker));
            }

            tradeHistoryEvent.setFill(event.getEventQueueFlags().isFill());
            tradeHistoryEvent.setOut(event.getEventQueueFlags().isOut());
            tradeHistoryEvent.setBid(event.getEventQueueFlags().isBid());
            tradeHistoryEvent.setMaker(event.getEventQueueFlags().isMaker());

            // Jupiter TX handling, only lookup unknown entities, only top 12 in history
            int maxRowsToJupiterSearch = 12;
            if (!isKnownTaker && i < maxRowsToJupiterSearch) {
                Optional<String> jupiterTx = marketManager.getJupiterTxForMarketAndOoa(
                        marketKey,
                        event.getOpenOrders(),
                        taker,
                        event.getFloatPrice(),
                        event.getFloatQuantity()
                );

                jupiterTx.ifPresent(tradeHistoryEvent::setJupiterTx);
            }

            result.add(tradeHistoryEvent);
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
    public Map<String, Object> getMarketDepth(@PathVariable String marketId, HttpServletResponse response) {
        response.addHeader(CACHE_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);
        response.addHeader(CACHE_CONTROL_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);

        final Map<String, Object> result = new HashMap<>();
        MarketBuilder builder;
        Market market;
        PublicKey marketKey = new PublicKey(marketId);

        // check builder map
        boolean isBuilderCached = marketManager.isBuilderCached(marketKey);
        if (isBuilderCached) {
            builder = marketManager.getBuilderFromCache(marketKey);
            market = builder.reload();
        } else {
            builder = new MarketBuilder()
                    .setClient(orderBookClient)
                    .setPublicKey(marketKey)
                    .setRetrieveOrderBooks(true)
                    .setOrderBookCacheEnabled(true);
            market = builder.build();

            // add to cache, saves decimal cache and initial account poll
            marketManager.addBuilderToCache(builder);
        }

        List<SerumOrder> bids = MarketUtil.convertOrderBookToSerumOrders(market.getBidOrderBook(), false);
        List<SerumOrder> asks = MarketUtil.convertOrderBookToSerumOrders(market.getAskOrderBook(), false);
        float bestBid = bids.size() > 0 ? market.getBidOrderBook().getBestBid().getFloatPrice() : 0.0f;
        float bestAsk = asks.size() > 0 ? market.getAskOrderBook().getBestAsk().getFloatPrice() : 0.0f;
        float midPoint = (bestBid + bestAsk) / 2;
        float aggregateBidQuantity = 0.0f, aggregateAskQuantity = 0.0f;

        for (SerumOrder bid : bids) {
            aggregateBidQuantity += bid.getQuantity();
        }

        final List<float[]> bidList = new ArrayList<>();
        for (SerumOrder bid : bids) {
            bidList.add(new float[]{bid.getPrice(), aggregateBidQuantity, bid.getQuantity()});
            aggregateBidQuantity -= bid.getQuantity();
        }

        float[][] floatBids = bidList.toArray(new float[0][0]);

        final List<float[]> askList = new ArrayList<>();
        for (SerumOrder ask : asks) {
            aggregateAskQuantity += ask.getQuantity();
            askList.add(new float[]{ask.getPrice(), aggregateAskQuantity, ask.getQuantity()});
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
        result.put(
                "marketId",
                market.getOwnAddress().toBase58()
        );

        return result;
    }

    private Map<String, Object> convertMarketToMap(Market market) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", market.getOwnAddress().toBase58());
        result.put("baseName", tokenManager.getTokenNameByMint(market.getBaseMint()));
        result.put("baseMint", market.getBaseMint().toBase58());
        result.put("baseSymbol", tokenManager.getTokenSymbolByMint(market.getBaseMint()));
        result.put("baseLogo", tokenManager.getTokenLogoByMint(market.getBaseMint()));
        result.put("quoteName", tokenManager.getTokenNameByMint(market.getQuoteMint()));
        result.put("quoteMint", market.getQuoteMint().toBase58());
        result.put("quoteSymbol", tokenManager.getTokenSymbolByMint(market.getQuoteMint()));
        result.put("quoteLogo", tokenManager.getTokenLogoByMint(market.getQuoteMint()));
        return result;
    }

}