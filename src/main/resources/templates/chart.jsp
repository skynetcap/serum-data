<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
<head>
    <title>Chart Test</title>
    <style>
        #container {
            min-width: 310px;
            max-width: 700px;
            height: 400px;
            margin: 0 auto;
        }
    </style>
    <script src="static/charting.js"></script>
    <script src="static/plugin.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"
            integrity="sha256-/xUj+3OJU5yExlq6GSYGSHk7tPXikynS7ogEvDej/m4=" crossorigin="anonymous"></script>
    <script th:inline="javascript">
        /*<![CDATA[*/
        var activeMarketId = '9wFFyRfZBsuAha4YcuxcXLKwMxJR43S7fPfQLusDBzvT';
        var bids = /*[[${initialBids}]]*/ '';
        var asks = /*[[${initialAsks}]]*/ '';
        var midPoint = /*[[${midpoint}]]*/ '';
        var chartTitle = /*[[${chartTitle}]]*/ 'Price';
        /*]]>*/
    </script>
</head>
<body>
<div id="container"></div>
<script th:inline="javascript">
    var depthChart = Highcharts.chart('container', {
        chart: {
            type: 'area',
            zoomType: 'xy'
        },
        title: {
            text: chartTitle
        },
        xAxis: {
            minPadding: 0,
            maxPadding: 0,
            min: midPoint - (midPoint / 10),
            max: midPoint + (midPoint / 10),
            plotLines: [{
                color: '#888',
                value: midPoint,
                width: 1,
                label: {
                    text: '',
                    rotation: 90
                }
            }],
            title: {
                text: 'Price'
            }
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
            data: bids,
            color: '#03a7a8'
        }, {
            name: 'Total Quantity',
            data: asks,
            color: '#fc5857'
        }]
    });

    function updateDepthChart() {
        let apiUrl = "/api/serum/market/" + activeMarketId + "/depth";
        // bids
        $.get({url: apiUrl, cache: false})
            .done(function (newData) {
                depthChart.series[0].setData(newData.bids);
                depthChart.series[1].setData(newData.asks);
                depthChart.xAxis[0].options.plotLines[0].value = newData.midpoint;
                depthChart.xAxis[0].setExtremes(newData.midpoint - (newData.midpoint / 10), newData.midpoint + (newData.midpoint / 10));
                depthChart.xAxis[0].update();

                depthChart.redraw();
                depthChart.hideLoading();
            });
    }

    setInterval(updateDepthChart, 1200);

</script>
</body>
</html>
