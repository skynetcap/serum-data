<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
<head>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js" integrity="sha256-/xUj+3OJU5yExlq6GSYGSHk7tPXikynS7ogEvDej/m4=" crossorigin="anonymous"></script>
    <style>
        table, th, td {
            border: 1px solid black;
            border-collapse: collapse;
        }
    </style>
</head>
<label for="tokenSelect">Token: </label>
<select class="form-control" id="tokenSelect">
    <option th:each="token : ${tokens.values()}"
            th:value="${token.address}"
            th:text="${token.symbol} + ' (' + ${token.name} + ') (' + ${token.address} + ')'">
    </option>
</select>
<input type="button" value="Search for Markets" id="searchForMarkets">
<br>
Popular Tokens: <a href="#" onClick="loadMarkets('So11111111111111111111111111111111111111112');">SOL</a> - <a href="#" onClick="loadMarkets('orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE');">ORCA</a> - <a href="#" onClick="loadMarkets('MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac');">MNGO</a>
<hr>
Markets:
<ul id="marketList">
</ul>
<hr>
<table>
    <tr><td>Market Details: <span id="marketIdSpan"></span></td></tr>
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
                    <th>Price</th><th>Quantity</th><th>Flags</th>
                </tr>
                </thead>
                <tbody>

                </tbody>
            </table>
        </td>
    </tr>
</table>
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
            $.each(data, function(k, v) {
                if (!v.flags.maker) {
                    $("#tradeHistoryTable tbody").append("<tr><td>" + v.price + "</td><td>" + v.quantity + "</td><td>" + JSON.stringify(v.flags) + "</td></tr>");
                }
            })
        });
    }

    function loadMarketDetail(marketId) {
        let apiUrl = "/api/serum/market/" + activeMarketId;
        $.get( apiUrl, function( data ) {
            $("#marketIdSpan").text(marketId);

            // bids
            $('#bidsTable tbody').empty();
            $.each(data.bids, function(k, v) {
                if (v.owner === '4beBRAZSVcCm7jD7yAmizqqVyi39gVrKNeEPskickzSF') {
                    v.owner = 'Alameda Research';
                } else if (v.owner === 'EpAdzaqV13Es3x4dukfjFoCrKVXnZ7y9Y76whgMHo5qx') {
                    v.owner = 'Atrix Finance';
                }
                $("#bidsTable tbody").append("<tr><td>" + v.price + "</td><td>" + v.quantity + "</td><td>"  + v.owner + "</td></tr>");
            })

            // asks
            $('#asksTable tbody').empty();
            $.each(data.asks, function(k, v) {
                if (v.owner === '4beBRAZSVcCm7jD7yAmizqqVyi39gVrKNeEPskickzSF') {
                    v.owner = 'Alameda Research';
                } else if (v.owner === 'EpAdzaqV13Es3x4dukfjFoCrKVXnZ7y9Y76whgMHo5qx') {
                    v.owner = 'Atrix Finance';
                }
                $("#asksTable tbody").append("<tr><td>" + v.price + "</td><td>" + v.quantity + "</td><td>"  + v.owner + "</td></tr>");
            })
        });
    }

    function updateOrderBookLoop() {
        if (activeMarketId) {
            loadMarketDetail(activeMarketId);
        }
    }

    setInterval(updateOrderBookLoop, 800);
</script>

</html>