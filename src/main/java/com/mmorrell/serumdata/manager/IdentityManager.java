package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.OpenOrdersAccount;
import com.google.common.collect.Lists;
import com.mmorrell.serumdata.model.SerumOrder;
import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class IdentityManager {

    private final RpcClient client = new RpcClient(RpcUtil.getPublicEndpoint());
    private final Map<String, String> ownerReverseLookupCache = new HashMap<>();
    private final Map<String, String> knownEntities = new HashMap<>();
    private final Map<String, String> knownEntitiesIcons = new HashMap<>();

    {
        knownEntities.put("CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq", "Alameda Research");
        knownEntities.put("9BVcYqEQxyccuwznvxXqDkSJFavvTyheiTYk231T1A8S", "Mango Markets");
        knownEntities.put("3uTzTX5GBSfbW7eM9R9k95H7Txe32Qw3Z25MtyD2dzwC", "Atrix Finance");
        knownEntities.put("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1", "Raydium");
        knownEntities.put("5xoBq7f7CDgZwqHrDBdRWM84ExRetg4gZq93dyJtoSwp", "Jump Trading");

        // refactor this better
        knownEntitiesIcons.put("CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq", "alameda");
        knownEntitiesIcons.put("9BVcYqEQxyccuwznvxXqDkSJFavvTyheiTYk231T1A8S", "mango");
        knownEntitiesIcons.put("3uTzTX5GBSfbW7eM9R9k95H7Txe32Qw3Z25MtyD2dzwC", "atrix");
        knownEntitiesIcons.put("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1", "raydium");
        knownEntitiesIcons.put("5xoBq7f7CDgZwqHrDBdRWM84ExRetg4gZq93dyJtoSwp", "jump");
    }

    public boolean hasReverseLookup(PublicKey publicKey) {
        return knownEntities.containsKey(publicKey.toBase58());
    }

    public String getEntityNameByOwner(PublicKey owner) {
        return knownEntities.get(owner.toBase58());
    }

    public String getEntityIconByOwner(PublicKey owner) {
        return knownEntitiesIcons.getOrDefault(owner.toBase58(), "");
    }

    public PublicKey lookupAndAddOwnerToCache(PublicKey openOrdersAccount) {
        try {
            // first check if we need to look it up...
            if (ownerReverseLookupCache.get(openOrdersAccount.toBase58()) != null) {
                return PublicKey.valueOf(ownerReverseLookupCache.get(openOrdersAccount.toBase58()));
            }

            final AccountInfo accountInfo = client.getApi().getAccountInfo(openOrdersAccount);
            if (accountInfo.getValue() == null) {
                ownerReverseLookupCache.put(openOrdersAccount.toBase58(), openOrdersAccount.toBase58());
                return openOrdersAccount;
            }

            final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                    Base64.getDecoder().decode(
                            accountInfo.getValue().getData().get(0).getBytes()
                    )
            );

            ownerReverseLookupCache.put(openOrdersAccount.toBase58(), ooa.getOwner().toBase58());
            return ooa.getOwner();
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }
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

            for (int i = 0; i < accountsToSearchList.size(); i++) {
                accountData.addAll(client.getApi().getMultipleAccounts(accountsToSearchList.get(i)));
            }

            for (int i = 0; i < accountsToSearch.size(); i++) {
                final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                        Base64.getDecoder().decode(
                                accountData.get(i).getData().get(0).getBytes()
                        )
                );
                ownerReverseLookupCache.put(accountsToSearch.get(i).toBase58(), ooa.getOwner().toBase58());
                // System.out.printf("OOA:%s,Owner:%s%n", accountsToSearch.get(i).toBase58(), ooa.getOwner().toBase58());
            }
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }
    }

    public void ownerReverseLookup(List<SerumOrder> orders, List<SerumOrder> unknownOwnerOrders) {
        for (SerumOrder order : orders) {
            // do we have the true owner?
            if (ownerReverseLookupCache.containsKey(order.getOwner())) {
                String owner = ownerReverseLookupCache.get(order.getOwner());
                // use human-readable name if we have it
                order.setOwner(knownEntities.getOrDefault(owner, owner));

                // icon and other metadata
                if (knownEntitiesIcons.containsKey(owner)) {
                    order.addMetadata("icon", knownEntitiesIcons.get(owner));
                }
            } else {
                // add to list for later processing
                unknownOwnerOrders.add(order);
            }
        }
    }
}
