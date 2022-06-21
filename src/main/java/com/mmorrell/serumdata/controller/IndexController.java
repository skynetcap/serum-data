package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.MarketBuilder;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.MarketRankManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.SerumOrder;
import com.mmorrell.serumdata.model.Token;
import com.mmorrell.serumdata.util.MarketUtil;
import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class IndexController {

    private final TokenManager tokenManager;
    private final MarketManager marketManager;
    private final MarketRankManager marketRankManager;
    private final RpcClient orderBookClient = new RpcClient(RpcUtil.getPublicEndpoint());

    public IndexController(TokenManager tokenManager,
                           MarketManager marketManager,
                           MarketRankManager marketRankManager) {
        this.tokenManager = tokenManager;
        this.marketManager = marketManager;
        this.marketRankManager = marketRankManager;
    }

    @RequestMapping("/")
    public String index(Model model) {
        Map<String, Token> tokenMap = new HashMap<>();
        tokenManager.getRegistry().forEach((tokenMint, token) -> {
            // only show tokens which have a serum market
            if (marketManager.numMarketsByToken(tokenMint) > 0) {
                tokenMap.put(tokenMint, token);
            }
        });

        model.addAttribute(
                "tokens",
                tokenMap
        );
        model.addAttribute(marketRankManager);
        return "index";
    }

    @RequestMapping("/chart")
    public String chart(Model model) {
        // initial data poll for the load, use the same code for ajax API
        final String marketId = "9wFFyRfZBsuAha4YcuxcXLKwMxJR43S7fPfQLusDBzvT";
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

        model.addAttribute(
                "chartTitle",
                tokenManager.getMarketNameByMarket(market) + " Price"
        );
        model.addAttribute(
                "initialBids",
                floatBids
        );
        model.addAttribute(
                "initialAsks",
                floatAsks
        );
        model.addAttribute(
                "midpoint",
                midPoint
        );

        return "chart";
    }
}
