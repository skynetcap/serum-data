$.fn.dataTable.ext.errMode = 'none';
$.fn.DataTable.ext.pager.numbers_length = 5;

var chartTitle = "", baseSymbol, quoteSymbol, baseLogo, quoteLogo, contextSlot, marketTable;

function formatToken(token) {
    if (!token.id) {
        return token.text;
    }
    if (token.element.dataset.icon != null) {
        return $(
            '<span><img loading="lazy" src="/api/serum/token/' + token.id + '/icon"' +
            ' class="img-icon" /> ' + token.text + '</span>'
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

    if (marketTable == null) {
        var marketList = $('#marketList');
        marketTable = marketList.DataTable({
            "ajax": {
                "url": apiUrl,
                "dataSrc": ""
            },
            autoWidth: false,
            pageLength: 3,
            info: false,
            searching: false,
            lengthChange: false,
            order: [[2, 'desc']],
            columns: [
                {
                    data: 'base',
                    render: function (data, type, row) {
                        if (row.baseLogo !== "" && row.baseSymbol !== "") {
                            return "<img style='float: left;' class='img-icon' src='/api/serum/token/" + row.baseMint + "/icon'><span" +
                                " style='padding-left: 5px;'>" + row.baseSymbol + "</span>";
                        } else {
                            return "<img style='float: left;' class='img-icon'" +
                                " src='/api/serum/token/unknown/icon'><span" +
                                " style='padding-left: 5px;'>?</span>";
                        }
                    }
                },
                {
                    data: 'quote',
                    render: function (data, type, row) {
                        if (row.quoteLogo !== "" && row.quoteSymbol !== "") {
                            return "<img style='float: left;' class='img-icon' src='/api/serum/token/" + row.quoteMint + "/icon'><span" +
                                " style='padding-left: 5px;'>" + row.quoteSymbol + "</span>";
                        } else {
                            return "?";
                        }
                    }
                },
                {
                    data: 'percentage',
                    render: function (data, type, row) {
                        return (row.percentage * 100).toFixed(0) + '%';
                    }
                },
                {
                    data: 'view',
                    render: function (data, type, row) {
                        return "<button type=\"button\" class=\"btn btn-primary btn-sm\"" +
                            " onClick=\"setMarket('" + row.id + "');\">View</button>";
                    }
                }
            ],
            columnDefs: [
                {
                    targets: [0],
                    className: 'dt-left',
                    width: '40%'
                },
                {
                    targets: [1],
                    className: 'dt-left',
                    width: '35%'
                },
                {
                    targets: [2],
                    className: 'dt-middle',
                    width: '10%'
                },
                {
                    targets: [3],
                    className: 'dt-middle',
                    width: '15%'
                }
            ]
        });
        marketList.on('click', 'tbody td', function () {
            var marketId = marketTable.row(this).data()['id'];
            if (marketId) {
                setMarket(marketId);
            }
        });
    } else {
        marketTable.ajax.url(apiUrl).load();
    }

}

function setMarket(marketId) {
    activeMarketId = marketId; // starts order book loop

    loadMarketDetail();

    // update trade history
    loadTradeHistory(marketId);
}

function loadTradeHistory(marketId) {
    let apiUrl = "/api/serum/market/" + marketId + "/tradeHistory";
    $.get({url: apiUrl, cache: false})
        .done(function (data) {
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
                "<img class=\"baseLogo img-icon\">" +
                "<span id=\"baseName\"></span> / " +
                "<img class=\"quoteLogo img-icon\">" +
                "<span id=\"quoteName\"></span> " +
                "<span id=\"ownerName\"></span> " +
                "<span class=\"livePrice\"></span>" +
                "<span class=\"marketContext\" style=\"float: right;\"></span>"
            );
            baseSymbol = data.baseSymbol;
            quoteSymbol = data.quoteSymbol;
            chartTitle = baseSymbol + " / " + data.quoteSymbol;
            lastLoadedMarketId = data.id;
            baseLogo = "/api/serum/token/" + data.baseMint + "/icon";
            quoteLogo = "/api/serum/token/" + data.quoteMint + "/icon";

            if (quoteSymbol === 'USDC' || quoteSymbol === 'USDT') {
                marketCurrencySymbol = '$';
            } else {
                marketCurrencySymbol = '';
            }

            $("#baseName").text(baseSymbol);
            $("#priceChartTitle").html("<img class=\"baseLogo img-icon\" style=\"float: left; margin-right: 5px !important;\">" + " <span class=\"livePrice\"></span>" + chartTitle + " Price - " + activeMarketId);
            $("#tradeHistoryTitle").text(baseSymbol + " Trade History")
            $("#quoteName").text(quoteSymbol);
            $("#ownerName").text("(" + lastLoadedMarketId.substring(0, 3) + ".." + lastLoadedMarketId.substring(lastLoadedMarketId.toString().length - 3) + ")");

            // Mkt details (bottom)
            $(".marketDetailsId").html(explorerLink(activeMarketId));
            $(".marketDetailsBaseMint").html(explorerLink(data.baseMint));
            $(".marketDetailsQuoteMint").html(explorerLink(data.quoteMint));
            $(".marketDetailsBids").html(explorerLink(data.bids));
            $(".marketDetailsAsks").html(explorerLink(data.asks));
            $(".marketDetailsEventQueue").html(explorerLink(data.eventQueue));
            $(".marketDetailsBaseVault").html(explorerLink(data.baseVault));
            $(".marketDetailsQuoteVault").html(explorerLink(data.quoteVault));
            $(".marketDetailsBaseDepositsTotal").text(formatter.format(data.baseDepositsFloat).substring(1) + " " + data.baseSymbol);
            $(".marketDetailsQuoteDepositsTotal").text(marketCurrencySymbol + formatter.format(data.quoteDepositsFloat).substring(1) + " " + data.quoteSymbol);
            $(".marketDetailsQuoteFeesAccrued").text(marketCurrencySymbol + formatter.format(data.quoteFeesAccruedFloat).substring(1) + " " + data.quoteSymbol);
            $(".marketDetailsBaseLotSize").text(data.baseLotSize);
            $(".marketDetailsQuoteLotSize").text(data.quoteLotSize);
            $(".marketDetailsBaseDecimals").text(data.baseDecimals);
            $(".marketDetailsQuoteDecimals").text(data.quoteDecimals);
            $(".marketDetailsReferrerRebatesAccrued").text(marketCurrencySymbol + formatter.format(data.referrerRebatesAccruedFloat).substring(1) + " " + data.quoteSymbol);
            $(".marketDetailsQuoteDustThreshold").text(data.quoteDustThreshold);
            $(".marketDetailsFeeRateBps").text(data.feeRateBps);
            $(".marketDetailsBaseTokenName").html("<img class=\"baseLogo img-icon\"/>" + data.baseName);
            $(".marketDetailsQuoteTokenName").html("<img class=\"quoteLogo img-icon\"/>" + data.quoteName);
            $(".marketDetailsName").html("<img class=\"baseLogo img-icon\"/>" + baseSymbol + " / " + "<img" +
                " class=\"quoteLogo img-icon\"/>" + quoteSymbol);
            $(".marketDetailsPermalink").val("https://openserum.io/" + activeMarketId);

            $(".baseLogo").attr("src", baseLogo);
            $(".quoteLogo").attr("src", quoteLogo);
        });
}

function explorerLink(accountId) {
    return "<a target=_blank href=\"https://solana.fm/account/" + accountId + "\">" + accountId + "</a>";
}

function updateDepthChart() {
    if (activeMarketId) {
        let apiUrl = "/api/serum/market/" + activeMarketId + "/depth";
        // bids + asks
        $.get({url: apiUrl, cache: false})
            .done(function (newData) {
                updateSlot(newData.contextSlot);
                // askContextSlot = newData.askContextSlot;
                //
                // $(".marketContext").text("Slot: " + bidContextSlot)

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
                    marketCurrencySymbol + newData.midpoint.toFixed(3) + ' ' + chartTitle.replace(/\s/g, '') + ' -' +
                    ' Openserum - Project Serum Market Data'
                );
            });
    }
}

function updateSlot(slot) {
    contextSlot = slot;
    $(".marketContext").text("Slot: " + contextSlot);
}