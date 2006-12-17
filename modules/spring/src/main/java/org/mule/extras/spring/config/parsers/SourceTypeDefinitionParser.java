/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the MuleSource MPL
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extras.spring.config.parsers;

import org.mule.extras.spring.config.AbstractChildBeanDefinitionParser;
import org.w3c.dom.Element;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;

/**
 * TODO
 */
public class SourceTypeDefinitionParser extends AbstractChildBeanDefinitionParser
{

    public String getPropertyName(Element e)
    {
        return "sourceType";
    }

    protected Class getBeanClass(Element element)
    {
        return String.class;
    }

    protected void parseChild(Element element, ParserContext parserContext, BeanDefinitionBuilder builder)
    {
        String clazz = element.getAttribute("class");
        builder.getBeanDefinition().getConstructorArgumentValues().addGenericArgumentValue(clazz, String.class.getName());
        postProcess(builder, element);
    }
}
