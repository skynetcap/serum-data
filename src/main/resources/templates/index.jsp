<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org" class="dark">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Project Serum market data">
    <title>openbook-dex Market Data - OpenSerum</title>
    <link rel="shortcut icon" type="image/png" href="static/serum-srm-logo.png"/>

    <!-- DARK MODE -->
    <meta name="color-scheme" content="dark">
    <link href="static/css/bootstrap-nightshade.min.css" rel="stylesheet">
    <link href="static/css/custom.css" rel="stylesheet">
    <link href="static/css/jquery.dataTables.min.css" rel="stylesheet">
    <!-- end dark mode -->

    <!-- github/twitter icons -->
    <link rel="stylesheet" href="static/css/font-awesome.min.css">
    <link href="static/css/select2.min.css" rel="stylesheet"/>

    <!-- jquery & chartjs -->
    <script src="static/js/jquery-3.6.0.min.js"></script>
    <script src="static/js/chart.min.js"></script>

    <!-- depth -->
    <script src="static/highcharts.js"></script>
    <script src="static/plugin.js"></script>

    <script src="static/js/select2.min.js"></script>
    <!-- JavaScript Bundle with Popper -->
    <script src="static/js/bootstrap.bundle.min.js"></script>
    <script src="static/js/jquery.dataTables.min.js"></script>
    <script src="static/js/custom.js"></script>

    <!-- inlined vars from controller -->
    <script th:inline="javascript">
        /*<![CDATA[*/
        var initialMarketId = /*[[${marketId}]]*/ '';
        var defaultTokenId = /*[[${defaultTokenId}]]*/ '';
        /*]]>*/


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
    <!-- Global site tag (gtag.js) - Google Analytics -->
    <script async defer src="https://www.googletagmanager.com/gtag/js?id=G-H55B3XYLG0"></script>
    <script>
        window.dataLayer = window.dataLayer || [];

        function gtag() {
            dataLayer.push(arguments);
        }

        gtag('js', new Date());

        gtag('config', 'G-H55B3XYLG0');
    </script>
</head>
<body class="dark">
<div class="container-fluid" style="max-width: 1500px !important;">
    <header class="d-flex flex-wrap justify-content-center py-3 mb-4 border-bottom">
        <a href="/" class="d-flex align-items-center mb-3 mb-md-0 me-md-auto text-dark text-decoration-none">
            <span class="coloredlink" style="font-size: calc(1.4rem + .3vw)!important;"><img
                    src="static/serum-srm-logo.png"
                                                                                  width="32"
                                                                       height="32"
                                                                       style="margin-right: 0.5rem!important;">openbook-dex Market Data</span>
        </a>
        <ul class="nav nav-pills">
            <li class="nav-item"><a href="/" class="nav-link active" aria-current="page">Home</a></li>
            <li class="nav-item"><a href="/markets" aria-current="page" class="nav-link">Markets</a></li>
            <li class="nav-item"><a th:href="${marketUrl}" aria-current="page"
                                    class="nav-link"
                                    target="_blank" style="background-image:
                                                         linear-gradient(45deg, #ff9500, #ff0000); color:white;"><img
                    src="static/entities/solape.ico" width="20"
                    height="20">Trade on Solape</a></li>
        </ul>
    </header>
</div>
<main class="container-fluid" style="max-width: 1500px !important;">
    <div class="p-5 rounded" style="padding-top: 0px!important;">
        <div class="row">
            <div class="col-sm-4">
                <div class="card">
                    <div class="card-body">
                        <h5 class="card-title" style="float: left; margin-right: 10px">Token</h5>
                        <p class="card-text">
                            <select class="form-control" id="tokenSelect" style="display: none; width: 75%;">
                                <option th:each="token : ${tokens.values()}"
                                        th:value="${token.address}"
                                        th:attr="data-icon=${marketRankManager.getImage(token.address)},data-rank=${marketRankManager.getMarketRankOfToken(token.address)}"
                                        th:text="${token.symbol} + ' (' + ${token.name} + ') (' + ${token.address} + ')'">
                                </option>
                            </select>
                        </p>
                        Popular:  <a
                            href="#"
                            onClick="loadMarkets('So11111111111111111111111111111111111111112'); setMarket('8BnEgHoWFysVcuFFX7QztDmzuH8r5ZFvyP3sYwn1XTh6');"
                            style="padding-right: 3px;"><img width="20" height="20"
                                                             src="/api/serum/token/So11111111111111111111111111111111111111112/icon"
                                                             class="img-icon"
                                                             style="border-radius: 5px; margin-right: 2px">SOL</a> <a
                            href="#"
                            onClick="loadMarkets('mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So'); setMarket('9Lyhks5bQQxb9EyyX55NtgKQzpM4WK7JCmeaWuQ5MoXD');"
                            style="padding-right: 3px;"><img width="20" height="20"
                                                             src="/api/serum/token/mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So/icon"
                                                             class="img-icon"
                                                             style="border-radius: 5px; margin-right: 2px">mSOL</a> <a
                            href="#"
                            onClick="loadMarkets('Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB'); setMarket('B2na8Awyd7cpC59iEU43FagJAPLigr3AP3s38KM982bu');"
                            style="padding-right: 3px;"><img
                            width="20" height="20"
                            src="/api/serum/token/Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB/icon"
                            class="img-icon" style="=border-radius: 5px; margin-right: 2px">USDT</a> <a
                            href="#"
                            onClick="loadMarkets('7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs'); setMarket('FZxi3yWkE5mMjyaZj6utmYL54QQYfMCKMcLaQZq4UwnA');"
                            style="padding-right: 3px;"><img width="20" height="20"
                                                             src="/api/serum/token/7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs/icon"
                                                             class="img-icon"
                                                             style="border-radius: 5px; margin-right: 2px">ETH</a>
                        <hr>
                        <input type="button" class="btn btn-primary" value="Search for Markets" id="searchForMarkets"
                               style="width: 100%">
                    </div>
                </div>
                <!-- markets card -->
                <p>
                <div class="card overflow-auto">
                    <div class="card-body">
                        <table id="marketList" class="table table-striped table-hover cell-border">
                            <thead>
                            <th>Base</th>
                            <th>Quote</th>
                            <th>Activity</th>
                            <th></th>
                            <tbody style="font-size: 18px; cursor: pointer;"></tbody>
                            </thead>
                        </table>
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
                            <table id="bidsTable" class="table table-striped table-hover cell-border"
                                   style="width: 100%; table-layout: fixed;">
                                <thead>
                                <tr>
                                    <th scope="col">Owner</th>
                                    <th scope="col">Size</th>
                                    <th scope="col" style="color: #118005">Price</th>
                                </tr>
                                </thead>
                                <tbody>
                                </tbody>
                            </table>
                        </div>
                        <div class="column orderBook">
                            <table id="asksTable" class="table table-striped table-hover cell-border"
                                   style="width: 100%; table-layout: fixed;">
                                <thead>
                                <tr>
                                    <th scope="col" style="color: #990603">Price</th>
                                    <th scope="col">Size</th>
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
                    <div class="orderBook" style="height: 100% !important; max-height: 625px; overflow-x: hidden;">
                        <table id="tradeHistoryTable" class="table table-striped table-hover cell-border"
                               style="width: 100%; table-layout: fixed; overflow-x:hidden;">
                            <thead>
                            <tr>
                                <th scope="col">Price</th>
                                <th scope="col">Size</th>
                                <th scope="col">Taker</th>
                                <th scope="col">Maker</th>
                            </tr>
                            </thead>
                            <tbody>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
        <p></p>
        <div class="row">
            <div class="card col-sm-6">
                <div class="card-body text-center">
                    <table class="table table-striped table-hover cell-border table-bordered" style="border-color:
                    rgb(29, 29, 29);">
                        <thead>
                        <tr>
                            <h4><img src="static/serum-srm-logo.png" width="32" height="32" style="margin-right:
                            0.5rem!important;">Market Information</h4>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <th scope="row">Base Token</th>
                            <td><span class="marketDetailsBaseTokenName"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Quote Token</th>
                            <td><span class="marketDetailsQuoteTokenName"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Market ID</th>
                            <td><span class="marketDetailsId"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Base Mint</th>
                            <td><span class="marketDetailsBaseMint"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Quote Mint</th>
                            <td><span class="marketDetailsQuoteMint"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Bids</th>
                            <td><span class="marketDetailsBids"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Asks</th>
                            <td><span class="marketDetailsAsks"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Event Queue</th>
                            <td><span class="marketDetailsEventQueue"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Base Vault</th>
                            <td><span class="marketDetailsBaseVault"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Quote Vault</th>
                            <td><span class="marketDetailsQuoteVault"></span></td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div class="card col-sm-6">
                <div class="card-body text-center">
                    <table class="table table-striped table-hover cell-border table-bordered" style="border-color:
                    rgb(29, 29, 29);">
                        <thead>
                        <tr>
                            <h4>Additional Details</h4>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <th scope="row">Market Name</th>
                            <td><span class="marketDetailsName"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Base Deposits</th>
                            <td><span class="marketDetailsBaseDepositsTotal"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Quote Deposits</th>
                            <td><span class="marketDetailsQuoteDepositsTotal"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Base Lot Size</th>
                            <td><span class="marketDetailsBaseLotSize"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Quote Lot Size</th>
                            <td><span class="marketDetailsQuoteLotSize"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Base Decimals</th>
                            <td><span class="marketDetailsBaseDecimals"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Quote Decimals</th>
                            <td><span class="marketDetailsQuoteDecimals"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Quote Dust Threshold</th>
                            <td><span class="marketDetailsQuoteDustThreshold"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Fee Rate (bps)</th>
                            <td><span class="marketDetailsFeeRateBps"></span></td>
                        </tr>
                        <tr>
                            <th scope="row">Permalink</th>
                            <td><input style="width: 100%" type="text" readonly="readonly"
                                       class="marketDetailsPermalink"></td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div style="margin-top: 15px">
            <ul style="list-style-type: none; margin: 0; padding: 0; overflow: hidden;">
                <li class="nav-item" style="float: right"><a href="https://twitter.com/openbookdex" aria-current="page"
                                                             class="nav-link"
                                                             target="_blank"><i class="fa fa-twitter"></i> Twitter</a>
                </li>
                <li class="nav-item" style="float: right"><a href="https://github.com/openbook-dex/resources"
                                                             aria-current="page"
                                                             class="nav-link"
                                                             target="_blank"><i class="fa fa-github"></i> GitHub</a>
                </li>
            </ul>
        </div>
    </div>
</main>
<script th:inline="javascript">
    var activeMarketId, lastLoadedMarketId, lastLoadedChartId;
    var marketCurrencySymbol = '$';
    var totalBids, totalAsks;
    var bidTotal, askTotal;
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

    $('#searchForMarkets').click(function () {
        var baseMint = $('#tokenSelect').val();
        loadMarkets(baseMint);
    });

    // DRAW DEPTH CHART AND SET INTERVAL

    var formatter = new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
    });

    var depthChart = Highcharts.chart('container', {
        chart: {
            type: 'area',
            zoomType: 'xy',
            backgroundColor: '#222222'
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
            gridLineWidth: 0,
            title: null,
            tickWidth: 1,
            tickLength: 5,
            tickPosition: 'inside',
            labels: {
                align: 'left',
                x: 8
            }
        }, {
            color: '#ffffff',
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

    setInterval(updateDepthChart, 550);

</script>
<script src="static/js/darkmode.min.js"></script>
<script type="text/javascript" th:inline="none" class="init">
    /*<![CDATA[*/
    $(document).ready(function () {
            var bidTable = $('#bidsTable').DataTable({
                paging: false,
                info: false,
                ajax: {
                    url: '/api/serum/market/' + activeMarketId + '/bids',
                    cache: true,
                    dataSrc: ''
                },
                columns: [
                    {
                        data: 'owner',
                        render: function (data, type, row) {
                            if (typeof row.metadata.name !== 'undefined') {
                                if (row.metadata.name === 'Mango') {
                                    var externalUrl = location.href.replace("#", "") + 'mango/lookup/' +
                                        row.metadata.mangoKey;
                                    return "<a target=_blank href=\"" + externalUrl + "\"><img src=\"static/entities/" +
                                        row.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                        row.metadata.name + " (" + row.metadata.mangoKey.substring(0, 3) + ")</a>";
                                }

                                return "<a target=_blank href=\"https://solana.fm/account/" + row.owner.publicKey + "\"><img src=\"static/entities/" +
                                    row.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.metadata.name + "</a>";
                            } else {
                                return "<a class='coloredlink' href=\"https://solana.fm/account/" + row.owner.publicKey
                                    + "\" target=_blank>" +
                                    row.owner.publicKey.substring(0, 3) +
                                    ".." +
                                    row.owner.publicKey.substring(row.owner.publicKey.toString().length - 3) +
                                    "</a>";
                            }
                        }
                    },
                    {data: 'quantity'},
                    {
                        data: 'price',
                        render: function (data, type, row) {
                            return marketCurrencySymbol + data;
                        }
                    }
                ],
                order: [[2, 'desc']],
                columnDefs: [
                    {
                        targets: [0],
                        className: 'dt-right',
                        width: '50%'
                    },
                    {
                        targets: [1],
                        className: 'dt-right',
                        width: '25%'
                    },
                    {
                        targets: [2],
                        className: 'dt-right',
                        width: '25%'
                    }
                ],
                rowCallback: function (row, data, displayNum, displayIndex, dataIndex) {
                    // Calculate percentage of bids for this row
                    // Global has this calculated callled `totalBids`
                    var total = totalBids ?? 0;
                    if (total !== 0) {
                        // 0.5 percentage of lead, since top of book can look empty
                        var percentage = ((data.metadata.percent ?? 0) * 100) + 0.5;
                        var rowSelector = $(row);
                        rowSelector.css("background", "linear-gradient(270deg, #118005 " + percentage.toFixed(0) +
                            "%, rgba(0, 0, 0, 0.00) 0%)"
                        );
                    }
                }
            });

            var askTable = $('#asksTable').DataTable({
                paging: false,
                info: false,
                ajax: {
                    url: '/api/serum/market/' + activeMarketId + '/asks',
                    cache: true,
                    dataSrc: ''
                },
                columns: [
                    {
                        data: 'price',
                        render: function (data, type, row) {
                            return marketCurrencySymbol + data;
                        }
                    },
                    {data: 'quantity'},
                    {
                        data: 'owner',
                        render: function (data, type, row) {
                            if (typeof row.metadata.name !== 'undefined') {
                                if (row.metadata.name === 'Mango') {
                                    var externalUrl = location.href.replace("#", "") + 'mango/lookup/' + row.metadata.mangoKey;
                                    return "<a target=_blank href=\"" + externalUrl + "\"><img src=\"static/entities/" +
                                        row.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                        row.metadata.name + " (" + row.metadata.mangoKey.substring(0, 3) + ")</a>";
                                }

                                return "<a target=_blank href=\"https://solana.fm/account/" + row.owner.publicKey + "\"><img src=\"static/entities/" +
                                    row.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.metadata.name + "</a>";
                            } else {
                                return "<a class='coloredlink' href=\"https://solana.fm/account/" +
                                    row.owner.publicKey + "\" target=_blank>" +
                                    row.owner.publicKey.substring(0, 3) +
                                    ".." +
                                    row.owner.publicKey.substring(row.owner.publicKey.toString().length - 3) +
                                    "</a>";
                            }
                        }
                    }
                ],
                order: [[0, 'asc']],
                columnDefs: [
                    {
                        targets: [0],
                        className: 'dt-left',
                        width: '25%'
                    },
                    {
                        targets: [1],
                        className: 'dt-left',
                        width: '25%'
                    },
                    {
                        targets: [2],
                        className: 'dt-left',
                        width: '50%'
                    }
                ],
                rowCallback: function (row, data, displayNum, displayIndex, dataIndex) {
                    // Calculate percentage of bids for this row
                    // Global has this calculated callled `totalBids`
                    var total = totalAsks ?? 0;
                    if (total !== 0) {
                        // 0.5 percentage of lead, since top of book can look empty
                        var percentage = ((data.metadata.percent ?? 0) * 100) + 0.5;
                        var rowSelector = $(row);
                        rowSelector.css("background", "linear-gradient(90deg, rgb(199 6 2) " + percentage.toFixed(0) +
                            "%, rgba(0, 0, 0, 0.00) 0%)"
                        );
                    }
                }
            });

            // Trade history table
            var tradeHistoryTable = $('#tradeHistoryTable').DataTable({
                ordering: false,
                searching: false,
                paging: false,
                info: false,
                ajax: {
                    url: '/api/serum/market/' + activeMarketId + '/tradeHistory',
                    cache: true,
                    dataSrc: ''
                },
                columns: [
                    {
                        data: 'price',
                        render: function (data, type, row) {
                            return marketCurrencySymbol + data;
                        }
                    },
                    {data: 'quantity'},
                    {
                        data: 'owner',
                        render: function (data, type, row) {
                            if (row.takerEntityName) {
                                var externalLink = '';
                                if (row.takerEntityName === 'Mango') {
                                    // Add external link
                                    var externalUrl = location.href.replace("#", "") + 'mango/lookup/' + row.takerOoa.publicKey;
                                    externalLink = "<a href=\"" + externalUrl + "\" target=_blank>" +
                                        "<img src=\"static/entities/" +
                                        row.takerEntityIcon +
                                        ".png\" width=16 height=16 style=\"margin-right: 6px;\"></a>"
                                    return externalLink +
                                        "<a target=_blank href=\"" + externalUrl + "\">" +
                                        row.takerEntityName + "</a>";
                                }

                                return "<a target=_blank href=\"https://solana.fm/account/" + row.owner.publicKey + "\"><img src=\"static/entities/" +
                                    row.takerEntityIcon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.takerEntityName + "</a>" + externalLink;
                            } else {
                                if (row.owner) {
                                    return "<a class='coloredlink' href=\"https://solana.fm/account/" +
                                        row.owner.publicKey + "\" target=_blank>" +
                                        row.owner.publicKey.substring(0, 3) +
                                        ".." +
                                        row.owner.publicKey.substring(row.owner.publicKey.toString().length - 3) +
                                        "</a>";
                                } else {
                                    return "Unknown";
                                }
                            }
                        }
                    },
                    {
                        data: 'maker',
                        render: function (data, type, row) {
                            if (row.makerEntityName) {
                                var externalLink = '';
                                if (row.makerEntityName === 'Mango') {
                                    // Add external link
                                    var externalUrl = location.href.replace("#", "") + 'mango/lookup/' + row.makerOoa.publicKey;
                                    externalLink = "<a href=\"" + externalUrl + "\" target=_blank>" +
                                        "<img src=\"static/entities/" +
                                        row.makerEntityIcon +
                                        ".png\" width=16 height=16 style=\"margin-right: 6px;\"></a>"
                                    return externalLink +
                                        "<a target=_blank href=\"" + externalUrl + "\">" +
                                        row.makerEntityName + "</a>";
                                }

                                return "<a target=_blank href=\"https://solana.fm/account/" +
                                    row.makerOwner.publicKey + "\"><img src=\"static/entities/" +
                                    row.makerEntityIcon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.makerEntityName + "</a>";
                            } else {
                                if (row.makerOwner) {
                                    return "<a class='coloredlink' href=\"https://solana.fm/account/" +
                                        row.makerOwner.publicKey + "\" target=_blank>" +
                                        row.makerOwner.publicKey.substring(0, 3) +
                                        ".." +
                                        row.makerOwner.publicKey.substring(row.makerOwner.publicKey.toString().length - 3) +
                                        "</a>";
                                } else {
                                    return "Unknown";
                                }
                            }
                        }
                    }
                ],
                columnDefs: [
                    {
                        targets: [0],
                        className: 'dt-left',
                        width: '30%'
                    },
                    {
                        targets: [1],
                        className: 'dt-left',
                        width: '20%'
                    },
                    {
                        targets: [2],
                        className: 'dt-left',
                        width: '25%'
                    },
                    {
                        targets: [3],
                        className: 'dt-left',
                        width: '25%'
                    }
                ],
                rowCallback: function (row, data, displayNum, displayIndex, dataIndex) {
                    var node = this.api().row(row).nodes().to$();
                    if (data.bid) {
                        node.addClass('table-success')
                    } else {
                        node.addClass('table-danger')
                    }
                }
            });

            setInterval(function () {
                bidTable.ajax.url('/api/serum/market/' + activeMarketId + '/bids');
                bidTable.ajax.reload();
            }, 400);
            setInterval(function () {
                askTable.ajax.url('/api/serum/market/' + activeMarketId + '/asks');
                askTable.ajax.reload();
            }, 400);
            setInterval(function () {
                tradeHistoryTable.ajax.url('/api/serum/market/' + activeMarketId + '/tradeHistory');
                tradeHistoryTable.ajax.reload();
            }, 2500);
        }
    );
    /*]]>*/
</script>
</body>
</html>