<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="uiComponents" uri="http://www.jahia.org/tags/uiComponentsLib" %>
<%@ taglib prefix="query" uri="http://www.jahia.org/tags/queryLib" %>
<%@ taglib prefix="tagcloud" uri="http://jahia.org/tags/tagcloud" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="acl" type="java.lang.String"--%>
<%--@elvariable id="tagcloud" type="java.util.Map<String,Integer>--%>
<template:addResources type="css" resources="tagCloud.css"/>

<c:set var="boundComponent" value="${uiComponents:getBoundComponent(currentNode, renderContext, 'j:bindedComponent')}"/>

<div id="tagcloud${boundComponent.identifier}" class="tagcloud">
    <c:set var="tagcloud" value="${tagcloud:getCloud(currentNode, renderContext)}" scope="request"/>

    <c:url var="postUrl" value="${url.base}${boundComponent.path}"/>
    <script type="text/javascript">
        function filterBoundComponentContent(tag) {
            $.post("${postUrl}.filterFromTag.do", {"tag": tag}, function (result) {
            }, "json");
            return false;
        }
    </script>

    <c:choose>
        <c:when test="${not empty tagcloud}">
            <c:forEach items="${tagcloud}" var="tag" varStatus="status">
                <span id="tag-${fn:replace(tag.key,' ','-')}" class="tag"><a href="#"
                                                                             onclick="filterBoundComponentContent('${tag.key}')">${fn:escapeXml(tag.key)}
                    (${tag.value})</a></span>
            </c:forEach>
        </c:when>
        <c:otherwise>
            <span class="notaggeditem${boundComponent.identifier}"><fmt:message key="label.tags.notag"/></span>
        </c:otherwise>
    </c:choose>
</div>
