package com.mmorrell.serumdata.controller;

import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.MarketRankManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.Token;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

@Controller
public class IndexController {

    private final TokenManager tokenManager;
    private final MarketManager marketManager;
    private final MarketRankManager marketRankManager;

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
}
