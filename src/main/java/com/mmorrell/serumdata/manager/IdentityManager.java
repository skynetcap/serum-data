package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.OpenOrdersAccount;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class IdentityManager {

    private final RpcClient client = new RpcClient("https://ssc-dao.genesysgo.net/");
    private Map<String, String> openOrdersReverseCache = new HashMap<>();
    private Map<String, String> ownerToNameCache = new HashMap<>();

    {
        ownerToNameCache.put("CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq", "Alameda Research");
    }

    public PublicKey lookupAndAddOwnerToCache(PublicKey openOrdersAccount) {
        // getAccountInfo, read offset into pubkey
        try {
            // first check if we need to look it up...
            if (openOrdersReverseCache.get(openOrdersAccount.toBase58()) != null) {
                return PublicKey.valueOf(openOrdersReverseCache.get(openOrdersAccount.toBase58()));
            }

            final AccountInfo accountInfo = client.getApi().getAccountInfo(openOrdersAccount);
            // offset 45 for owner
            final OpenOrdersAccount ooa = OpenOrdersAccount.readOpenOrdersAccount(
                    Base64.getDecoder().decode(
                            accountInfo.getValue().getData().get(0).getBytes()
                    )
            );
            openOrdersReverseCache.put(openOrdersAccount.toBase58(), ooa.getOwner().toBase58());

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
        return ownerToNameCache.get(owner.toBase58());
    }

}
