package com.mmorrell.serumdata.util;

import ch.openserum.serum.model.OrderBook;
import com.mmorrell.serumdata.model.SerumOrder;
import org.p2p.solanaj.core.PublicKey;

import java.util.List;
import java.util.stream.Collectors;

public class MarketUtil {

    // For initial market caching
    public static final PublicKey USDC_MINT =
            PublicKey.valueOf("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    public static final PublicKey USDT_MINT =
            PublicKey.valueOf("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB");

    // Jupiter
    public static final PublicKey JUPITER_PROGRAM_ID =
            PublicKey.valueOf("JUP3c2Uh3WA4Ng34tw6kPd2G4C5BB21Xo36Je1s32Ph");
    public static final PublicKey JUPITER_USDC_WALLET =
            PublicKey.valueOf("H5sizxhR6ssXrX2YNDoYaUv93PU34VzyRaVaUHuo5eFk");
    public static final PublicKey JUPITER_USDT_WALLET =
            PublicKey.valueOf("FVKG6bkrQ4rksme6GT1FN7PgvZf9cNmupyWfN5kJj8Fx");
    public static final PublicKey JUPITER_WSOL_WALLET =
            PublicKey.valueOf("61CjGbapEVoyCC51x5tPZGZHCYsgtPSSssCatHEEUWeG");


    public static List<SerumOrder> convertOrderBookToSerumOrders(OrderBook orderBook, boolean isBid) {
        return orderBook.getOrders().stream()
                .map(order -> {
                    SerumOrder serumOrder = new SerumOrder();
                    serumOrder.setPrice(order.getFloatPrice());
                    serumOrder.setQuantity(order.getFloatQuantity());
                    serumOrder.setOwner(order.getOwner());
                    return serumOrder;
                })
                .sorted((o1, o2) -> {
                    if (isBid) {
                        return Float.compare(o2.getPrice(), o1.getPrice());
                    }
                    return Float.compare(o1.getPrice(), o2.getPrice());
                })
                .collect(Collectors.toList());
    }

}
