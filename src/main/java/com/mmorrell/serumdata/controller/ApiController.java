package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.*;
import com.mmorrell.serumdata.manager.IdentityManager;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.MarketDepth;
import com.mmorrell.serumdata.model.SerumOrder;
import com.mmorrell.serumdata.model.TradeHistoryEvent;
import com.mmorrell.serumdata.util.MarketUtil;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
public class ApiController {

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

    /**
     * Returns Serum market metadata such as token mints, etc
     * @param marketId serum market id
     * @return map with market metadata
     */
    @GetMapping(value = "/api/serum/market/{marketId}")
    public Map<String, Object> getMarket(@PathVariable String marketId) {
        final Optional<Market> market = marketManager.getMarketById(marketId);

        if (market.isEmpty()) {
            return Collections.emptyMap();
        }

        return convertMarketToMap(market.get());
    }

    @GetMapping(value = "/api/serum/market/{marketId}/bids")
    public List<SerumOrder> getMarketBids(@PathVariable String marketId, HttpServletResponse response) {
        response.addHeader(CACHE_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);
        response.addHeader(CACHE_CONTROL_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);

        final PublicKey marketPublicKey = PublicKey.valueOf(marketId);
        final Optional<OrderBook> orderBook = marketManager.getCachedBidOrderBook(marketPublicKey);

        if (orderBook.isPresent()) {
            List<SerumOrder> serumOrders = MarketUtil.convertOrderBookToSerumOrders(orderBook.get(), true);
            identityManager.reverseOwnerLookup(serumOrders);
            return serumOrders;
        } else {
            return Collections.emptyList();
        }
    }

    @GetMapping(value = "/api/serum/market/{marketId}/asks")
    public List<SerumOrder> getMarketAsks(@PathVariable String marketId, HttpServletResponse response) {
        response.addHeader(CACHE_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);
        response.addHeader(CACHE_CONTROL_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);

        final PublicKey marketPublicKey = PublicKey.valueOf(marketId);
        final Optional<OrderBook> orderBook = marketManager.getCachedAskOrderBook(marketPublicKey);

        if (orderBook.isPresent()) {
            List<SerumOrder> serumOrders = MarketUtil.convertOrderBookToSerumOrders(orderBook.get(), false);
            identityManager.reverseOwnerLookup(serumOrders);
            return serumOrders;
        } else {
            return Collections.emptyList();
        }
    }

    @GetMapping(value = "/api/serum/market/{marketId}/tradeHistory")
    public List<TradeHistoryEvent> getMarketTradeHistory(@PathVariable String marketId, HttpServletResponse response) {
        response.addHeader(CACHE_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);
        response.addHeader(CACHE_CONTROL_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);

        final List<TradeHistoryEvent> result = new ArrayList<>();
        final PublicKey marketKey = new PublicKey(marketId);

        final Optional<EventQueue> eventQueue = marketManager.getCachedEventQueue(marketKey);
        if (eventQueue.isEmpty()) {
            return Collections.emptyList();
        }

        List<TradeEvent> tradeEvents = eventQueue.get().getEvents();
        Map<PublicKey, Optional<PublicKey>> owners = identityManager.lookupAndAddOwnersToCache(
                tradeEvents.stream()
                        .map(TradeEvent::getOpenOrders)
                        .toList()
        );

        for (int i = 0; i < tradeEvents.size(); i++) {
            TradeEvent event = tradeEvents.get(i);

            if (event.getEventQueueFlags().isMaker()) {
                // Skip event if it's a maker side fill
                // We are attaching that metadata to the taker event instead.
                continue;
            }

            // Get owner of taker OOA
            Optional<PublicKey> owner = owners.getOrDefault(event.getOpenOrders(), Optional.empty());

            // Fall back if not found yet
            PublicKey taker = owner.orElseGet(event::getOpenOrders);

            // Calculate the corresponding maker for trade.
            // The maker row is always adjacent to the taker. E.g. index 0 is taker, index 1 is maker.
            // Volume and price can also be correlated but it isn't as deterministic.
            int makerIndex = i + 1;
            final Optional<PublicKey> makerPubkey = makerIndex < tradeEvents.size() ?
                    Optional.ofNullable(tradeEvents.get(makerIndex).getOpenOrders()) :
                    Optional.empty();

            final TradeHistoryEvent tradeHistoryEvent = TradeHistoryEvent.builder()
                    .index(i)
                    .price(event.getFloatPrice())
                    .quantity(event.getFloatQuantity())
                    .owner(taker)
                    .build();

            // Known entity e.g. Wintermute
            boolean isKnownTaker = identityManager.hasReverseLookup(taker);
            if (isKnownTaker) {
                tradeHistoryEvent.setTakerEntityName(identityManager.getEntityNameByOwner(taker));
                tradeHistoryEvent.setTakerEntityIcon(identityManager.getEntityIconByOwner(taker));
            }

            // Maker metadata
            if (makerPubkey.isPresent()) {
                Optional<PublicKey> makerOwner = owners.get(makerPubkey.get());
                if (makerOwner.isPresent()) {
                    tradeHistoryEvent.setMakerOwner(makerOwner.get());
                    if (identityManager.hasReverseLookup(makerOwner.get())) {
                        tradeHistoryEvent.setMakerEntityName(identityManager.getEntityNameByOwner(makerOwner.get()));
                        tradeHistoryEvent.setMakerEntityIcon(identityManager.getEntityIconByOwner(makerOwner.get()));
                    }
                } else {
                    tradeHistoryEvent.setMakerOwner(makerPubkey.get());
                }
            }

            tradeHistoryEvent.setFill(event.getEventQueueFlags().isFill());
            tradeHistoryEvent.setOut(event.getEventQueueFlags().isOut());
            tradeHistoryEvent.setBid(event.getEventQueueFlags().isBid());
            tradeHistoryEvent.setMaker(event.getEventQueueFlags().isMaker());

            result.add(tradeHistoryEvent);
        }

        return result;
    }

    // Only works for cached markets.
    @GetMapping(value = "/api/serum/market/{marketId}/depth")
    public MarketDepth getMarketDepth(@PathVariable String marketId, HttpServletResponse response) {
        response.addHeader(CACHE_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);
        response.addHeader(CACHE_CONTROL_HEADER_NAME, CACHE_HEADER_VALUE_FORMATTED);

        final PublicKey marketPubkey = new PublicKey(marketId);
        CompletableFuture<Optional<OrderBook>> bidFuture = CompletableFuture.supplyAsync(() -> marketManager.getCachedBidOrderBook(marketPubkey));
        CompletableFuture<Optional<OrderBook>> askFuture = CompletableFuture.supplyAsync(() -> marketManager.getCachedAskOrderBook(marketPubkey));

        final CompletableFuture<Void> combinedFutures = CompletableFuture.allOf(bidFuture, askFuture);
        Optional<OrderBook> bidOrderBook, askOrderBook;

        try {
            combinedFutures.get();
            bidOrderBook = bidFuture.get();
            askOrderBook = askFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        if (bidOrderBook.isEmpty() || askOrderBook.isEmpty()) {
            return MarketDepth.builder().build();
        }

        // isBid = false on the bids since chart JS library expects ascending order
        final List<SerumOrder> bids = MarketUtil.convertOrderBookToSerumOrders(bidOrderBook.get(), false);
        final List<SerumOrder> asks = MarketUtil.convertOrderBookToSerumOrders(askOrderBook.get(), false);

        float bestBid = bids.size() > 0 ? bidOrderBook.get().getBestBid().getFloatPrice() : 0.0f;
        float bestAsk = asks.size() > 0 ? askOrderBook.get().getBestAsk().getFloatPrice() : 0.0f;
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

        return MarketDepth.builder()
                .asks(floatAsks)
                .bids(floatBids)
                .midpoint(midPoint)
                .bidContextSlot(marketManager.getBidContext(marketPubkey))
                .askContextSlot(marketManager.getAskContext(marketPubkey))
                .build();
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