function formatToken(token) {
    // only load top 100 icons
    if (!token.id || token.element.dataset.rank > 100) {
        return token.text;
    }
    return $(
        '<span><img src="' + token.element.dataset.icon + '" class="img-icon" /> ' + token.text + '</span>'
    );
}


function addData(label, data, update) {
    myChart.data.labels.push(label);
    myChart.data.datasets.forEach((dataset) => {
        dataset.data.push(data);
    });
    if (update) {
        myChart.update();
    }
    //myChart.update();
}

function loadMarkets(tokenId) {
    let apiUrl = "/api/serum/token/" + tokenId;
    $.get(apiUrl, function (data) {
        $("#marketList").empty();
        $.each(data, function (k, v) {
            $("#marketList").append(
                "<li>" +
                "<img src=" + v.baseLogo + " class=\"img-icon\" style=\"float: left\"/> " +
                "<a href=\"#\" onClick=\"setMarket('" + v.id + "');\">" +
                v.baseSymbol + " / " + v.quoteSymbol + " / " + v.id.substring(0, 3) + ".." + v.id.substring(v.id.length - 3) +
                " (" + (v.percentage * 100).toFixed(0) + "%)" +
                "</a></li>"
            );
        })
    });
}

function setMarket(marketId) {
    activeMarketId = marketId; // starts order book loop

    loadMarketDetail();

    // update trade history
    loadTradeHistory(marketId);
}

function loadTradeHistory(marketId) {
    let apiUrl = "/api/serum/market/" + marketId + "/tradeHistory";
    $.get(apiUrl, function (data) {
        $('#tradeHistoryTable tbody').empty();

        // reset chart
        myChart.data = {
            labels: [],
            datasets: [{
                label: 'Price',
                data: [],
                fill: false,
                borderColor: 'rgb(41,98,255)',
                tension: 0.1
            }]
        };
        myChart.update();

        $.each(data, function (k, v) {
            if (!v.maker) {
                $("#tradeHistoryTable tbody").append(
                    "<tr class='" + (v.bid ? "table-success" : "table-danger") + "'>" +
                    "<td style=\"text-align: right\">" + marketCurrencySymbol + v.price + "</td>" +
                    "<td style=\"text-align: right\">" +
                    v.quantity +
                    "</td>" +
                    "<td style=\"text-align: left\">" +
                    (v.jupiterTx ? "<a href=\"https://solscan.io/tx/" + v.jupiterTx + "\" target=_blank><img src=\"static/entities/jup.png\" width=16 height=16 style=\"margin-right: 6px;\"> Jupiter (" + v.owner.publicKey.substring(0, 3) + "..)" : "") +
                    ((!v.jupiterTx && v.entityName) ? "<img src=\"static/entities/" + v.entityIcon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" : "") +
                    (!v.jupiterTx ?
                        (!v.entityName ? "<a href=\"https://solscan.io/account/" + v.owner.publicKey + "\" target=_blank>" + v.owner.publicKey.substring(0, 3) + ".." + v.owner.publicKey.substring(v.owner.publicKey.toString().length - 3) + "</a>" : v.entityName)
                        : "") +
                    "</td>" +
                    "</tr>"
                );
            }
            addData(k, v.price, false);
        })

        myChart.data.datasets.forEach((dataset) => {
            dataset.data.reverse();
        });
        myChart.update();

        lastLoadedChartId = marketId;
    });
}

function loadMarketDetail() {
    let apiUrl = "/api/serum/market/" + activeMarketId;
    $.get({url: apiUrl, cache: true})
        .done(function (data) {
            $("#orderBookHeader").html("Order Book: " +
                "<img class=\"baseLogo img-icon\"> " +
                "<span id=\"baseName\"></span> / " +
                "<img class=\"quoteLogo img-icon\"> " +
                "<span id=\"quoteName\"></span> " +
                "<span id=\"ownerName\"></span> " +
                "<span class=\"livePrice\"></span>"
            );
            $("#baseName").text(data.baseSymbol);
            $("#priceChartTitle").html("<img class=\"baseLogo img-icon\" style=\"float: left; margin-right: 5px !important;\">" + " <span class=\"livePrice\"></span>" + data.baseSymbol + "/" + data.quoteSymbol + " Price - " + activeMarketId);
            $("#tradeHistoryTitle").text(data.baseSymbol + " Trade History")
            $("#quoteName").text(data.quoteSymbol);
            $("#ownerName").text("(" + data.id.substring(0, 3) + ".." + data.id.substring(data.id.toString().length - 3) + ")");
            $(".baseLogo").attr("src", data.baseLogo);
            $(".quoteLogo").attr("src", data.quoteLogo);
            lastLoadedMarketId = data.id;

            if (data.quoteSymbol === 'USDC' || data.quoteSymbol === 'USDT') {
                marketCurrencySymbol = '$';
            } else {
                marketCurrencySymbol = '';
            }
        });
}

function updateSales() {
    if (activeMarketId) {
        let apiUrl = "/api/serum/market/" + activeMarketId + "/tradeHistory";
        $.get({url: apiUrl, cache: true})
            .done(function (data) {
                $('#tradeHistoryTable tbody').empty();
                $.each(data, function (k, v) {
                    if (!v.maker) {
                        $("#tradeHistoryTable tbody").append(
                            "<tr class='" + (v.bid ? "table-success" : "table-danger") + "'>" +
                            "<td style=\"text-align: right\">" + marketCurrencySymbol + v.price + "</td>" +
                            "<td style=\"text-align: right\">" +
                            v.quantity +
                            "</td>" +
                            "<td style=\"text-align: left\">" +
                            (v.jupiterTx ? "<a href=\"https://solscan.io/tx/" + v.jupiterTx + "\" target=_blank><img src=\"static/entities/jup.png\" width=16 height=16 style=\"margin-right: 6px;\"> Jupiter (" + v.owner.publicKey.substring(0, 3) + "..)" : "") +
                            ((!v.jupiterTx && v.entityName) ? "<img src=\"static/entities/" + v.entityIcon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" : "") +
                            (!v.jupiterTx ?
                                (!v.entityName ? "<a href=\"https://solscan.io/account/" + v.owner.publicKey + "\" target=_blank>" + v.owner.publicKey.substring(0, 3) + ".." + v.owner.publicKey.substring(v.owner.publicKey.toString().length - 3) + "</a>" : v.entityName)
                                : "") +
                            "</td>" +
                            "</tr>"
                        );
                    }
                })
            });
    }
}

function updateDepthChart() {
    if (activeMarketId) {
        let apiUrl = "/api/serum/market/" + activeMarketId + "/depth";
        // bids + asks
        $.get({url: apiUrl, cache: true})
            .done(function (newData) {
                // loop total bids, total each level, total all that
                totalBids = newData.bids.reduce(
                    (previousValue, currentValue) => {
                        return previousValue + (currentValue[0] * currentValue[2]);
                    },
                    0
                );

                var totalBidsString = formatter.format(totalBids);
                if (marketCurrencySymbol !== '$') {
                    // trim $ if not a usdc pair, since formatter assumes money
                    totalBidsString = totalBidsString.substring(1);
                }

                if (newData.asks.length === 0) {
                    totalAsks = 0;
                } else {
                    totalAsks = newData.asks[newData.asks.length - 1][1].toFixed(2);
                }

                depthChart.series[0].setData(newData.bids);
                depthChart.series[1].setData(newData.asks);
                depthChart.xAxis[0].options.plotLines[0].value = newData.midpoint;
                depthChart.xAxis[0].setExtremes(newData.midpoint - (newData.midpoint / 3), newData.midpoint + (newData.midpoint / 3));
                depthChart.xAxis[0].update();

                // text for agg totals
                bidTotal ? bidTotal.destroy() : null;
                bidTotal = depthChart.renderer.text(totalBidsString + " " + $("#quoteName").text(), 50, 133)
                    .attr({
                        zIndex: 5
                    })
                    .css({
                        fontSize: '12px',
                        color: '#00ff08'
                    })
                    .add();

                var totalAsksString = formatter.format(totalAsks).substring(1);

                askTotal ? askTotal.destroy() : null;
                var xAskTotal = $("#container").width() * 0.75;
                askTotal = depthChart.renderer.text(totalAsksString + " " + $("#baseName").text(), xAskTotal, 133)
                    .attr({
                        zIndex: 5
                    })
                    .css({
                        fontSize: '12px',
                        color: '#ff0000'
                    })
                    .add();

                depthChart.redraw();
                depthChart.hideLoading();

                // update ticker spans
                $(".livePrice").text(marketCurrencySymbol + newData.midpoint.toFixed(2) + " ");

                // update price chart with a midpoint tick, if it has changed.
                if (parseFloat(myChart.data.datasets[0].data[myChart.data.labels.length - 1]).toFixed(8) !== parseFloat(newData.midpoint).toFixed(8)) {
                    // only update it if the midpoint changes

                    if (newData.marketId !== lastLoadedChartId) {
                        return;
                    }

                    addData(parseInt(myChart.data.labels[myChart.data.labels.length - 1]) + 1, newData.midpoint, true);

                    // if over 1000 data points, start popping from the front
                    if (myChart.data.labels.length >= 100) {
                        myChart.data.datasets[0].data.shift();
                        myChart.data.labels.shift();
                        myChart.update();
                    }

                }

                // paint midpoint once if no other data exists
                if (isNaN(myChart.data.labels[0])) {
                    myChart.data.labels.pop();
                    myChart.data.datasets[0].data.pop();
                    addData(0, newData.midpoint, true);
                    addData(1, newData.midpoint, true); // 2 entries to draw a straight line
                }

                $(document).attr("title",
                    ((newData.chartTitle.includes("USDC Price") || newData.chartTitle.includes("USDT Price")) ? '$' : '') + newData.midpoint.toFixed(2) + ' ' + newData.chartTitle.replace("Price", "").replace(/\s/g, '')
                );
            });
    }
}