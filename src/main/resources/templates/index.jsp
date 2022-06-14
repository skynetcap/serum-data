<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
<head>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js" integrity="sha256-/xUj+3OJU5yExlq6GSYGSHk7tPXikynS7ogEvDej/m4=" crossorigin="anonymous"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/3.8.0/chart.min.js" integrity="sha512-sW/w8s4RWTdFFSduOTGtk4isV1+190E/GghVffMA9XczdJ2MDzSzLEubKAs5h0wzgSJOQTRYyaz73L3d6RtJSg==" crossorigin="anonymous" referrerpolicy="no-referrer"></script>
    <style>
        table, th, td {
            border: 1px solid black;
            border-collapse: collapse;
        }

        .chart-container {
            width: 600px;
            height:400px
        }
    </style>
    <!-- CSS only -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.2.0-beta1/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-0evHe/X+R7YkIZDRvuzKMRqM+OrBnVFBL6DOitfPri4tjfHxaWutUpFmBp4vmVor" crossorigin="anonymous">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.2.0-beta1/dist/js/bootstrap.min.js" integrity="sha384-kjU+l4N0Yf4ZOJErLsIcvOU2qSb74wXpOhqTvwVx3OElZRweTnQ6d31fXEoRD1Jy" crossorigin="anonymous"></script>
</head>
<h1>Serum Data</h1>
<hr>
<label for="tokenSelect">Token: </label>
<select class="form-control" id="tokenSelect">
    <option th:each="token : ${tokens.values()}"
            th:value="${token.address}"
            th:text="${token.symbol} + ' (' + ${token.name} + ') (' + ${token.address} + ')'">
    </option>
</select>
<input type="button" value="Search for Markets" id="searchForMarkets">
<br>
Popular Tokens: <a href="#" onClick="loadMarkets('So11111111111111111111111111111111111111112');">SOL</a> - <a href="#" onClick="loadMarkets('orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE');">ORCA</a> - <a href="#" onClick="loadMarkets('MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac');">MNGO</a> - <a href="#" onClick="loadMarkets('9n4nbM75f5Ui33ZbPYXn59EwSgE8CGsHtAeTH5YFeJ9E');">BTC</a> - <a href="#" onClick="loadMarkets('7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs');">ETH</a>
<hr>
<table>
    <tr>
        <td>
            Markets:
            <ul id="marketList">
            </ul>
        </td>
        <td>
            <div class="chart-container">
                <canvas id="myChart"></canvas>
            </div>
        </td>
    </tr>
</table>
<hr>
<table>
    <tr>
        <td>
            <span id="marketHeader" style="display: none;">
                <img id="baseLogo" src="" width="64" height="64"><img id="quoteLogo" src="" width="64" height="64">
                <span id="baseTokenSpan"></span> / <span id="quoteTokenSpan"></span>
            </span>
        </td>
    </tr>
    <tr>
        <td>Bids</td>
        <td>Asks</td>
        <td>Trade History</td>
    </tr>
    <tr>
        <td style="vertical-align:top;">
            <table id="bidsTable" >
                <thead>
                <tr>
                    <th>Price</th><th>Quantity</th><th>Owner</th>
                </tr>
                </thead>
                <tbody>

                </tbody>
            </table>
        </td>
        <td style="vertical-align:top;">
            <table id="asksTable" >
                <thead>
                <tr>
                    <th>Price</th><th>Quantity</th><th>Owner</th>
                </tr>
                </thead>
                <tbody>

                </tbody>
            </table>
        </td>
        <td style="vertical-align:top;">
            <table id="tradeHistoryTable">
                <thead>
                <tr>
                    <th>Price</th><th>Quantity</th><th>Owner</th>
                </tr>
                </thead>
                <tbody>

                </tbody>
            </table>
        </td>
    </tr>
</table>
<script>
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
            responsive: true,
            maintainAspectRatio: false
        }
    });

    function addData(label, data) {
        myChart.data.labels.push(label);
        myChart.data.datasets.forEach((dataset) => {
            dataset.data.push(data);
        });
        //myChart.update();
    }
</script>

<hr>


<script>

    var activeMarketId;

    $('#searchForMarkets').click(function () {
        var baseMint = $('#tokenSelect').val();
        loadMarkets(baseMint);
    });

    function loadMarkets(tokenId) {
        let apiUrl = "/api/serum/token/" + tokenId;
        $.get( apiUrl, function( data ) {
            $("#marketList").empty();
            $.each(data, function(k, v) {
                $("#marketList").append("<li>" +
                    "<a href=\"#\" onClick=\"setMarket('" + v.id + "');\">" + v.baseName + " / " + v.quoteName + " / "  + v.id + "</a></li>");
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
        $.get( apiUrl, function( data ) {
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


            $.each(data, function(k, v) {
                if (!v.flags.maker) {
                    $("#tradeHistoryTable tbody").append("<tr style='background-color: " + (v.flags.bid ? "green" : "red") + "'><td>" + v.price + "</td><td>" + v.quantity + "</td><td>" + v.owner + "</td></tr>");
                }
                addData(k, v.price);
            })

            myChart.data.datasets.forEach((dataset) => {
                dataset.data.reverse();
            });
            myChart.update();
        });
    }

    function loadMarketDetail() {
        let apiUrl = "/api/serum/market/" + activeMarketId;
        $.get( apiUrl, function( data ) {
            $("#baseTokenSpan").text(data.baseName);
            $("#quoteTokenSpan").text(data.quoteName);
            $("#baseLogo").attr("src", data.baseLogo);
            $("#marketHeader").attr("style", "display: inherit;");
            $("#quoteLogo").attr("src", data.quoteLogo);


            // bids
            $('#bidsTable tbody').empty();
            $.each(data.bids, function(k, v) {
                // if (v.owner === '4beBRAZSVcCm7jD7yAmizqqVyi39gVrKNeEPskickzSF') {
                //     v.owner = 'Alameda Research';
                // } else if (v.owner === 'EpAdzaqV13Es3x4dukfjFoCrKVXnZ7y9Y76whgMHo5qx') {
                //     v.owner = 'Atrix Finance';
                // }
                $("#bidsTable tbody").append("<tr><td>" + v.price + "</td><td>" + v.quantity + "</td><td>"  + v.owner + "</td></tr>");
            })

            // asks
            $('#asksTable tbody').empty();
            $.each(data.asks, function(k, v) {
                // if (v.owner === '4beBRAZSVcCm7jD7yAmizqqVyi39gVrKNeEPskickzSF') {
                //     v.owner = 'Alameda Research';
                // } else if (v.owner === 'EpAdzaqV13Es3x4dukfjFoCrKVXnZ7y9Y76whgMHo5qx') {
                //     v.owner = 'Atrix Finance';
                // }
                $("#asksTable tbody").append("<tr><td>" + v.price + "</td><td>" + v.quantity + "</td><td>"  + v.owner + "</td></tr>");
            })
        });
    }

    function updateOrderBookLoop() {
        if (activeMarketId) {
            loadMarketDetail();
        }
    }

    setInterval(updateOrderBookLoop, 800);
</script>

</html>