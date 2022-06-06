<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
<head>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js" integrity="sha256-/xUj+3OJU5yExlq6GSYGSHk7tPXikynS7ogEvDej/m4=" crossorigin="anonymous"></script>
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
Market Details:
<hr>


<script>

    $('#searchForMarkets').click(function () {
        var baseMint = $('#tokenSelect').val();
        loadMarkets(baseMint);
    });

    function loadMarkets(tokenId) {
        let apiUrl = "/api/serum/token/" + tokenId;
        $.get( apiUrl, function( data ) {
            $("#marketList").empty();
            $.each(data, function(k, v) {
                $("#marketList").append("<li>" + v.baseName + " / " + v.quoteName + " / "  + v.id + "</li>");
            })
        });
    }
</script>

</html>