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
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.query.QOMBuilder;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;

import javax.jcr.RepositoryException;
import javax.jcr.query.qom.QueryObjectModel;
import javax.jcr.query.qom.QueryObjectModelFactory;
import java.util.List;
import java.util.Map;

/**
 * @author Christophe Laprun
 */
class FromQueryCloudGenerator extends FromNodeIteratorCloudGenerator {

    public static final String SELECTOR_NAME = "tags";

    FromQueryCloudGenerator(RenderContext context) {
        super(context);
        hasTagsChecked = true;
    }

    @Override
    public Map<String, Tag> generateTagCloud(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, Map<String, List<KeyValue>> appliedFacets) throws RepositoryException {
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
        return generateCloudFrom(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags, allTags, appliedFacets);
    }

    protected Map<String, Tag> generateCloudFrom(JCRNodeWrapper boundComponent, int minimumCardinalityForInclusion, int maxNumberOfTags, QueryResultWrapper allTags, Map<String, List<KeyValue>> appliedFacets) throws RepositoryException {
        // define on which nodes super's implementation will operate
        nodes = allTags.getNodes();

        return super.generateTagCloud(boundComponent, minimumCardinalityForInclusion, maxNumberOfTags, appliedFacets);
    }

    protected void refineQuery(QueryObjectModelFactory factory, QOMBuilder qomBuilder, int minimunCardinalityForInclusion) throws RepositoryException {
        qomBuilder.andConstraint(factory.propertyExistence(SELECTOR_NAME, TAGS_PROPERTY_NAME));
    }
}
