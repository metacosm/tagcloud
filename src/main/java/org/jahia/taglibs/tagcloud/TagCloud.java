/*
* JBoss, a division of Red Hat
* Copyright 2012, Red Hat Middleware, LLC, and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.jahia.taglibs.tagcloud;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.query.QOMBuilder;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.taglibs.uicomponents.Functions;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.qom.QueryObjectModel;
import javax.jcr.query.qom.QueryObjectModelFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:chris.laprun@jboss.com">Chris Laprun</a>
 */
public class TagCloud {
    public static Map<String, Integer> getCloud(JCRNodeWrapper currentNode, RenderContext renderContext) throws RepositoryException {
        final JCRNodeWrapper boundComponent = Functions.getBoundComponent(currentNode, renderContext, "j:bindedComponent");
        final JCRSessionWrapper session = currentNode.getSession();

        int minimumCardinalityForInclusion = Integer.parseInt(currentNode.getPropertyAsString("j:usageThreshold"));
        int maxNumberOfTags = Integer.parseInt(currentNode.getPropertyAsString("limit"));

        return createTagCloudFromQuery(boundComponent, session, minimumCardinalityForInclusion, maxNumberOfTags);
    }


    private static Map<String, Integer> createTagCloudFromQuery(JCRNodeWrapper boundComponent, JCRSessionWrapper session, int minimumCardinalityForInclusion, int maxNumberOfTags) throws RepositoryException {
        QueryObjectModelFactory factory = session.getWorkspace().getQueryManager().getQOMFactory();
        QOMBuilder qomBuilder = new QOMBuilder(factory, session.getValueFactory());

        qomBuilder.setSource(factory.selector("jnt:content", "tags"));
        qomBuilder.andConstraint(factory.descendantNode("tags", boundComponent.getPath()));
        qomBuilder.andConstraint(factory.propertyExistence("tags", "j:tags"));


        QueryObjectModel qom = qomBuilder.createQOM();
        qom.setLimit(maxNumberOfTags);

        QueryResultWrapper allTags = (QueryResultWrapper) qom.execute();

        return createTagCloudFromQueryResult(allTags);
    }

    private static Map<String, Integer> createTagCloudFromQueryResult(QueryResultWrapper allTags) throws RepositoryException {
        NodeIterator nodes = allTags.getNodes();
        final long size = nodes.getSize();
        Map<String, Integer> tagCloud = new HashMap<String, Integer>((int) size);
        while (nodes.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
            JCRPropertyWrapper property = node.getProperty("j:tags");
            final Value[] values = property.getRealValues();
            for (Value value : values) {
                final String tag = ((JCRValueWrapper) value).getNode().getDisplayableName();
                Integer cardinality = tagCloud.get(tag);
                if (cardinality == null) {
                    cardinality = 0;
                }
                tagCloud.put(tag, cardinality + 1);
            }
        }

        return tagCloud;
    }

    private static Map<String, Integer> createTagCloudFromQueryResultNewAPI(QueryResultWrapper allTags) throws RepositoryException {
        /*Map<String, Integer> tagCloud = new HashMap<String, Integer>();
        for (JCRNodeWrapper node : allTags.getWrappedNodes()) {
            final JCRPropertyWrapper property = node.getProperty("j:tags");
            final JCRValueWrapper[] values = property.getRealValues();
            for (JCRValueWrapper value : values) {
                final String tag = value.getNode().getDisplayableName();
                Integer cardinality = tagCloud.get(tag);
                if (cardinality == null) {
                    cardinality = 0;
                }
                tagCloud.put(tag, cardinality + 1);
            }
        }*/

        return Collections.emptyMap();
    }

}
