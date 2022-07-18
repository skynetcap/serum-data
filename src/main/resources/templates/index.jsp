<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org" class="dark">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Project Serum market data">
    <title>Openserum Market Data</title>
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
    <script src="static/charting.js"></script>
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
    <script async src="https://www.googletagmanager.com/gtag/js?id=G-H55B3XYLG0"></script>
    <script>
        window.dataLayer = window.dataLayer || [];
        function gtag(){dataLayer.push(arguments);}
        gtag('js', new Date());

        gtag('config', 'G-H55B3XYLG0');
    </script>
</head>
<body class="dark">
<div class="container">
    <header class="d-flex flex-wrap justify-content-center py-3 mb-4 border-bottom">
        <a href="/" class="d-flex align-items-center mb-3 mb-md-0 me-md-auto text-dark text-decoration-none">
            <span class="fs-4" style="color: rgb(225, 225, 225);"><img src="static/serum-srm-logo.png" width="32"
                                                                       height="32"
                                                                       style="margin-right: 0.5rem!important;">Openserum Market Data</span>
        </a>

        <ul class="nav nav-pills">
            <li class="nav-item"><a href="/" class="nav-link active" aria-current="page">Home</a></li>
            <li class="nav-item"><a href="/markets" aria-current="page" class="nav-link">Markets</a></li>
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
                            href="#" onClick="loadMarkets('SRMuApVNdxXokk5GT7XD5cUUgXMBCoAz2LHeuAoKWRt');">SRM</a> - <a
                            href="#" onClick="loadMarkets('MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac');">MNGO</a>
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
                            <table id="bidsTable" class="table table-striped table-hover cell-border"
                                   style="width: 100%; table-layout: fixed;">
                                <thead>
                                <tr>
                                    <th scope="col">Owner</th>
                                    <th scope="col">Size</th>
                                    <th scope="col">Price</th>
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
                                    <th scope="col">Price</th>
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
                    <div class="orderBook" style="height: 100% !important; max-height: 650px; overflow-x: hidden;">
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
                ajax: {
                    url: '/api/serum/market/' + activeMarketId + '/bids',
                    dataSrc: ''
                },
                columns: [
                    {
                        data: 'owner',
                        render: function (data, type, row) {
                            if (typeof row.metadata.name !== 'undefined') {
                                return "<img src=\"static/entities/" + row.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.metadata.name;
                            } else {
                                return "<a href=\"https://solscan.io/account/" + row.owner.publicKey + "\" target=_blank>" +
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
                        className: 'table-success dt-right',
                        width: '25%'
                    }
                ]
            });

            var askTable = $('#asksTable').DataTable({
                paging: false,
                ajax: {
                    url: '/api/serum/market/' + activeMarketId + '/asks',
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
                                return "<img src=\"static/entities/" + row.metadata.icon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.metadata.name;
                            } else {
                                return "<a href=\"https://solscan.io/account/" + row.owner.publicKey + "\" target=_blank>" +
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
                        className: 'dt-left table-danger',
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
                ]
            });

            // Trade history table
            var tradeHistoryTable = $('#tradeHistoryTable').DataTable({
                ordering: false,
                searching: false,
                paging: false,
                info: false,
                ajax: {
                    url: '/api/serum/market/' + activeMarketId + '/tradeHistory',
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
                                return"<img src=\"static/entities/" + row.takerEntityIcon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.takerEntityName;
                            } else {
                                return "<a href=\"https://solscan.io/account/" + row.owner.publicKey + "\" target=_blank>" +
                                    row.owner.publicKey.substring(0, 3) +
                                    ".." +
                                    row.owner.publicKey.substring(row.owner.publicKey.toString().length - 3) +
                                    "</a>";
                            }
                        }
                    },
                    {
                        data: 'maker',
                        render: function (data, type, row) {
                            if (row.makerEntityName) {
                                return"<img src=\"static/entities/" + row.makerEntityIcon + ".png\" width=16 height=16 style=\"margin-right: 6px;\">" +
                                    row.makerEntityName;
                            } else {
                                return "<a href=\"https://solscan.io/account/" + row.owner.publicKey + "\" target=_blank>" +
                                    row.owner.publicKey.substring(0, 3) +
                                    ".." +
                                    row.owner.publicKey.substring(row.owner.publicKey.toString().length - 3) +
                                    "</a>";
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
                        width: '25%'
                    }
                ],
                rowCallback: function( row, data, displayNum, displayIndex, dataIndex ) {
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