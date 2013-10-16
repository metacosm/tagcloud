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
import org.apache.solr.client.solrj.response.FacetField;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.query.QOMBuilder;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.taglibs.AbstractJahiaTag;
import org.jahia.taglibs.uicomponents.Functions;
import org.jahia.utils.Url;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.query.qom.QueryObjectModel;
import javax.jcr.query.qom.QueryObjectModelFactory;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import java.util.*;

/**
 * @author Christophe Laprun
 */
public class TagCloudTag extends AbstractJahiaTag {
    private static final String SELECTOR_NAME = "tags";
    private static final String TAGS_PROPERTY_NAME = "j:tags";

    private String cloudVar;
    private String target;

    public void setCloud(String cloud) {
        this.cloudVar = cloud;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public int doStartTag() throws JspException {
        try {
            final JCRNodeWrapper node = getCurrentResource().getNode();
            final RenderContext renderContext = getRenderContext();
            final JCRNodeWrapper boundComponent = Functions.getBoundComponent(node, renderContext, "j:bindedComponent");

            // generate tag cloud
            Map<String, Tag> cloud = Collections.emptyMap();
            if (boundComponent != null) {
                int minimumCardinalityForInclusion = Integer.parseInt(node.getPropertyAsString("j:usageThreshold"));
                int maxNumberOfTags = Integer.parseInt(node.getPropertyAsString("limit"));

                final String facetURLParameterName = getFacetURLParameterName(boundComponent.getName());
                final String currentQuery = Url.decodeUrlParam(renderContext.getRequest().getParameter(facetURLParameterName));

                cloud = generateTagCloud(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags, currentQuery, renderContext);
            }

            pageContext.setAttribute(cloudVar, cloud, PageContext.REQUEST_SCOPE);

            if (target != null && !target.isEmpty()) {
                pageContext.setAttribute(target, boundComponent, PageContext.REQUEST_SCOPE);
            }
        } catch (RepositoryException e) {
            throw new JspException(e);
        }

        return SKIP_BODY;
    }

    public Map<String, Tag> generateTagCloud(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, String currentQuery, RenderContext renderContext) throws RepositoryException {
        final Map<String, List<KeyValue>> appliedFacets = org.jahia.taglibs.facet.Functions.getAppliedFacetFilters(currentQuery);

        // retrieve all jmix:tagged nodes that descending from the bound component path
        final JCRSessionWrapper session = boundComponent.getSession();
        QueryObjectModelFactory factory = session.getWorkspace().getQueryManager().getQOMFactory();
        QOMBuilder qomBuilder = new QOMBuilder(factory, session.getValueFactory());
        qomBuilder.setSource(factory.selector("jmix:tagged", SELECTOR_NAME));
        qomBuilder.andConstraint(factory.descendantNode(SELECTOR_NAME, boundComponent.getPath()));

        // faceting on the TAGS_PROPERTY_NAME field with specified minimum cardinality
        qomBuilder.getColumns().add(factory.column(SELECTOR_NAME, TAGS_PROPERTY_NAME, "rep:facet(facet.mincount=" + minimumCardinalityForInclusion + "&key=1)"));

        // limiting the query to the specified maximum number of tags
        QueryObjectModel qom = qomBuilder.createQOM();
        qom.setLimit(maxNumberOfTags);

        QueryResultWrapper allTags = (QueryResultWrapper) qom.execute();

        // map recording which tags have which cardinality, sorted in reverse cardinality order (most numerous tags first, being more important)
        final SortedMap<Integer, Set<Tag>> tagCounts = new TreeMap<Integer, Set<Tag>>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o2.compareTo(o1);
            }
        });

        final FacetField tags = allTags.getFacetField(TAGS_PROPERTY_NAME);
        final List<FacetField.Count> values = tags.getValues();
        int totalCardinality = 0;
        for (FacetField.Count value : values) {
            // facet query should only return tags with a cardinality greater than the one we specified
            final int count = (int) value.getCount();
            // facets return value of the j:tags property which is a weak reference to a node so we need to load it to get its name
            final String tagUUID = value.getName();
            final JCRNodeWrapper tagNode = boundComponent.getSession().getNodeByUUID(tagUUID);
            final String name = tagNode.getDisplayableName();

            // use specific Tag subclass that adds facet filtering support
            final Tag tag = new Tag(name, count, tagUUID, value);

            // increase totalCardinality with the current tag's count, this is used to compute the tag's weight in the cloud
            totalCardinality += count;

            // add tag to tag counts
            Set<Tag> associatedTags = tagCounts.get(count);
            if (associatedTags == null) {
                associatedTags = new HashSet<Tag>();
                tagCounts.put(count, associatedTags);
            }
            associatedTags.add(tag);
        }
        Tag.setTotalCardinality(totalCardinality);

        final String url = renderContext.getURLGenerator().getMainResource();
        final Map<String, Tag> tagCloud = new LinkedHashMap<String, Tag>(maxNumberOfTags);
        boolean stop = false;
        for (Set<Tag> tags1 : tagCounts.values()) {
            if (stop) {
                break;
            }

            for (Tag tag : tags1) {
                if (tagCloud.size() < maxNumberOfTags) {
                    String result = url + "?" + getFacetURLParameterName(boundComponent.getName()) + "=" + Url.encodeUrlParam(org.jahia.taglibs.facet.Functions.getFacetDrillDownUrl(tag.getFacetValue(), currentQuery));
                    tag.setActionURL(result);
                    tagCloud.put(tag.getName(), tag);
                } else {
                    stop = true;
                    break;
                }
            }
        }
        return tagCloud;
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
}
