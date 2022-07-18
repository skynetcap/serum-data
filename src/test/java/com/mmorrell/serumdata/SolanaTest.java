package com.mmorrell.serumdata;

import com.google.common.primitives.UnsignedLong;
import lombok.extern.log4j.Log4j;
import org.bitcoinj.core.Utils;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.utils.ByteUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class SolanaTest {

    private final RpcClient client = new RpcClient(Cluster.MAINNET);

    @Test
    public void solendTest() throws RpcException, IOException {
        // 5suXmvdbKQ98VonxGCXqViuWRu8k4zgZRxndYKsH2fJg
        PublicKey solendSrmReservePubkey = PublicKey.valueOf("5suXmvdbKQ98VonxGCXqViuWRu8k4zgZRxndYKsH2fJg");
        AccountInfo accountInfo = client.getApi().getAccountInfo(solendSrmReservePubkey);

        byte[] data = Base64.getDecoder().decode(accountInfo.getValue().getData().get(0));

        // version
        byte version = data[0];
        System.out.println("Version: " + version);

        // lastUpdate - slot
        long slot = ByteUtils.readUint64(data, 1).longValue();
        System.out.println("Slot: " + slot);

        // lastUpdate - stale
        boolean stale = data[10] == 1;
        System.out.println("Stale: " + stale);

        // mintKey = 10 + 32 = 42

        // availableAmount = 139 (8)
        // borrowedAmountWads = 147 (16)
        long availableAmonut = ByteUtils.readUint64(data, 138).longValue();
        long borrowedAmountWads = new BigInteger(reverseBytes(readBytes(data, 146, 16))).longValue();

        System.out.println("Available Amount: " + availableAmonut);
        System.out.println("Borrowed Amount: " + borrowedAmountWads);
        UnsignedLong avail = UnsignedLong.fromLongBits(availableAmonut);
        UnsignedLong borrowed = UnsignedLong.fromLongBits(borrowedAmountWads);

        float result = borrowed.bigIntegerValue().floatValue() / avail.bigIntegerValue().floatValue();
        System.out.println("Utiliziation: " + (result * 100) + "%");

        Path filePath = Path.of("solend.bin");
        Files.write(filePath, data);
    }

    public static byte[] readBytes(byte[] buf, int offset, int length) {
        byte[] b = new byte[length];
        System.arraycopy(buf, offset, b, 0, length);
        return b;
    }

    public static byte[] reverseBytes(byte[] bytes) {
        byte[] buf = new byte[bytes.length];

        for(int i = 0; i < bytes.length; ++i) {
            buf[i] = bytes[bytes.length - 1 - i];
        }

        return buf;
    }

    /**
     * Return a BigInteger equal to the unsigned value of the argument.
     */
    private static BigInteger toUnsignedBigInteger(long i) {
        if (i >= 0L) {
            return BigInteger.valueOf(i);
        } else {
            int upper = (int) (i >>> 32);
            int lower = (int) i;
            // return (upper << 32) + lower
            return BigInteger.valueOf(Integer.toUnsignedLong(upper))
                    .shiftLeft(32)
                    .add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
        }
    }
}
