$.fn.dataTable.ext.errMode = 'none';
var chartTitle, baseSymbol, quoteSymbol, baseLogo, quoteLogo, bidContextSlot, askContextSlot;

function formatToken(token) {
    if (!token.id) {
        return token.text;
    }
    if (token.element.dataset.icon != null) {
        return $(
            '<span><img loading="lazy" src="' + token.element.dataset.icon + '" class="img-icon" /> ' + token.text + '</span>'
        );
    } else {
        return $(
            '<span>' + token.text + '</span>'
        );
    }
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
                "<img width='20' height='20' src=\"" + (v.baseLogo == null ? "" : v.baseLogo) + "\" class=\"img-icon\"" +
                    " style=\"float:" +
                    " left; border-radius: 5px;\"/><img" +
                " height='20' width='20' src=\"" + (v.quoteLogo == null ? "" : v.quoteLogo) + "\" class=\"img-icon\"" +
                " style=\"float: left;" +
                " border-radius: 5px;\"/> " +
                "<a href=\"#\" style='padding-left: 6px;' onClick=\"setMarket('" + v.id + "');\">" +
                v.baseSymbol + " - " + v.quoteSymbol + " - " + "(" + (v.percentage * 100).toFixed(0) + "%)" +
                " (" + v.id.substring(0, 3) + "..." + v.id.substring(v.id.length - 3) +
                ")</a></li>"
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
                addData(k, v.price, false);
            }
        });

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
                "<span class=\"livePrice\"></span>" +
                "<span class=\"marketContext\" style=\"float: right;\"></span>"
            );
            baseSymbol = data.baseSymbol;
            quoteSymbol = data.quoteSymbol;
            chartTitle = baseSymbol + "/" + data.quoteSymbol;
            lastLoadedMarketId = data.id;
            baseLogo = data.baseLogo;
            quoteLogo = data.quoteLogo;

            $("#baseName").text(baseSymbol);
            $("#priceChartTitle").html("<img class=\"baseLogo img-icon\" style=\"float: left; margin-right: 5px !important;\">" + " <span class=\"livePrice\"></span>" + chartTitle + " Price - " + activeMarketId);
            $("#tradeHistoryTitle").text(baseSymbol + " Trade History")
            $("#quoteName").text(quoteSymbol);
            $("#ownerName").text("(" + lastLoadedMarketId.substring(0, 3) + ".." + lastLoadedMarketId.substring(lastLoadedMarketId.toString().length - 3) + ")");
            $(".baseLogo").attr("src", baseLogo);
            $(".quoteLogo").attr("src", quoteLogo);

            if (quoteSymbol === 'USDC' || quoteSymbol === 'USDT') {
                marketCurrencySymbol = '$';
            } else {
                marketCurrencySymbol = '';
            }
        });
}

function updateDepthChart() {
    if (activeMarketId) {
        let apiUrl = "/api/serum/market/" + activeMarketId + "/depth";
        // bids + asks
        $.get({url: apiUrl, cache: true})
            .done(function (newData) {
                bidContextSlot = newData.bidContextSlot;
                askContextSlot = newData.askContextSlot;

                $(".marketContext").text("Slot: " + bidContextSlot)

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
                $(".livePrice").text(marketCurrencySymbol + newData.midpoint.toFixed(3) + " ");

                // update price chart with a midpoint tick, if it has changed.
                if (parseFloat(myChart.data.datasets[0].data[myChart.data.labels.length - 1]).toFixed(8) !== parseFloat(newData.midpoint).toFixed(8)) {
                    // only update it if the midpoint changes

                    if (activeMarketId !== lastLoadedChartId) {
                        return;
                    }

                    if (totalBids === 0) {
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
                    marketCurrencySymbol + newData.midpoint.toFixed(3) + ' ' + chartTitle.replace(/\s/g, '') + ' - Openserum'
                );
            });
    }
}