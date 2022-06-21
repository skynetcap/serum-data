<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
<head>
    <title>Chart Test</title>
    <style>
        #container {
            min-width: 310px;
            max-width: 1040px;
            height: 400px;
            margin: 0 auto;
        }
    </style>
    <script src="static/charting.js"></script>
    <script src="static/plugin.js"></script>
    <script th:inline="javascript">
        /*<![CDATA[*/
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
    Highcharts.chart('container', {
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
            name: 'Bids',
            data: bids,
            color: '#03a7a8'
        }, {
            name: 'Asks',
            data: asks,
            color: '#fc5857'
        }]
    });

</script>
</body>
</html>
