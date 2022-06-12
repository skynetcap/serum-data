package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.OpenOrdersAccount;
import com.google.common.collect.Lists;
import com.mmorrell.serumdata.model.SerumOrder;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class IdentityManager {

    private final RpcClient client = new RpcClient("https://ssc-dao.genesysgo.net/");
    private final Map<String, String> ownerReverseLookupCache = new HashMap<>();
    private final Map<String, String> knownEntities = new HashMap<>();

    {
        knownEntities.put("CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq", "Alameda Research");
    }

    public PublicKey lookupAndAddOwnerToCache(PublicKey openOrdersAccount) {
        try {
            // first check if we need to look it up...
            if (ownerReverseLookupCache.get(openOrdersAccount.toBase58()) != null) {
                return PublicKey.valueOf(ownerReverseLookupCache.get(openOrdersAccount.toBase58()));
            }

            final AccountInfo accountInfo = client.getApi().getAccountInfo(openOrdersAccount);
            final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                    Base64.getDecoder().decode(
                            accountInfo.getValue().getData().get(0).getBytes()
                    )
            );
            ownerReverseLookupCache.put(openOrdersAccount.toBase58(), ooa.getOwner().toBase58());

            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return ooa.getOwner();
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }
    }

    public String getNameByOwner(PublicKey owner) {
        return knownEntities.get(owner.toBase58());
    }

    public void reverseOwnerLookup(List<SerumOrder> bids, List<SerumOrder> asks) {
        List<SerumOrder> unknownOwnerOrders = new ArrayList<>();

        // bids
        ownerReverseLookup(bids, unknownOwnerOrders);

        // asks
        ownerReverseLookup(asks, unknownOwnerOrders);

        // get unknowns and set in cache
        lookupAndAddMultipleOwnersToCache(unknownOwnerOrders);
    }

    private void lookupAndAddMultipleOwnersToCache(List<SerumOrder> unknownOwnerOrders) {
        // craft getMultipleAccounts call
        List<PublicKey> accountsToSearch = unknownOwnerOrders.stream()
                .map(serumOrder -> PublicKey.valueOf(serumOrder.getOwner()))
                .distinct()
                .collect(Collectors.toList());

        try {
            List<AccountInfo.Value> accountData = new ArrayList<>();
            List<List<PublicKey>> accountsToSearchList  = Lists.partition(accountsToSearch, 100);

            for(List<PublicKey> searchList : accountsToSearchList) {
                accountData.addAll(client.getApi().getMultipleAccounts(searchList));
            }

            for (int i = 0; i < accountsToSearch.size(); i++) {
                final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                        Base64.getDecoder().decode(
                                accountData.get(i).getData().get(0).getBytes()
                        )
                );
                ownerReverseLookupCache.put(accountsToSearch.get(i).toBase58(), ooa.getOwner().toBase58());
            }
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }
    }

    public void ownerReverseLookup(List<SerumOrder> orders, List<SerumOrder> unknownOwnerOrders) {
        for (SerumOrder order : orders) {
            // do we have the true owner?
            if (ownerReverseLookupCache.containsKey(order.getOwner())) {
                // update with the true owner
                order.setOwner(ownerReverseLookupCache.get(order.getOwner()));
            } else {
                // add to list for later processing
                unknownOwnerOrders.add(order);
            }
        }
    }
}
