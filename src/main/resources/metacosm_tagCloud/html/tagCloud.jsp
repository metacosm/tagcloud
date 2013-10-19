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
<%--@elvariable id="tagcloud" type="java.util.Map<String,Tag>--%>
<%--@elvariable id="applied" type="java.util.List<Tag>--%>
<template:addResources type="css" resources="tagCloud.css"/>

<%-- Creates the tag cloud and put the resulting data in the tagcloud variable, applied attribute contains the list of applied tags, target attribute specifies the name of the component on which the tag cloud will operate --%>
<tagcloud:tagcloud cloud="tagcloud" appliedTags="applied" target="boundComponent"/>

<c:choose>
    <c:when test="${use3D}">
        <template:addResources type="javascript" resources="jquery.tagcanvas.min.js"/>
        <template:addResources type="javascript" resources="excanvas.js" condition="if lt IE 9"/>
    </c:when>
    <c:otherwise>
        <template:addResources type="javascript" resources="jquery.tagcloud.js"/>
    </c:otherwise>
</c:choose>

<c:set var="targetId" value="${boundComponent.identifier}"/>

<div id="tagcloud${targetId}" class="tagcloud">

    <c:if test="${not empty applied}">
        <div class="activeTagsContainer">
            <p><fmt:message key="label.tagcloud.active"/></p>
            <ul class="activeTags">
                <c:forEach items="${applied}" var="tag">
                    <li><a href="${tag.deleteActionURL}" title='<fmt:message key="label.tagcloud.remove"><fmt:param value="${tag.name}"/></fmt:message>'>${tag.name}</a></li>
                </c:forEach>
            </ul>
        </div>
    </c:if>

    <c:choose>
        <c:when test="${not empty tagcloud}">

            <template:addResources type="inlinejavascript" key="tagCloud">
                <c:choose>
                    <c:when test="${use3D}">
                        <%-- 3D Cloud is generated using jQuery plugin available under LGPL 3 license at http://www.goat1000.com/tagcanvas.php --%>
                        <script type="text/javascript">
                            $(document).ready(function () {
                                if (!$('#tagcloudCanvas${targetId}').tagcanvas({
                                    outlineMethod: 'colour',
                                    outlineColour: '${highlightColor}', // tagcloud tag retrieves this value from the node configuration and puts it in the page context
                                    weight: 'true', // use weighted mode to display tags
                                    weightMode: 'size', // use both color and size to display weight
                                    weightFrom: 'rel', // tagcanvas derives how tags are weighted based on the value of an attribute (put on anchor elements) specified by weightFrom option
                                    weightSize: 1.25,
                                    maxSpeed: 0.03,
                                    depth: 0.75,
                                    shadowBlur: 2,
                                    shadowOffset: [1, 2]
                                }, 'tags${targetId}')) { // this second parameter must be the id of the div containing the tags if they're not directly put within the canvas element
                                    // TagCanvas failed to load
                                    $('#tagcloudCanvasContainer${targetId}').hide();
                                }
                            });
                        </script>
                    </c:when>
                    <c:otherwise>
                        <script type="text/javascript">
                            $.fn.tagcloud.defaults = {
                                size: {start: 14, end: 18, unit: 'pt'},
                                color: {start: '#cde', end: '#f52'}
                            };

                            $(function () {
                                $('#tags${targetId} a').tagcloud();
                            });
                        </script>
                    </c:otherwise>
                </c:choose>
            </template:addResources>

            <c:if test="${use3D}">
                <%-- The tagcloud plugin uses the canvas contained in this div to display tags in 3D --%>
                <div id="tagcloudCanvasContainer${targetId}">
                    <canvas width="280" height="200" id="tagcloudCanvas${targetId}">
                        <p>In Internet Explorer versions up to 8, things inside the canvas are inaccessible! Content will be replaced in browsers supporting canvas tag.</p>
                    </canvas>
                </div>
            </c:if>

            <%--
            Ideally, we would put the tags within the canvas element and they would be replaced by the plugin directly. However, this method doesn't work on IE < 9 so we need to put the tags in a separate div that will be hidden by the
            plugin. This is accomplished by passing this div's id to the tagcanvas function.
            --%>
            <div id="tags${targetId}">
                <c:forEach items="${tagcloud}" var="tag">
                    <a href='${tag.value.actionURL}' rel="${tag.value.weight}">${fn:escapeXml(tag.key)}</a>
                </c:forEach>
            </div>


        </c:when>
        <c:otherwise>
            <span class="notaggeditem${targetId}"><fmt:message key="label.tagcloud.notag"/></span>
        </c:otherwise>
    </c:choose>
</div>
