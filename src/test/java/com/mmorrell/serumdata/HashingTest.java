package com.mmorrell.serumdata;

import com.mmorrell.serumdata.util.MarketUtil;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HashingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HashingTest.class);

    private static final PublicKey TEST_PUBKEY_1 = PublicKey.valueOf(
            "skynetDj29GH6o6bAqoixCpDuYtWqi1rm8ZNx1hB3vq"
    );
    private static final PublicKey TEST_PUBKEY_2 = MarketUtil.USDC_MINT;
    private static final PublicKey TEST_PUBKEY_3 = MarketUtil.USDT_MINT;

    /**
     * Checks that Pubkeys can properly be used as keys in Maps
     * Which involves hashCode() etc
     */
    @Test
    public void testPubkeyHashmap() {
        // Create map with PublicKey as key type
        Map<PublicKey, Integer> publicKeyIntegerMap = new HashMap<>();

        // Put some values in it.
        publicKeyIntegerMap.put(TEST_PUBKEY_1, 1337);
        publicKeyIntegerMap.put(TEST_PUBKEY_2, 420);

        // Check the hashing (containsKey)
        assertTrue(publicKeyIntegerMap.containsKey(TEST_PUBKEY_1));
        assertTrue(publicKeyIntegerMap.containsKey(TEST_PUBKEY_2));
        assertFalse(publicKeyIntegerMap.containsKey(TEST_PUBKEY_3));

        // Log the values
        int firstValue = publicKeyIntegerMap.get(TEST_PUBKEY_1);
        int secondValue = publicKeyIntegerMap.get(TEST_PUBKEY_2);

        LOGGER.info(String.format("First Key: %s, Value: %d", TEST_PUBKEY_1, firstValue));
        LOGGER.info(String.format("Second Key: %s, Value: %d", TEST_PUBKEY_2, secondValue));

        // Check the values
        assertEquals(1337, publicKeyIntegerMap.get(TEST_PUBKEY_1));
        assertEquals(420, publicKeyIntegerMap.get(TEST_PUBKEY_2));
        assertEquals(2, publicKeyIntegerMap.size());

        // Remove first value
        publicKeyIntegerMap.remove(TEST_PUBKEY_1);

        // Check that it's removed
        assertEquals(1, publicKeyIntegerMap.size());
        assertFalse(publicKeyIntegerMap.containsKey(TEST_PUBKEY_1));
        assertTrue(publicKeyIntegerMap.containsKey(TEST_PUBKEY_2));
    }

}
