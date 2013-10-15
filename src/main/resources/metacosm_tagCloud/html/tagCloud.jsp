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
<template:addResources type="javascript" resources="jquery.tagcanvas.min.js"/>
<template:addResources type="javascript" resources="excanvas.js" condition="if lt IE 9"/>

<%-- Creates the tag cloud and put the resulting data in the tagcloud variable, target attribute specifies the name of the component on which the tag cloud will operate --%>
<tagcloud:tagcloud cloud="tagcloud" target="boundComponent"/>

<c:set var="targetId" value="${boundComponent.identifier}"/>

<div id="tagcloud${targetId}" class="tagcloud">

    <c:choose>
        <c:when test="${not empty tagcloud}">

            <%-- Cloud is generated using jQuery plugin available under LGPL 3 license at http://www.goat1000.com/tagcanvas.php --%>
            <template:addResources type="inlinejavascript" key="tagCloud">
                <script type="text/javascript">
                    $(document).ready(function () {
                        if (!$('#tagcloudCanvas${targetId}').tagcanvas({
                            outlineMethod: 'colour',
                            outlineColour: '#ccc',
                            weight: 'true', // use weighted mode to display tags
                            weightMode: 'both', // use both color and size to display weight
                            weightFrom: 'data-weight', // tagcanvas derives how tags are weighted based on the value of an attribute (put on anchor elements) specified by weightFrom option
                            maxSpeed: 0.03,
                            depth: 0.75
                        }, 'tags${targetId}')) { // this second parameter must be the id of the div containing the tags if they're not directly put within the canvas element
                            // TagCanvas failed to load
                            $('#tagcloudCanvasContainer${targetId}').hide();
                        }
                    });
                </script>
            </template:addResources>


            <%-- The tagcloud plugin uses the canvas contained in this div to display tags in 3D --%>
            <div id="tagcloudCanvasContainer${targetId}">
                <canvas width="280" height="200" id="tagcloudCanvas${targetId}">
                    <p>In Internet Explorer versions up to 8, things inside the canvas are inaccessible! Content will be replaced in browsers supporting canvas tag.</p>
                </canvas>
            </div>

            <%--
            Ideally, we would put the tags within the canvas element and they would be replaced by the plugin directly. However, this method doesn't work on IE < 9 so we need to put the tags in a separate div that will be hidden by the
            plugin. This is accomplished by passing this div's id to the tagcanvas function.
            --%>
            <div id="tags${targetId}">
                <ul>
                    <c:forEach items="${tagcloud}" var="tag" varStatus="status">
                        <li id="tag-${fn:replace(tag.key,' ','-')}" class="tag"><a href='${tag.value.actionURL}' data-weight='${tag.value.weight}'>${fn:escapeXml(tag.key)}</a></li>
                    </c:forEach>
                </ul>
            </div>


        </c:when>
        <c:otherwise>
            <span class="notaggeditem${targetId}"><fmt:message key="label.tags.notag"/></span>
        </c:otherwise>
    </c:choose>
</div>
