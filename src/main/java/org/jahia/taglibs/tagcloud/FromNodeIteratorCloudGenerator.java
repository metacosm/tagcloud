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
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.render.RenderContext;

import javax.jcr.RepositoryException;
import java.util.*;

/**
 * @author Christophe Laprun
 */
class FromNodeIteratorCloudGenerator implements CloudGenerator {
    JCRNodeIteratorWrapper nodes;
    boolean hasTagsChecked;
    final RenderContext context;

    FromNodeIteratorCloudGenerator(RenderContext context) {
        this.context = context;
    }

    FromNodeIteratorCloudGenerator(JCRNodeIteratorWrapper nodes, boolean hasTagsChecked, RenderContext context) {
        this.nodes = nodes;
        this.hasTagsChecked = hasTagsChecked;
        this.context = context;
    }

    @Override
    public Map<String, Tag> generateTagCloud(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, Map<String, List<KeyValue>> appliedFacets) throws RepositoryException {
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
