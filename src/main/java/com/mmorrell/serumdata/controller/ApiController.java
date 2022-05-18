package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import com.mmorrell.serumdata.manager.TokenManager;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.Memcmp;
import org.p2p.solanaj.rpc.types.ProgramAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ApiController {

    private final TokenManager tokenManager;

    public ApiController(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
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

    /*
    TODO:
    1. Get ALL Serum markets on-chain
    2. Get Bids/Asks (one API response) for a given market id
    3. (more)
     */

    // None of this is going to be cached or anything, 1-1 poll/RPC call each time (heavy)


    @GetMapping(value = "/api/serum/markets")
    public List<String> getSerumMarkets() throws RpcException {
        RpcClient client = new RpcClient("https://ssc-dao.genesysgo.net/");
        List<ProgramAccount> programAccounts = client.getApi().getProgramAccounts(
                SerumUtils.SERUM_PROGRAM_ID_V3,
                List.of(
                        new Memcmp(
                                85,
                                "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
                        )
                ),
                388
        );

        for(ProgramAccount programAccount : programAccounts) {
            Market market = Market.readMarket(programAccount.getAccount().getDecodedData());
            System.out.printf("Market: %s / USDC, ", tokenManager.getTokenByMint(market.getBaseMint().toBase58()));
            System.out.printf("Market ID: %s", market.getOwnAddress().toBase58());
            System.out.println();
        }



        if (programAccounts == null) {
            return new ArrayList<>();
        }

        return programAccounts.stream()
                .map(ProgramAccount::getPubkey)
                .collect(Collectors.toList());
    }

    // TODO - return a list of "Token" DTOs
    @GetMapping(value = "/api/solana/tokens")
    public List<String> getTokenRegistry() {
        tokenManager.getRegistry();
        return new ArrayList<>();
    }





}