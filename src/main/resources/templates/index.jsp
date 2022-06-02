<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
Thymeleaf message: <span th:text="${testVal}"></span><br>
<label for="tokenSelect">Token: </label>
<select class="form-control" id="tokenSelect">
    <option th:each="token : ${tokens.values()}" th:value="${token.address}" th:text="${token.name} + ' (' + ${token.symbol} + ')'"></option>
</select>
<br>
Serum
<hr>
Tokens:
<hr>
Markets:
<hr>
Market Details:
<hr>

</html>