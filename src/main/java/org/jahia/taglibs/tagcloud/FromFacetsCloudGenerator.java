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
package org.jahia.taglibs.tagcloud;

import org.apache.commons.collections.KeyValue;
import org.apache.solr.client.solrj.response.FacetField;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.query.QOMBuilder;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.taglibs.facet.Functions;
import org.jahia.utils.Url;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.query.qom.QueryObjectModelFactory;
import java.util.*;

/**
 * @author Christophe Laprun
 */
class FromFacetsCloudGenerator extends FromQueryCloudGenerator {
    private final String currentQuery;

    FromFacetsCloudGenerator(RenderContext context, String currentQuery) {
        super(context);
        this.currentQuery = currentQuery;
    }

    @Override
    protected void refineQuery(QueryObjectModelFactory factory, QOMBuilder qomBuilder, int minimunCardinalityForInclusion) throws RepositoryException {
        qomBuilder.getColumns().add(factory.column(SELECTOR_NAME, TAGS_PROPERTY_NAME, "rep:facet(facet.mincount=" + minimunCardinalityForInclusion + "&key=1)"));
    }

    @Override
    protected Map<String, Tag> generateCloudFrom(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, QueryResultWrapper allTags, Map<String, List<KeyValue>> appliedFacets) throws RepositoryException {
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
            final Tag tag = new Tag(name, count, tagUUID, PropertyType.WEAKREFERENCE);

            // increase totalCardinality with the current tag's count, this is used to compute the tag's weight in the cloud
            totalCardinality += count;

            // facet filtering
            tag.setFacetValue(value);

            // add tag to tag counts
            Set<Tag> associatedTags = tagCounts.get(count);
            if (associatedTags == null) {
                associatedTags = new HashSet<Tag>();
                tagCounts.put(count, associatedTags);
            }
            associatedTags.add(tag);
        }
        Tag.setTotalCardinality(totalCardinality);

        return keepOnlyMostNumerousTagsUpToMaxNumber(boundComponent, maxNumberOfTags, tagCounts);
    }

    @Override
    public String generateActionURL(JCRNodeWrapper boundComponent, Tag tag, RenderContext context) throws RepositoryException {
        final String url = context.getURLGenerator().getMainResource();

        return url + "?" + TagCloud.getFacetURLParameterName(boundComponent.getName()) + "=" + Url.encodeUrlParam(Functions.getFacetDrillDownUrl(tag.getFacetValue(), currentQuery));
    }
}
