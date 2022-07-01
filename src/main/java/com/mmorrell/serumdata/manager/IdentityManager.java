package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.OpenOrdersAccount;
import com.google.common.collect.Lists;
import com.mmorrell.serumdata.model.SerumOrder;
import com.mmorrell.serumdata.util.RpcUtil;
import org.jetbrains.annotations.NotNull;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class IdentityManager {

    private final RpcClient client = new RpcClient(RpcUtil.getPublicEndpoint());
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityManager.class);

    // <ooa, owner>
    private final Map<PublicKey, PublicKey> ownerReverseLookupCache = new HashMap<>();
    private final Map<PublicKey, String> knownEntities = new HashMap<>();
    private final Map<PublicKey, String> knownEntitiesIcons = new HashMap<>();

    {
        addKnownEntity(
                "CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq",
                "Alameda Research",
                "alameda"
        );
        addKnownEntity(
                "9BVcYqEQxyccuwznvxXqDkSJFavvTyheiTYk231T1A8S",
                "Mango Markets",
                "mango"
        );
        addKnownEntity(
                "3uTzTX5GBSfbW7eM9R9k95H7Txe32Qw3Z25MtyD2dzwC",
                "Atrix Finance",
                "atrix"
        );
        addKnownEntity(
                "5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1",
                "Raydium",
                "raydium"
        );
        addKnownEntity(
                "5xoBq7f7CDgZwqHrDBdRWM84ExRetg4gZq93dyJtoSwp",
                "Jump Trading",
                "jump"
        );
        addKnownEntity(
                "CwyQtt6xGptR7PaxrrksgqBSRCZ3Zb2GjUYjKD9jH3tf",
                "Wintermute",
                "wintermute"
        );
    }

    public void addKnownEntity(String publicKeyString, String name, String icon) {
        PublicKey publicKey = new PublicKey(publicKeyString);
        knownEntities.put(publicKey, name);
        knownEntitiesIcons.put(publicKey, icon);
    }

    public boolean hasReverseLookup(PublicKey publicKey) {
        return knownEntities.containsKey(publicKey);
    }

    public String getEntityNameByOwner(PublicKey owner) {
        return knownEntities.get(owner);
    }

    public String getEntityIconByOwner(PublicKey owner) {
        return knownEntitiesIcons.get(owner);
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
                .map(SerumOrder::getOwner)
                .distinct()
                .collect(Collectors.toList());

        try {
            List<AccountInfo.Value> accountData = new ArrayList<>();
            List<List<PublicKey>> accountsToSearchList = Lists.partition(accountsToSearch, 100);

            for (int i = 0; i < accountsToSearchList.size(); i++) {
                accountData.addAll(client.getApi().getMultipleAccounts(accountsToSearchList.get(i)));
            }

            for (int i = 0; i < accountsToSearch.size(); i++) {
                List<String> accountDataStrings = accountData.get(i).getData();
                if (accountDataStrings.size() < 1) {
                    break;
                }

                final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                        Base64.getDecoder().decode(
                                accountData.get(i).getData().get(0).getBytes()
                        )
                );
                ownerReverseLookupCache.put(accountsToSearch.get(i), ooa.getOwner());
            }
        } catch (RpcException e) {
            throw new RuntimeException(e);
        }
    }

    public void ownerReverseLookup(List<SerumOrder> orders, List<SerumOrder> unknownOwnerOrders) {
        for (SerumOrder order : orders) {
            // do we have the true owner?
            if (ownerReverseLookupCache.containsKey(order.getOwner())) {
                PublicKey owner = ownerReverseLookupCache.get(order.getOwner());
                order.setOwner(owner);

                if (knownEntities.containsKey(owner)) {
                    order.addMetadata("name", knownEntities.get(owner));
                    order.addMetadata("icon", knownEntitiesIcons.get(owner));
                }
            } else {
                // add to list for later processing
                unknownOwnerOrders.add(order);
            }
        }
    }

    // TODO: Refactor/dedupe similar function above.
    public Map<PublicKey, Optional<PublicKey>> lookupAndAddOwnersToCache(@NotNull List<PublicKey> openOrdersAccounts) {
        // <ooa, owner>
        Map<PublicKey, Optional<PublicKey>> resultMap = new HashMap<>();

        // Build map of keys to search
        for (PublicKey ooa : openOrdersAccounts) {
            boolean hasOwner = ownerReverseLookupCache.containsKey(ooa);
            if (hasOwner) {
                // LOGGER.info("hasOwner (not searching): " + ooa.toBase58() + ", " + ownerReverseLookupCache.get(ooa));
                resultMap.put(ooa, Optional.of(ownerReverseLookupCache.get(ooa)));
            } else {
                // LOGGER.info("Going to search: " + ooa.toBase58());
                resultMap.put(ooa, Optional.empty());
            }
        }

        // Craft list to pass to getMultipleAccounts
        List<PublicKey> keysToSearch = resultMap.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<List<PublicKey>> accountsToSearchList = Lists.partition(keysToSearch, 100);

        for (int i = 0; i < accountsToSearchList.size(); i++) {
            try {
                List<PublicKey> partitionedList = accountsToSearchList.get(i);
                // LOGGER.info("Querying: " + partitionedList.stream().map(PublicKey::toBase58).collect(Collectors.joining(",")));

                List<AccountInfo.Value> accountDataList = client.getApi().getMultipleAccounts(partitionedList);

                for (int j = 0; j < partitionedList.size(); j++) {
                    PublicKey ooaKey = partitionedList.get(j);
                    AccountInfo.Value ooaAccountData = accountDataList.get(j);

                    final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                            Base64.getDecoder().decode(
                                    ooaAccountData.getData().get(0).getBytes()
                            )
                    );

                    resultMap.put(ooaKey, Optional.of(ooa.getOwner()));
                    ownerReverseLookupCache.put(ooaKey, ooa.getOwner());
                    // LOGGER.info("Fully cached: " + ooaKey.toBase58() + ", " + ooa.getOwner().toBase58());
                }
            } catch (RpcException e) {
                throw new RuntimeException(e);
            }
        }

        return resultMap;
    }
}
