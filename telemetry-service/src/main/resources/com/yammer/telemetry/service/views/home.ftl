<#-- @ftlvariable name="" type="com.yammer.telemetry.service.views.TracingHomeView" -->
<html>
<head>
    <title>Traces</title>
</head>
<body>
<table>
    <tr>
        <th>ID</th>
        <th>Name</th>
    </tr>
<#list traces as trace>
    <tr>
        <td><a href="/tracing/${trace.id?c?url('utf-8')}">${trace.id}</a></td>
        <td>${trace.root.name}</td>
    </tr>
</#list>
</table>
</body>
</html>