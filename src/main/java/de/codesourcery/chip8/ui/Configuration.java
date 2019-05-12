/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.chip8.ui;

import org.apache.commons.lang3.StringUtils;

import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.util.Properties;

/**
 * Application configuration.
 *
 * Helper class that wraps a {@link Properties} object to
 * hide the serialization/deserialization of configuration properties.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class Configuration
{
    private final Properties props;
    private MainFrame.ConfigKey configKey;

    public enum HighlightingColors {
        DIRECTIVE("color_directive",Color.ORANGE.darker()),
        COMMENT("color_comment",Color.LIGHT_GRAY),
        INSTRUCTION("color_instruction",Color.BLUE),
        IDENTIFIER("color_identifier", Color.GREEN.darker()),
        REGISTER("color_register", Color.MAGENTA),
        TEXT("color_text", Color.BLACK );

        public final String propertyName;
        public final Color defaultColor;

        HighlightingColors(String propertyName,Color defaultColor)
        {
            this.propertyName = propertyName;
            this.defaultColor = defaultColor;
        }

        public Style getStyle(StyledDocument document)
        {
            return document.getStyle( propertyName );
        }
    }

    private Configuration(Properties props, MainFrame.ConfigKey configKey) {
        this.props = props;
        this.configKey = configKey;
    }

    public Style addStyle(HighlightingColors color, StyledDocument document) {
        final Style style = document.addStyle( color.propertyName, null );
        StyleConstants.setForeground(style, getColor( color  ));
        return style;
    }

    public static Configuration of(Properties props) {
        return new Configuration(props, MainFrame.ConfigKey.GLOBAL);
    }

    public static Configuration of(Properties props, MainFrame.ConfigKey configKey) {
        return new Configuration(props,configKey);
    }

    private String prop(String name) {
        return configKey +"."+name;
    }

    private String getProperty(String name) {
        return props.getProperty( prop(name ) );
    }

    private void setProperty(String name,String value)
    {
        if ( value == null ) {
            props.remove(prop(name));
        }
        else
        {
            props.setProperty(prop(name), value);
        }
    }

    private Point getTuple(String name) {
        final String loc = getProperty( name );
        if ( StringUtils.isBlank(loc ) ) {
            return null;
        }
        final String[] parts = loc.split(",");
        return new Point( Integer.parseInt(parts[0]) , Integer.parseInt( parts[1] ) );
    }

    private void setTuple(String name,Point value)
    {
        setProperty(name, value == null ? null : value.x + "," + value.y);
    }

    public Dimension getSize(Dimension defaultSize) {
        final Point p = getTuple("size");
        return p == null ? defaultSize : new Dimension(p.x,p.y);
    }

    public void setSize(Dimension dim) {
        setTuple("size", new Point(dim.width,dim.height) );
    }

    public void setLocation(Point p) {
        setTuple("location", p);
    }

    public Point getLocation(Point defaultValue) {
        final Point p = getTuple("location");
        return p == null ? defaultValue : p;
    }

    private boolean getBoolean(String name,boolean defaultValue) {
        String s = getProperty( name );
        return StringUtils.isBlank(s) ? defaultValue : Boolean.parseBoolean(s);
    }

    private void setBoolean(String name,Boolean value)
    {
        setProperty(name,value == null ? null : Boolean.toString(value) );
    }

    public boolean isEnabled(boolean defaultValue)
    {
        return getBoolean("enabled",defaultValue);
    }

    public void setEnabled(boolean yesNo) {
        setBoolean("enabled", yesNo);
    }

    public static void applyWindowState(Properties props, MainFrame.ConfigKey configKey, Component comp)
    {
        final Configuration wrapper = new Configuration(props, configKey );
        comp.setSize( wrapper.getSize(new Dimension(200,100 ) ) );
        comp.setLocation( wrapper.getLocation(new Point(0,0 ) ) );
        final boolean isEnabled = wrapper.isEnabled( true );
        comp.setVisible( isEnabled );
    }

    private File getFile(String prop) {
        String path = getProperty(prop);
        final File file = path == null ? null : new File(path);
        return file != null && file.exists() ? file : null;
    }

    private void setFile(String prop,File file) {
        setProperty(prop, file == null ? null : file.getAbsolutePath() );
    }

    public File getLastBinary() {
        return getFile("binary");
    }

    public void setLastBinary(File file) {
        setFile("binary",file);
    }

    public File getLastSource() {
        return getFile("source");
    }

    public void setLastSource(File file) {
        setFile("source",file);
    }

    public Color getColor(HighlightingColors key) {
        final String value = getProperty( key.propertyName );
        if ( StringUtils.isBlank( value ) ) {
            return key.defaultColor;
        }
        return new Color( Integer.parseInt( value.trim(), 16 ) );
    }

    public void setColor(HighlightingColors key,Color color)
    {
        setProperty( key.propertyName, StringUtils.leftPad( Integer.toHexString( color.getRGB() ), 8, '0' ) );
    }

    public static void saveWindowState(Properties props, MainFrame.ConfigKey configKey, Component comp)
    {
        final Configuration wrapper = new Configuration(props, configKey );
        wrapper.setSize( comp.getSize() );
        wrapper.setLocation( comp.getLocation() );
        wrapper.setEnabled( comp.isVisible() );
    }
}
