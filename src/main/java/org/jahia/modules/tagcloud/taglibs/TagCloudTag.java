/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2013 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.tagcloud.taglibs;

import org.apache.commons.collections.KeyValue;
import org.apache.jackrabbit.util.Text;
import org.apache.solr.client.solrj.response.FacetField;
import org.jahia.api.Constants;
import org.jahia.modules.tagcloud.model.Tag;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.query.QOMBuilder;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.taglibs.AbstractJahiaTag;
import org.jahia.taglibs.facet.Functions;
import org.jahia.taglibs.template.include.OptionTag;
import org.jahia.utils.Url;

import javax.jcr.RepositoryException;
import javax.jcr.query.qom.QueryObjectModel;
import javax.jcr.query.qom.QueryObjectModelFactory;
import javax.jcr.query.qom.Selector;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import java.util.*;

/**
 * @author Christophe Laprun
 */
public class TagCloudTag extends AbstractJahiaTag {
    private String cloudVar;
    private String target;
    private String appliedTags;

    public void setCloud(String cloud) {
        this.cloudVar = cloud;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void setAppliedTags(String appliedTags) {
        this.appliedTags = appliedTags;
    }

    @Override
    public int doStartTag() throws JspException {
        try {
            final JCRNodeWrapper node = getCurrentResource().getNode();
            final RenderContext renderContext = getRenderContext();
            final JCRNodeWrapper boundComponent = org.jahia.taglibs.uicomponents.Functions.getBoundComponent(node, renderContext, "j:bindedComponent");

            if (boundComponent != null) {

                // we need to render the hidden.load view of the bound component to make sure that all elements are loaded, this is, in particular, needed when the bound component is a jnt:query component
                // this is equivalent to using in the JSP:
                // <template:option node="${boundComponent}" nodetype="${boundComponent.primaryNodeTypeName},jmix:list" view="hidden.load">
                //    <template:param name="queryLoadAllUnsorted" value="true"/>
                // </template:option>
                OptionTag.renderNodeWithViewAndTypes(boundComponent, "hidden.load", boundComponent.getPrimaryNodeTypeName() + ",jmix:list", pageContext, Collections.singletonMap("queryLoadAllUnsorted", "true"));

                // get component configuration
                int minimumCardinalityForInclusion = Integer.parseInt(node.getPropertyAsString("minInclusionCardinality"));
                int maxNumberOfTags = Integer.parseInt(node.getPropertyAsString("maxTagNumber"));

                // extract URL parameters
                final String facetURLParameterName = getFacetURLParameterName(boundComponent.getName());
                final String currentQuery = Url.decodeUrlParam(renderContext.getRequest().getParameter(facetURLParameterName));

                // generate cloud and applied facet list
                generateTagCloud(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags, currentQuery, renderContext);
            }

            if (target != null && !target.isEmpty()) {
                pageContext.setAttribute(target, boundComponent, PageContext.REQUEST_SCOPE);
            }
        } catch (Exception e) {
            throw new JspException(e);
        }

        return SKIP_BODY;
    }

    public void generateTagCloud(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, String currentQuery, RenderContext renderContext) throws RepositoryException {

        // applied facets
        final Map<String, List<KeyValue>> appliedFacets = Functions.getAppliedFacetFilters(currentQuery);

        // query
        QueryResultWrapper filteredTags = getNodesWithFacets(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags, appliedFacets);

        if (!filteredTags.isFacetResultsEmpty()) {
            // map recording which unapplied tags have which cardinality, sorted in reverse cardinality order (most numerous tags first, being more important)
            final NavigableMap<Integer, Set<Tag>> tagCounts = new TreeMap<Integer, Set<Tag>>();
            // applied tags facets
            final List<KeyValue> appliedTagsValues = appliedFacets.get(Constants.TAGS);
            Map.Entry<String, List<KeyValue>> appliedTagsFacets = null;
            // list of applied tags
            List<Tag> appliedTagsList = Collections.emptyList();
            if (appliedTagsValues != null) {
                appliedTagsFacets = new StringListEntry(Constants.TAGS, appliedTagsValues);
                appliedTagsList = new ArrayList<Tag>(appliedTagsValues.size());
            }

            // action URL start
            final String facetURLParameterName = getFacetURLParameterName(boundComponent.getName());
            final String url = renderContext.getURLGenerator().getMainResource();
            final String actionURLStart = url + "?" + facetURLParameterName + "=";

            // process the query results
            final FacetField tags = filteredTags.getFacetField(Constants.TAGS);
            final List<FacetField.Count> values = tags.getValues();
            int totalCardinality = 0;
            for (FacetField.Count value : values) {
                // facet query should only return tags with a cardinality greater than the one we specified
                final int count = (int) value.getCount();

                // facets return value of the j:tags property which is a weak reference to a node so we need to load it to get its name
                final String tagUUID = value.getName();
                final JCRNodeWrapper tagNode = boundComponent.getSession().getNodeByUUID(tagUUID);
                final String name = tagNode.getDisplayableName();

                // create tag
                final Tag tag = new Tag(name, count, tagUUID, value);

                if (!Functions.isFacetValueApplied(value, appliedFacets)) {
                    // only add tag to cloud if it's not applied

                    // increase totalCardinality with the current tag's count, this is used to compute the tag's weight in the cloud
                    totalCardinality += count;

                    // add tag to tag counts
                    Set<Tag> associatedTags = tagCounts.get(count);
                    if (associatedTags == null) {
                        associatedTags = new HashSet<Tag>();
                        tagCounts.put(count, associatedTags);
                    }
                    associatedTags.add(tag);
                } else {
                    // get KeyValue for current tag
                    KeyValue current = null;
                    for (KeyValue tagsValue : appliedTagsValues) {
                        if (tagUUID.equals(tagsValue.getKey())) {
                            current = tagsValue;
                        }
                    }

                    tag.setDeleteActionURL(getActionURL(actionURLStart, Functions.getDeleteFacetUrl(appliedTagsFacets, current, currentQuery)));
                    appliedTagsList.add(tag);
                }
            }
            Tag.setTotalCardinality(totalCardinality);

            // extract only the maxNumberOfTags most numerous tags
            final Map<String, Tag> tagCloud = new LinkedHashMap<String, Tag>(maxNumberOfTags);
            boolean stop = false;
            for (Set<Tag> tags1 : tagCounts.descendingMap().values()) {
                if (stop) {
                    break;
                }

                for (Tag tag : tags1) {
                    if (tagCloud.size() < maxNumberOfTags) {
                        String result = getActionURL(actionURLStart, Functions.getFacetDrillDownUrl(tag.getFacetValue(), currentQuery));
                        tag.setActionURL(result);
                        tagCloud.put(tag.getName(), tag);
                    } else {
                        stop = true;
                        break;
                    }
                }
            }

            // put cloud and applied tags in their respective page context variables
            pageContext.setAttribute(cloudVar, tagCloud, PageContext.REQUEST_SCOPE);
            pageContext.setAttribute(appliedTags, appliedTagsList, PageContext.REQUEST_SCOPE);
        }
    }

    private String getActionURL(String start, String paramValue) {
        return start + Url.encodeUrlParam(paramValue);
    }

    private QueryResultWrapper getNodesWithFacets(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, Map<String, List<KeyValue>> appliedFacets) throws RepositoryException {
        // retrieve all jmix:tagged nodes that descending from the bound component path
        final JCRSessionWrapper session = boundComponent.getSession();
        QueryObjectModelFactory factory = session.getWorkspace().getQueryManager().getQOMFactory();

        QOMBuilder qomBuilder = new QOMBuilder(factory, session.getValueFactory());
        boolean hadExisting = false;
        String selectorName = Constants.JAHIAMIX_TAGGED;

        final int scope = pageContext.getAttributesScope("moduleMap");
        if (scope != 0) {
            final Map moduleMap = (Map) pageContext.getAttribute("moduleMap", scope);
            if (moduleMap != null) {
                final Object listQuery = moduleMap.get("listQuery");
                if (listQuery instanceof QueryObjectModel) {
                    QueryObjectModel existing = (QueryObjectModel) listQuery;
                    final Selector selector = (Selector) existing.getSource();
                    selectorName = selector.getSelectorName();
                    qomBuilder.setSource(selector);
                    qomBuilder.andConstraint(existing.getConstraint());
                    hadExisting = true;
                }
            }
        }

        if (!hadExisting) {
            qomBuilder.setSource(factory.selector(Constants.JAHIAMIX_TAGGED, selectorName));
            qomBuilder.andConstraint(factory.descendantNode(selectorName, boundComponent.getPath()));
        }

        // faceting on the TAGS_PROPERTY_NAME field with specified minimum cardinality
        qomBuilder.getColumns().add(factory.column(selectorName, Constants.TAGS, "rep:facet(facet.nodetype=" + Constants.JAHIAMIX_TAGGED + "&facet.mincount=" + minimumCardinalityForInclusion + "&key=1)"));

        // repeat applied facets
        if (appliedFacets != null) {
            for (Map.Entry<String, List<KeyValue>> appliedFacet : appliedFacets.entrySet()) {
                for (KeyValue keyValue : appliedFacet.getValue()) {
                    final String propertyName = "rep:filter(" + Text.escapeIllegalJcrChars(appliedFacet.getKey()) + ")";
                    qomBuilder.andConstraint(factory.fullTextSearch(selectorName, propertyName, factory.literal(qomBuilder.getValueFactory().createValue(keyValue.getValue().toString()))));
                }
            }
        }

        // limiting the query to the specified maximum number of tags
        QueryObjectModel qom = qomBuilder.createQOM();
        qom.setLimit(maxNumberOfTags);
        return (QueryResultWrapper) qom.execute();
    }


    /**
     * Isolate parameter name in a single spot
     *
     * @param targetName
     * @return
     */
    static String getFacetURLParameterName(String targetName) {
        return "N-" + targetName;
    }

    private static class StringListEntry implements Map.Entry<String, List<KeyValue>> {
        private String key;
        private List<KeyValue> value;

        public StringListEntry(String key, List<KeyValue> value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public List<KeyValue> getValue() {
            return value;
        }

        @Override
        public List<KeyValue> setValue(List<KeyValue> value) {
            throw new UnsupportedOperationException();
        }
    }
}
