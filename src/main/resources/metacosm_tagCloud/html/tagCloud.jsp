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
<%--@elvariable id="tagcloud" type="java.util.Map<String,TagCloud.Tag>--%>
<template:addResources type="css" resources="tagCloud.css"/>
<%-- Creates the tag cloud and put the resulting data in the tagcloud variable, target attribute specifies the name of the component on which the tag cloud will operate --%>
<tagcloud:tagcloud cloud="tagcloud" target="boundComponent"/>

<c:set var="targetId" value="${boundComponent.identifier}"/>

<div id="tagcloud${targetId}" class="tagcloud">

    <c:choose>
        <c:when test="${not empty tagcloud}">
            <c:forEach items="${tagcloud}" var="tag" varStatus="status">
                <span id="tag-${fn:replace(tag.key,' ','-')}" class="tag"><a href='${tag.value.actionURL}'>${fn:escapeXml(tag.key)} (${tag.value.cardinality})</a></span>
            </c:forEach>
        </c:when>
        <c:otherwise>
            <span class="notaggeditem${targetId}"><fmt:message key="label.tags.notag"/></span>
        </c:otherwise>
    </c:choose>
</div>
