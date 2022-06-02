<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://thymeleaf.org">
<label for="tokenSelect">Token: </label>
<select class="form-control" id="tokenSelect">
    <option th:each="token : ${tokens.values()}"
            th:value="${token.address}"
            th:text="${token.symbol} + ' (' + ${token.name} + ') (' + ${token.address} + ')'">
    </option>
</select>
<input type="button" value="Search for Markets">
<hr>
Markets:
<hr>
Market Details:
<hr>

</html>