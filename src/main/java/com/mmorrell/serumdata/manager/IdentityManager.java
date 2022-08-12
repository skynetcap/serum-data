package com.mmorrell.serumdata.manager;

import com.mmorrell.serum.model.OpenOrdersAccount;
import com.mmorrell.serumdata.client.SerumDbClient;
import com.mmorrell.serumdata.model.SerumOrder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class IdentityManager {

    private final SerumDbClient serumDbClient;
    // <ooa, owner>
    private final Map<PublicKey, PublicKey> ownerReverseLookupCache = new HashMap<>();
    private final Map<PublicKey, String> knownEntities = new HashMap<>();
    private final Map<PublicKey, String> knownEntitiesIcons = new HashMap<>();

    {
        // Alameda Research
        addKnownEntity(
                "CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq",
                "Alameda",
                "alameda"
        );
        addKnownEntity(
                "HtJAWMsSRXbyBvXm1F4PDnGFzhgfBAPciyHWMZgugejX",
                "Alameda",
                "alameda"
        );
        addKnownEntity(
                "5Xm6nU1Bi6UewCrhJQFk1CAV97ZJaRiFw4tFNhUbXy3u",
                "Alameda",
                "alameda"
        );

        // Mango Markets
        addKnownEntity(
                "9BVcYqEQxyccuwznvxXqDkSJFavvTyheiTYk231T1A8S",
                "Mango",
                "mango"
        );

        // Atrix Finance
        addKnownEntity(
                "3uTzTX5GBSfbW7eM9R9k95H7Txe32Qw3Z25MtyD2dzwC",
                "Atrix",
                "atrix"
        );

        // Raydium
        addKnownEntity(
                "5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1",
                "Raydium",
                "raydium"
        );
        addKnownEntity(
                "3uaZBfHPfmpAHW7dsimC1SnyR61X4bJqQZKWmRSCXJxv",
                "Raydium",
                "raydium"
        );

        // Jump Trading / Jump Crypto
        addKnownEntity(
                "5xoBq7f7CDgZwqHrDBdRWM84ExRetg4gZq93dyJtoSwp",
                "Jump",
                "jump"
        );
        addKnownEntity(
                "5yv6Vh8FNx93TXeSS94xy8VLZMbTqx4vXp7Zg5bDLZtE",
                "Jump",
                "jump"
        );

        // Wintermute Trading
        addKnownEntity(
                "CwyQtt6xGptR7PaxrrksgqBSRCZ3Zb2GjUYjKD9jH3tf",
                "Wintermute",
                "wintermute"
        );
    }

    public IdentityManager(final SerumDbClient serumDbClient) {
        this.serumDbClient = serumDbClient;
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

    public void reverseOwnerLookup(List<SerumOrder> serumOrders) {
        List<SerumOrder> unknownOwnerOrders = new ArrayList<>();
        ownerReverseLookup(serumOrders, unknownOwnerOrders);

        List<PublicKey> unknownAccounts = unknownOwnerOrders.stream()
                .map(SerumOrder::getOwner)
                .distinct()
                .toList();

        lookupAndAddOwnersToCache(unknownAccounts);
    }

    public void ownerReverseLookup(List<SerumOrder> orders, List<SerumOrder> unknownOwnerOrders) {
        for (SerumOrder order : orders) {
            // do we have the true owner?
            if (ownerReverseLookupCache.containsKey(order.getOwner())) {
                PublicKey ooa = order.getOwner();
                PublicKey owner = ownerReverseLookupCache.get(ooa);
                order.setOwner(owner);

                if (knownEntities.containsKey(owner)) {
                    String entityName = knownEntities.get(owner);
                    order.addMetadata("name", entityName);
                    order.addMetadata("icon", knownEntitiesIcons.get(owner));

                    if (entityName.equalsIgnoreCase("Mango")) {
                        order.addMetadata("mangoKey", ooa.toBase58());
                    }
                }
            } else {
                // add to list for later processing
                unknownOwnerOrders.add(order);
            }
        }
    }

    /**
     * Retrieves owners and updates cache. Returns the new cache, if needed.
     *
     * @param openOrdersAccounts ooa pubkeys to lookup
     * @return map <ooa, optional<owner>>
     */
    public Map<PublicKey, Optional<PublicKey>> lookupAndAddOwnersToCache(@NotNull List<PublicKey> openOrdersAccounts) {
        // <ooa, owner>
        Map<PublicKey, Optional<PublicKey>> resultMap = new HashMap<>();

        // Build map of keys to search
        for (PublicKey ooa : openOrdersAccounts) {
            boolean hasOwner = ownerReverseLookupCache.containsKey(ooa);
            if (hasOwner) {
                resultMap.put(ooa, Optional.of(ownerReverseLookupCache.get(ooa)));
            } else {
                resultMap.put(ooa, Optional.empty());
            }
        }

        // Craft list to pass to getMultipleAccounts
        List<PublicKey> keysToSearch = resultMap.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();

        Map<PublicKey, ByteBuffer> accountDataList = serumDbClient.getMultipleAccounts(keysToSearch);
        for (PublicKey ooaKey : keysToSearch) {
            ByteBuffer ooaAccountData = accountDataList.get(ooaKey);
            if (ooaAccountData != null && ooaAccountData.hasArray() && ooaAccountData.array().length > 0) {
                final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                        ooaAccountData.array()
                );

                resultMap.put(ooaKey, Optional.of(ooa.getOwner()));
                ownerReverseLookupCache.put(ooaKey, ooa.getOwner());
            } else {
                // OOA was closed or otherwise deleted (rare).
                resultMap.put(ooaKey, Optional.of(ooaKey));
                ownerReverseLookupCache.put(ooaKey, ooaKey);
            }
        }

        return resultMap;
    }
}
