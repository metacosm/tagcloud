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
package org.jahia.modules.tagcloud.conditions;

import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.visibility.BaseVisibilityConditionRule;
import org.jahia.services.visibility.VisibilityService;
import org.jahia.utils.i18n.Messages;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.Locale;

/**
 * @author Christophe Laprun
 */
public class TagVisibilityCondition extends BaseVisibilityConditionRule {

    @Override
    public boolean matches(JCRNodeWrapper node) {
        try {
            if (node.hasProperty("j:tags") && node.hasNode(VisibilityService.NODE_NAME)) {
                JCRPropertyWrapper property = node.getProperty("j:tags");
                final JCRNodeWrapper conditions = node.getNode(VisibilityService.NODE_NAME);
                final String tagName = conditions.getPropertyAsString("tag");

                if (property != null) {
                    final Value[] values = property.getRealValues();
                    for (Value value : values) {
                        final String name = ((JCRValueWrapper) value).getNode().getDisplayableName();
                        if (tagName.equals(name)) {
                            return false;
                        }
                    }
                }
            }
        } catch (RepositoryException e) {
        }

        return true;
    }

    @Override
    public String getGWTDisplayTemplate(Locale locale) {
        return Messages.get(ServicesRegistry.getInstance().getJahiaTemplateManagerService().getTemplatePackage("Tagcloud module"), "label.tagCondition.xtemplate", locale);
    }
}
