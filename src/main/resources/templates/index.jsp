<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Project Serum market data">
    <title>Openserum Market Data</title>
    <link rel="shortcut icon" type="image/png" href="static/serum-srm-logo.png"/>

    <!-- jquery & chartjs -->
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"
            integrity="sha256-/xUj+3OJU5yExlq6GSYGSHk7tPXikynS7ogEvDej/m4=" crossorigin="anonymous"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/3.8.0/chart.min.js"
            integrity="sha512-sW/w8s4RWTdFFSduOTGtk4isV1+190E/GghVffMA9XczdJ2MDzSzLEubKAs5h0wzgSJOQTRYyaz73L3d6RtJSg=="
            crossorigin="anonymous" referrerpolicy="no-referrer"></script>

    <!-- depth -->
    <script src="static/charting.js"></script>
    <script src="static/plugin.js"></script>

    <!-- inlined vars from controller -->
    <script th:inline="javascript">
        /*<![CDATA[*/
        var initialMarketId = /*[[${marketId}]]*/ '';
        var defaultTokenId = /*[[${defaultTokenId}]]*/ '';
        /*]]>*/
    </script>

    <!-- CSS only -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.2.0-beta1/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-0evHe/X+R7YkIZDRvuzKMRqM+OrBnVFBL6DOitfPri4tjfHxaWutUpFmBp4vmVor" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">


    <style>
        .chart-container {
            height: 400px;
            width: 100%;
        }

        .nav-scroller .nav {
            display: flex;
            flex-wrap: nowrap;
            padding-bottom: 1rem;
            margin-top: -1px;
            overflow-x: auto;
            text-align: center;
            white-space: nowrap;
            -webkit-overflow-scrolling: touch;
        }

        /* sticky footer */
        html {
            position: relative;
            min-height: 100%;
        }

        img.img-icon {
            margin: 0 !important;
            display: inherit !important;
            height: 18px;
            width: 18px
        }

        * {
            box-sizing: border-box;
        }

        .row {
            display: flex;
            margin-left: -5px;
            margin-right: -5px;
        }

        .column {
            flex: 50%;
            padding: 5px;
        }

        #container {
            min-width: 100px;
            max-width: 650px;
            height: 175px;
            margin: 0 auto;
        }

        #priceChartTitle {
            text-overflow: ellipsis;
            width: 450px;
            white-space: nowrap;
            overflow: hidden;
        }

        th {
            background: white !important;
            position: sticky;
            top: 0; /* Don't forget this, required for the stickiness */
            box-shadow: 0 2px 2px -1px rgba(0, 0, 0, 0.4);
        }

        .orderBook {
            height: 100%;
            max-height: 450px;
            overflow-y: scroll;
            padding-top: 0 !important;
        }

    </style>
    <link href="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/css/select2.min.css" rel="stylesheet"/>
    <script src="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/js/select2.min.js"></script>

    <script>
        function formatToken(token) {
            // only load top 100 icons
            if (!token.id || token.element.dataset.rank > 100) {
                return token.text;
            }
            return $(
                '<span><img src="' + token.element.dataset.icon + '" class="img-icon" /> ' + token.text + '</span>'
            );
        };

        $(document).ready(function () {
            var options = $("#tokenSelect option");                    // Collect options
            options.detach().sort(function (a, b) {               // Detach from select, then Sort
                var at = $(a).data("rank");
                var bt = $(b).data("rank");
                return (at > bt) ? 1 : ((at < bt) ? -1 : 0);            // Tell the sort function how to order
            });
            options.appendTo("#tokenSelect");                          // Re-attach to select
            $("#tokenSelect").val($("#tokenSelect option:first").val());
            $("#tokenSelect").show();

            $('#tokenSelect').select2({
                templateResult: formatToken,
                templateSelection: formatToken
            });

            // todo - async?
            loadMarkets(defaultTokenId);
            setMarket(initialMarketId);
            updateDepthChart();
        });
    </script>
</head>
<body class="bg-light">
<div class="container">
    <header class="d-flex flex-wrap justify-content-center py-3 mb-4 border-bottom">
        <a href="#" class="d-flex align-items-center mb-3 mb-md-0 me-md-auto text-dark text-decoration-none">
            <span class="fs-4"><img src="static/serum-srm-logo.png" width="32" height="32"
                                    style="margin-right: 0.5rem!important;">Openserum Market Data</span>
        </a>

        <ul class="nav nav-pills">
            <li class="nav-item"><a href="/" class="nav-link active" aria-current="page">Home</a></li>
            <li class="nav-item"><a href="#" aria-current="page" class="nav-link">Market List</a></li>
            <li class="nav-item"><a href="#" aria-current="page" class="nav-link">API</a></li>
            <li class="nav-item"><a href="https://github.com/skynetcap/serum-data" aria-current="page" class="nav-link"
                                    target="_blank"><i class="fa fa-github"></i> GitHub</a></li>
            <li class="nav-item"><a href="https://twitter.com/openserum" aria-current="page" class="nav-link"
                                    target="_blank"><i class="fa fa-twitter"></i> Twitter</a></li>
        </ul>
    </header>
</div>
<main class="container">
    <div class="p-5 rounded" style="padding-top: 0px!important;">
        <div class="row">
            <div class="col-sm-4">
                <div class="card">
                    <div class="card-body">
                        <h5 class="card-title">Tokens</h5>
                        <p class="card-text">
                            <select class="form-control" id="tokenSelect" style="display: none">
                                <option th:each="token : ${tokens.values()}"
                                        th:value="${token.address}"
                                        th:attr="data-icon=${token.logoURI},data-rank=${marketRankManager.getMarketRankOfToken(token.address)}"
                                        th:text="${token.symbol} + ' (' + ${token.name} + ') (' + ${token.address} + ')'">
                                </option>
                            </select>
                        </p>
                        <hr>
                        Popular: <a
                            href="#" onClick="loadMarkets('9n4nbM75f5Ui33ZbPYXn59EwSgE8CGsHtAeTH5YFeJ9E');">BTC</a> - <a
                            href="#" onClick="loadMarkets('7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs');">ETH</a> - <a
                            href="#" onClick="loadMarkets('So11111111111111111111111111111111111111112');">SOL</a> - <a
                            href="#" onClick="loadMarkets('MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac');">MNGO</a> - <a
                            href="#" onClick="loadMarkets('orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE');">ORCA</a>
                        <hr>
                        <input type="button" class="btn btn-primary" value="Search for Markets" id="searchForMarkets"
                               style="width: 100%">
                    </div>
                </div>
                <!-- markets card -->
                <p>
                <div class="card overflow-auto">
                    <div class="card-body">
                        <h5 class="card-title">Markets</h5>
                        <hr>
                        <ul id="marketList" style="height: 100px; overflow: auto; list-style: none; padding-left: 5px">
                        </ul>
                    </div>
                </div>
            </div>
            <div class="col-sm-8">
                <div class="card">
                    <div class="card-body">
                        <h5 id="priceChartTitle" class="card-title">Price Chart</h5>
                        <div class="chart-container">
                            <canvas id="myChart"></canvas>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <p>
        <div class="row">
            <div class="card col-sm-8">
                <div class="card-body">
                    <div style="font-size: 1.25rem; font-weight: 500; display: inline" id="orderBookHeader">Order
                        Book:
                    </div>
                    <hr>
                    <div id="container"></div>
                    <div class="row">
                        <div class="column orderBook">
                            <table id="bidsTable" class="table table-striped table-hover table-bordered"
                                   style="width: 100%">
                                <thead>
                                <tr>
                                    <th scope="col">Price</th>
                                    <th scope="col">Quantity</th>
                                    <th scope="col">Owner</th>
                                </tr>
                                </thead>
                                <tbody>
                                </tbody>
                            </table>
                        </div>
                        <div class="column orderBook">
                            <table id="asksTable" class="table table-striped table-hover table-bordered"
                                   style="width: 100%">
                                <thead>
                                <tr>
                                    <th scope="col">Price</th>
                                    <th scope="col">Quantity</th>
                                    <th scope="col">Owner</th>
                                </tr>
                                </thead>
                                <tbody>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
            <div class="card col-sm-4">
                <div class="card-body">
                    <h5 id="tradeHistoryTitle" class="card-title">Trade History</h5>
                    <hr>
                    <div class="orderBook" style="height: 100% !important; max-height: 650px;">
                        <table id="tradeHistoryTable" class="table table-hover table-bordered" style="width: 100%">
                            <thead>
                            <tr>
                                <th scope="col">Price</th>
                                <th scope="col">Quantity</th>
                                <th scope="col">Taker</th>
                            </tr>
                            </thead>
                            <tbody>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>
<!-- JavaScript Bundle with Popper -->
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.2.0-beta1/dist/js/bootstrap.bundle.min.js"
        integrity="sha384-pprn3073KE6tl6bjs2QrFaJGz5/SUsLqktiwsUTF55Jfv3qYSDhgCecCxMW52nD2"
        crossorigin="anonymous"></script>
<script>
    var activeMarketId, lastLoadedMarketId, lastLoadedChartId;
    var marketCurrencySymbol;
    var totalBids, totalAsks;
    const ctx = document.getElementById('myChart').getContext('2d');
    const myChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Price',
                data: [],
                fill: false,
                borderColor: 'rgb(41,98,255)',
                tension: 0.1
            }]
        },
        options: {
            scales: {
                y: {
                    beginAtZero: false
                }
            },
            animation: false,
            responsive: true,
            maintainAspectRatio: false
        }
    });

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

    $('#searchForMarkets').click(function () {
        var baseMint = $('#tokenSelect').val();
        loadMarkets(baseMint);
    });

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
                if (!v.flags.maker) {
                    $("#tradeHistoryTable tbody").append(
                        "<tr class='" + (v.flags.bid ? "table-success" : "table-danger") + "'>" +
                        "<td>" + v.price + "</td>" +
                        "<td style=\"text-align: right\">" + v.quantity + "</td>" +
                        "<td>" + (v.owner.toString().length > 32 ? v.owner.substring(0, 3) + ".." + v.owner.substring(v.owner.toString().length - 3) : v.owner) + "</td>" +
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
        let apiUrl = "/api/serum/market/" + activeMarketId + "/cached";
        $.get({url: apiUrl, cache: false})
            .done(function (data) {
                // only update html if the refresh is a new market
                if (data.id !== lastLoadedMarketId) {
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
                }

                if (data.quoteSymbol === 'USDC' || data.quoteSymbol === 'USDT') {
                    marketCurrencySymbol = '$';
                } else {
                    marketCurrencySymbol = '';
                }


                // bids
                $('#bidsTable tbody').empty();
                $.each(data.bids, function (k, v) {
                    $("#bidsTable tbody").append(
                        "<tr>" +
                        "<td style=\"text-align: right\">" + marketCurrencySymbol + v.price + "</td>" +
                        "<td style=\"text-align: right\">" +
                        v.quantity +
                        "</td>" +
                        "<td style=\"text-align: left\">" +
                        (v.metadata.icon ? "<img src=\"static/entities/" + v.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" : "") +
                        (v.owner.toString().length > 32 ? v.owner.substring(0, 3) + ".." + v.owner.substring(v.owner.toString().length - 3) : v.owner) +
                        "</td>" +
                        "</tr>"
                    );
                })

                // asks
                $('#asksTable tbody').empty();
                $.each(data.asks, function (k, v) {
                    $("#asksTable tbody").append(
                        "<tr>" +
                        "<td style=\"text-align: right\">" + marketCurrencySymbol + v.price + "</td>" +
                        "<td style=\"text-align: right\">" +
                        v.quantity +
                        "</td>" +
                        "<td style=\"text-align: left\">" +
                        (v.metadata.icon ? "<img src=\"static/entities/" + v.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" : "") +
                        (v.owner.toString().length > 32 ? v.owner.substring(0, 3) + ".." + v.owner.substring(v.owner.toString().length - 3) : v.owner) +
                        "</td>" +
                        "</tr>"
                    );
                })
            });
    }

    function updateOrderBookLoop() {
        if (activeMarketId) {
            loadMarketDetail();
        }
    }

    function updateSales() {
        if (activeMarketId) {
            let apiUrl = "/api/serum/market/" + activeMarketId + "/tradeHistory";
            $.get({url: apiUrl, cache: false})
                .done(function (data) {
                    $('#tradeHistoryTable tbody').empty();
                    $.each(data, function (k, v) {
                        if (!v.flags.maker) {
                            $("#tradeHistoryTable tbody").append(
                                "<tr class='" + (v.flags.bid ? "table-success" : "table-danger") + "'>" +
                                "<td style=\"text-align: right\">" + marketCurrencySymbol + v.price + "</td>" +
                                "<td style=\"text-align: right\">" +
                                v.quantity +
                                "</td>" +
                                "<td style=\"text-align: left\">" +
                                (v.jupiterTx ? "<a href=\"https://explorer.solana.com/tx/" + v.jupiterTx + "\" target=_blank><img src=\"static/entities/jup.png\" width=16 height=16 style=\"margin-right: 6px;\"> Jupiter (" + v.owner.substring(0, 3) + "..)" : "") +
                                ((!v.jupiterTx && v.icon) !== '' ? "<img src=\"static/entities/" + v.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" : "") +
                                (!v.jupiterTx ? (v.owner.toString().length > 32 ? v.owner.substring(0, 3) + ".." + v.owner.substring(v.owner.toString().length - 3) : v.owner) : "") +
                                "</td>" +
                                "</tr>"
                            );
                        }
                    })
                });
        }
    }

    setInterval(updateOrderBookLoop, 1100);
    setInterval(updateSales, 3000);

</script>
<script th:inline="javascript">
    var bidTotal, askTotal;
    var formatter = new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
    });

    var depthChart = Highcharts.chart('container', {
        chart: {
            type: 'area',
            zoomType: 'xy'
        },
        title: {
            text: ''
        },
        xAxis: {
            minPadding: 0,
            maxPadding: 0,
            min: 0,
            max: 0,
            plotLines: [{
                color: '#888',
                value: 0,
                width: 1,
                label: {
                    text: '',
                    rotation: 90
                }
            }]
        },
        yAxis: [{
            lineWidth: 1,
            gridLineWidth: 1,
            title: null,
            tickWidth: 1,
            tickLength: 5,
            tickPosition: 'inside',
            labels: {
                align: 'left',
                x: 8
            }
        }, {
            opposite: true,
            linkedTo: 0,
            lineWidth: 1,
            gridLineWidth: 0,
            title: null,
            tickWidth: 1,
            tickLength: 5,
            tickPosition: 'inside',
            labels: {
                align: 'right',
                x: -8
            }
        }],
        legend: {
            enabled: false
        },
        plotOptions: {
            area: {
                fillOpacity: 0.2,
                lineWidth: 1,
                step: 'center'
            }
        },
        tooltip: {
            headerFormat: '<span style="font-size=10px;">Price: {point.key}</span><br/>',
            valueDecimals: 2
        },
        series: [{
            name: 'Total Quantity',
            data: [],
            color: '#03a7a8'
        }, {
            name: 'Total Quantity',
            data: [],
            color: '#fc5857'
        }]
    });

    function updateDepthChart() {
        if (activeMarketId) {
            let apiUrl = "/api/serum/market/" + activeMarketId + "/depth";
            // bids
            $.get({url: apiUrl, cache: false})
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


                    totalAsks = newData.asks[newData.asks.length - 1][1].toFixed(2);

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
                            fontSize: '12px'
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
                            fontSize: '12px'
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

                    $(document).attr("title",
                        ((newData.chartTitle.includes("USDC Price") || newData.chartTitle.includes("USDT Price")) ? '$' : '') + newData.midpoint.toFixed(2) + ' ' + newData.chartTitle.replace("Price", "").replace(/\s/g, '')
                    );
                });
        }
    }

    setInterval(updateDepthChart, 550);

</script>
</body>
</html>