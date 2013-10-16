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

import org.apache.solr.client.solrj.response.FacetField;

import javax.jcr.PropertyType;

/**
 * @author Christophe Laprun
 */
public class Tag {
    private final FacetField.Count facetValue;
    private final int cardinality;
    private final String name;
    private final String uuid;
    private final static String type = PropertyType.TYPENAME_WEAKREFERENCE;
    String actionURL;
    private static int totalCardinality;

    public Tag(String name, int cardinality, String uuid, FacetField.Count facetValue) {
        this.name = name;
        this.cardinality = cardinality;
        this.uuid = uuid;
        this.facetValue = facetValue;
    }

    public String getName() {
        return name;
    }

    public static void setTotalCardinality(int totalCardinality) {
        Tag.totalCardinality = totalCardinality;
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

    public int getWeight() {
        float weight = totalCardinality > 0 ? 100 * ((float) cardinality / (float) totalCardinality) : 100 / (float) cardinality;
        return (int) weight;
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

    public FacetField.Count getFacetValue() {
        return facetValue;
    }
}
