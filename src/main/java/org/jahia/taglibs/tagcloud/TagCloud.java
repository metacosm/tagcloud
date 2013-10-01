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

import org.jahia.services.content.*;
import org.jahia.services.query.QOMBuilder;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.taglibs.uicomponents.Functions;

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

            return createTagCloudFromNodeIterator(boundComponent.getNodes(), minimumCardinalityForInclusion, maxNumberOfTags, false);
        }

        return Collections.emptyMap();
    }

    private static Map<String, Tag> createTagCloudFromQuery(JCRNodeWrapper boundComponent, JCRSessionWrapper session, int minimumCardinalityForInclusion, int maxNumberOfTags) throws RepositoryException {
        QueryObjectModelFactory factory = session.getWorkspace().getQueryManager().getQOMFactory();
        QOMBuilder qomBuilder = new QOMBuilder(factory, session.getValueFactory());

        qomBuilder.setSource(factory.selector("jnt:content", "tags"));
        qomBuilder.andConstraint(factory.descendantNode("tags", boundComponent.getPath()));
        qomBuilder.andConstraint(factory.propertyExistence("tags", "j:tags"));


        QueryObjectModel qom = qomBuilder.createQOM();
        qom.setLimit(maxNumberOfTags);

        QueryResultWrapper allTags = (QueryResultWrapper) qom.execute();

        return createTagCloudFromNodeIterator(allTags.getNodes(), minimumCardinalityForInclusion, maxNumberOfTags, true);
    }

    private static Map<String, Tag> createTagCloudFromNodeIterator(JCRNodeIteratorWrapper nodes, int minimumCardinalityForInclusion, int maxNumberOfTags, boolean hasTagsChecked) throws RepositoryException {
        final Map<String, Tag> tagCloud = new LinkedHashMap<String, Tag>(maxNumberOfTags);

        // map recording which tags have which cardinality, sorted in reverse cardinality order (most numerous tags first, being more important)
        SortedMap<Integer, Set<Tag>> tagCounts = new TreeMap<Integer, Set<Tag>>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o2.compareTo(o1);
            }
        });
        final Map<String, Integer> allTags = new HashMap<String, Integer>(maxNumberOfTags * 2);
        for (JCRNodeWrapper node : nodes) {
            if (hasTagsChecked || node.hasProperty("j:tags")) {
                JCRPropertyWrapper property = node.getProperty("j:tags");
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
        boolean stop = false;
        for (Set<Tag> tags : aboveMinimalCardinality.values()) {
            if (stop) {
                break;
            }

            for (Tag tag : tags) {
                if (tagCloud.size() < maxNumberOfTags) {
                    tagCloud.put(tag.name, tag);
                } else {
                    stop = true;
                    break;
                }
            }
        }

        return tagCloud;
    }

    public static class Tag {
        final int cardinality;
        final String name;
        final String uuid;
        final String type;

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
    }
}
