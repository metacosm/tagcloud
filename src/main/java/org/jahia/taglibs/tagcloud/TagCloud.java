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

import org.apache.solr.client.solrj.response.FacetField;
import org.jahia.services.content.*;
import org.jahia.services.query.QOMBuilder;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.taglibs.uicomponents.Functions;
import org.jahia.utils.Url;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.query.qom.QueryObjectModel;
import javax.jcr.query.qom.QueryObjectModelFactory;
import java.util.*;

/**
 * @author Christophe Laprun
 */
public class TagCloud {
    public static Map<String, Tag> getCloud(JCRNodeWrapper currentNode, RenderContext renderContext) throws RepositoryException {
        final JCRNodeWrapper boundComponent = Functions.getBoundComponent(currentNode, renderContext, "j:bindedComponent");
        if (boundComponent != null) {
            int minimumCardinalityForInclusion = Integer.parseInt(currentNode.getPropertyAsString("j:usageThreshold"));
            int maxNumberOfTags = Integer.parseInt(currentNode.getPropertyAsString("limit"));

            final CloudGenerator generator = new FromFacetsCloudGenerator(renderContext);
            return generator.generateTagCloud(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags);
        }

        return Collections.emptyMap();
    }

    public static class Tag {
        final int cardinality;
        final String name;
        final String uuid;
        final String type;
        String actionURL;
        private FacetField.Count facetValue;

        public Tag(String name, int cardinality, String uuid, int type) {
            this.name = name;
            this.cardinality = cardinality;
            this.uuid = uuid;
            this.type = PropertyType.nameFromValue(type);
        }

        public String getUuid() {
            return uuid;
        }

        public int getCardinality() {
            return cardinality;
        }

        public String getType() {
            return type;
        }

        public String getActionURL() {
            return actionURL;
        }

        public void setActionURL(String actionURL) {
            this.actionURL = actionURL;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Tag tag = (Tag) o;

            if (cardinality != tag.cardinality) return false;
            if (!name.equals(tag.name)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = cardinality;
            result = 31 * result + name.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Tag (" + name + ',' + cardinality + ')';
        }

        public void setFacetValue(FacetField.Count facetValue) {
            this.facetValue = facetValue;
        }

        public FacetField.Count getFacetValue() {
            return facetValue;
        }
    }

    private static interface CloudGenerator {
        String TAGS_PROPERTY_NAME = "j:tags";

        Map<String, Tag> generateTagCloud(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags) throws RepositoryException;

        String generateActionURL(JCRNodeWrapper boundComponent, Tag tag, RenderContext context) throws RepositoryException;
    }

    private static class FromFacetsCloudGenerator extends FromQueryCloudGenerator {
        private FromFacetsCloudGenerator(RenderContext context) {
            super(context);
        }

        @Override
        protected void refineQuery(QueryObjectModelFactory factory, QOMBuilder qomBuilder, int minimunCardinalityForInclusion) throws RepositoryException {
            qomBuilder.getColumns().add(factory.column(SELECTOR_NAME, TAGS_PROPERTY_NAME, "rep:facet(facet.mincount=" + minimunCardinalityForInclusion + "&key=1)"));
        }

        @Override
        protected Map<String, Tag> generateCloudFrom(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, QueryResultWrapper allTags) throws RepositoryException {
            // map recording which tags have which cardinality, sorted in reverse cardinality order (most numerous tags first, being more important)
            final SortedMap<Integer, Set<Tag>> tagCounts = new TreeMap<Integer, Set<Tag>>(new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return o2.compareTo(o1);
                }
            });

            final FacetField tags = allTags.getFacetField(TAGS_PROPERTY_NAME);
            final List<FacetField.Count> values = tags.getValues();
            for (FacetField.Count value : values) {
                // facet query should only return tags with a cardinality greater than the one we specified
                final int count = (int) value.getCount();
                // facets return value of the j:tags property which is a weak reference to a node so we need to load it to get its name
                final String tagUUID = value.getName();
                final JCRNodeWrapper tagNode = boundComponent.getSession().getNodeByUUID(tagUUID);
                final String name = tagNode.getDisplayableName();
                final Tag tag = new Tag(name, count, tagUUID, PropertyType.WEAKREFERENCE);

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

            return keepOnlyMostNumerousTagsUpToMaxNumber(boundComponent, maxNumberOfTags, tagCounts);
        }

        @Override
        public String generateActionURL(JCRNodeWrapper boundComponent, Tag tag, RenderContext context) throws RepositoryException {
            final String url = context.getURLGenerator().getMainResource();

            return url + "?N-" + boundComponent.getName() + "=" + Url.encodeUrlParam(org.jahia.taglibs.facet.Functions.getFacetDrillDownUrl(tag.getFacetValue(), ""));
        }
    }

    private static class FromQueryCloudGenerator extends FromNodeIteratorCloudGenerator {

        public static final String SELECTOR_NAME = "tags";

        private FromQueryCloudGenerator(RenderContext context) {
            super(context);
            hasTagsChecked = true;
        }

        @Override
        public Map<String, Tag> generateTagCloud(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags) throws RepositoryException {
            final JCRSessionWrapper session = boundComponent.getSession();
            QueryObjectModelFactory factory = session.getWorkspace().getQueryManager().getQOMFactory();
            QOMBuilder qomBuilder = new QOMBuilder(factory, session.getValueFactory());

            qomBuilder.setSource(factory.selector("jmix:tagged", SELECTOR_NAME));
            qomBuilder.andConstraint(factory.descendantNode(SELECTOR_NAME, boundComponent.getPath()));

            // give subclasses the opportunity to refine the query
            refineQuery(factory, qomBuilder, minimumCardinalityForInclusion);

            QueryObjectModel qom = qomBuilder.createQOM();
            qom.setLimit(maxNumberOfTags);

            QueryResultWrapper allTags = (QueryResultWrapper) qom.execute();
            return generateCloudFrom(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags, allTags);
        }

        protected Map<String, Tag> generateCloudFrom(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, QueryResultWrapper allTags) throws RepositoryException {
            // define on which nodes super's implementation will operate
            nodes = allTags.getNodes();

            return super.generateTagCloud(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags);
        }

        protected void refineQuery(QueryObjectModelFactory factory, QOMBuilder qomBuilder, int minimunCardinalityForInclusion) throws RepositoryException {
            qomBuilder.andConstraint(factory.propertyExistence(SELECTOR_NAME, TAGS_PROPERTY_NAME));
        }
    }

    private static class FromNodeIteratorCloudGenerator implements CloudGenerator {
        JCRNodeIteratorWrapper nodes;
        boolean hasTagsChecked;
        final RenderContext context;

        private FromNodeIteratorCloudGenerator(RenderContext context) {
            this.context = context;
        }

        private FromNodeIteratorCloudGenerator(JCRNodeIteratorWrapper nodes, boolean hasTagsChecked, RenderContext context) {
            this.nodes = nodes;
            this.hasTagsChecked = hasTagsChecked;
            this.context = context;
        }

        @Override
        public Map<String, Tag> generateTagCloud(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags) throws RepositoryException {
            // map recording which tags have which cardinality, sorted in reverse cardinality order (most numerous tags first, being more important)
            SortedMap<Integer, Set<Tag>> tagCounts = new TreeMap<Integer, Set<Tag>>(new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return o2.compareTo(o1);
                }
            });

            final Map<String, Integer> allTags = new HashMap<String, Integer>(maxNumberOfTags * 2);
            for (JCRNodeWrapper node : nodes) {
                if (hasTagsChecked || node.hasProperty(TAGS_PROPERTY_NAME)) {
                    JCRPropertyWrapper property = node.getProperty(TAGS_PROPERTY_NAME);
                    if (property != null) {
                        final int type = property.getType();

                        final JCRValueWrapper[] values = property.getValues();
                        for (JCRValueWrapper value : values) {
                            final JCRNodeWrapper tagNode = value.getNode();
                            final String name = tagNode.getDisplayableName();
                            final String identifier = tagNode.getIdentifier();

                            // record tag name and cardinality
                            Integer cardinality = allTags.get(name);
                            if (cardinality == null) {
                                cardinality = 0;
                            }
                            cardinality++;
                            allTags.put(name, cardinality);

                            // add tag to tag counts
                            Set<Tag> associatedTags = tagCounts.get(cardinality);
                            if (associatedTags == null) {
                                associatedTags = new HashSet<Tag>();
                                tagCounts.put(cardinality, associatedTags);
                            }

                            // remove tag from cardinality - 1 since its count just increased
                            final Set<Tag> previous = tagCounts.get(cardinality - 1);
                            if (previous != null) {
                                previous.remove(new Tag(name, cardinality - 1, identifier, type));
                            }
                            // add tag to cardinality set
                            associatedTags.add(new Tag(name, cardinality, identifier, type));
                        }
                    }
                }
            }

            // create the tag cloud, only including maxNumberOfTags tags, with only the most numerous tags
            // first only consider tags with more than the minimal cardinality
            final SortedMap<Integer, Set<Tag>> aboveMinimalCardinality = tagCounts.headMap(minimumCardinalityForInclusion - 1);
            return keepOnlyMostNumerousTagsUpToMaxNumber(boundComponent, maxNumberOfTags, aboveMinimalCardinality);
        }

        protected Map<String, Tag> keepOnlyMostNumerousTagsUpToMaxNumber(JCRNodeWrapper boundComponent, int maxNumberOfTags, SortedMap<Integer, Set<Tag>> aboveMinimalCardinality) throws RepositoryException {
            final Map<String, Tag> tagCloud = new LinkedHashMap<String, Tag>(maxNumberOfTags);
            boolean stop = false;
            for (Set<Tag> tags : aboveMinimalCardinality.values()) {
                if (stop) {
                    break;
                }

                for (Tag tag : tags) {
                    if (tagCloud.size() < maxNumberOfTags) {
                        tag.setActionURL(generateActionURL(boundComponent, tag, context));
                        tagCloud.put(tag.name, tag);
                    } else {
                        stop = true;
                        break;
                    }
                }
            }
            return tagCloud;
        }

        @Override
        public String generateActionURL(JCRNodeWrapper boundComponent, Tag tag, RenderContext context) throws RepositoryException {
            final String url = context.getURLGenerator().getMainResource();
            final String filter = "?filter={name=\"j:tags\",value:\"" + tag.getUuid() + "\",op:\"eq\",uuid:\"" + boundComponent.getIdentifier() + "\",type:\"" + tag.getType() + "\"}";
            return url + filter;
        }
    }
}
