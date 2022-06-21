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

    public static List<SerumOrder> convertOrderBookToSerumOrders(OrderBook orderBook, boolean isBid) {
        return orderBook.getOrders().stream()
                .map(order -> {
                    SerumOrder serumOrder = new SerumOrder();
                    serumOrder.setPrice(order.getFloatPrice());
                    serumOrder.setQuantity(order.getFloatQuantity());
                    serumOrder.setOwner(order.getOwner().toBase58());
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
