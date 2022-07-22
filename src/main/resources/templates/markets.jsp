<html lang="en" xmlns="http://www.w3.org/1999/xhtml"
      xmlns:th="http://www.thymeleaf.org/dtd/xhtml1-strict-thymeleaf-4.dtd" class="dark">
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

    <!-- jquery & chartjs -->
    <script src="static/js/jquery-3.6.0.min.js"></script>
    <script src="static/js/chart.min.js"></script>

    <!-- JavaScript Bundle with Popper -->
    <script src="static/js/bootstrap.bundle.min.js"></script>

    <script src="static/js/custom.js"></script>
    <script src="//cdn.datatables.net/1.12.1/js/jquery.dataTables.min.js"></script>
    <!-- Global site tag (gtag.js) - Google Analytics -->
    <script async src="https://www.googletagmanager.com/gtag/js?id=G-H55B3XYLG0"></script>
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
<div class="container">
    <header class="d-flex flex-wrap justify-content-center py-3 mb-4 border-bottom">
        <a href="/" class="d-flex align-items-center mb-3 mb-md-0 me-md-auto text-dark text-decoration-none">
            <span class="fs-4" style="color: rgb(225, 225, 225);"><img src="static/serum-srm-logo.png" width="32"
                                                                       height="32"
                                                                       style="margin-right: 0.5rem!important;">Openserum Market Data</span>
        </a>

        <ul class="nav nav-pills">
            <li class="nav-item"><a href="/" class="nav-link" aria-current="page">Home</a></li>
            <li class="nav-item"><a href="/markets" aria-current="page" class="nav-link active">Markets</a></li>
            <li class="nav-item"><a href="#" aria-current="page" class="nav-link">API</a></li>
            <li class="nav-item"><a href="https://github.com/skynetcap/serum-data" aria-current="page" class="nav-link"
                                    target="_blank"><i class="fa fa-github"></i> GitHub</a></li>
            <li class="nav-item"><a href="https://twitter.com/openserum" aria-current="page" class="nav-link"
                                    target="_blank"><i class="fa fa-twitter"></i> Twitter</a></li>
            <li class="nav-item"><a href="https://trade.mango.markets?ref=openserum" aria-current="page"
                                    class="nav-link"
                                    target="_blank" style="background-image:
                                                         linear-gradient(45deg, #FF6404, #FE3800); color:white;"><img
                    src="https://mango.markets/favicon.ico" width="20"
                    height="20">Mango Markets</a></li>
        </ul>
    </header>
</div>
<main class="container">
    <div class="p-5 rounded" style="padding-top: 0px!important;">
        <div class="row">
            <div class="col">
                <div class="card">
                    <div class="card-body">
                        <h1 class="card-title">Serum Markets</h1>
                        <hr>
                        <p>
                        <table id="marketListings" class="table table-dark table-striped">
                            <thead>
                            <tr>
                                <th>
                                    Name
                                </th>
                                <th>
                                    ID
                                </th>
                                <th>
                                    Quote Deposits Notional
                                </th>
                            </tr>
                            </thead>
                            <tbody>
                            <tr th:each="market : ${marketListings}">
                                <td>
                                    <img loading="lazy" width="20" height="20" th:src="${market.baseIcon}"
                                         style="border-radius: 5px;">
                                    <img loading="lazy" width="20" height="20" th:src="${market.quoteIcon}"
                                         style="border-radius: 5px;">
                                    <span th:text="${market.name}"></span>
                                </td>
                                <td>
                                    <a th:href="@{'/' + ${market.id}}" th:text="${market.id}" target="_blank"></a>
                                </td>
                                <td><span th:value="${#numbers.formatCurrency(market.quoteNotional)}"
                                          th:text="${#numbers.formatCurrency(market.quoteNotional)}"></span></td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>
<script src="static/js/darkmode.min.js"></script>
<script type="text/javascript" th:inline="none" class="init">
    /*<![CDATA[*/
    $(document).ready(function () {
            $('#marketListings').DataTable({
                paging: true,
                //scrollY: 500,
                order: [[2, 'desc']]
            });
        }
    );
    /*]]>*/
</script>
</body>
</html>