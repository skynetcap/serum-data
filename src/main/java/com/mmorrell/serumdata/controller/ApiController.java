package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.SerumUtils;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.Memcmp;
import org.p2p.solanaj.rpc.types.ProgramAccount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class ApiController {
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
        // getprogram accounts against serum v3 dex program id
        // bytes at index 85 must equal usdc mint (usdc quote markets)
        // datasize equals market account
        List<ProgramAccount> programAccounts = client.getApi().getProgramAccounts(
                SerumUtils.SERUM_PROGRAM_ID_V3,
                List.of(
                        new Memcmp(85, "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                ),
                388
        );

        if (programAccounts == null) {
            return new ArrayList<>();
        }

        return programAccounts.stream()
                .map(ProgramAccount::getPubkey)
                .collect(Collectors.toList());
    }

}