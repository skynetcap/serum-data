package com.mmorrell.serumdata;

import com.mmorrell.serum.model.Market;
import com.mmorrell.serum.model.SerumUtils;
import com.mmorrell.serumdata.util.RpcUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.ProgramAccount;

import java.util.Collections;
import java.util.List;

@Slf4j
public class GetProgramAccountsTest {

    private final RpcClient client = new RpcClient(RpcUtil.getPublicEndpoint());

    @Test
    public void getProgramAccountsTest() throws RpcException {
        List<ProgramAccount> programAccounts = client.getApi().getProgramAccounts(
                SerumUtils.SERUM_PROGRAM_ID_V3,
                Collections.emptyList(),
                SerumUtils.MARKET_ACCOUNT_SIZE
        );

        for (int i = 0; i < programAccounts.size(); i++) {
            ProgramAccount programAccount = programAccounts.get(i);
            Market market = Market.readMarket(programAccount.getAccount().getDecodedData());

            log.info(String.format("%d: %s", i, market.getOwnAddress()));
        }
    }


}
