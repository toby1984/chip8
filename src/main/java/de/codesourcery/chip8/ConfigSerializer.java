package de.codesourcery.chip8;

import org.apache.commons.lang3.StringUtils;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.util.Properties;

final class ConfigSerializer
{
    private final Properties props;
    private String windowKey;

    public ConfigSerializer(Properties props) {
        this(props,"global");
    }

    public ConfigSerializer(Properties props, String windowKey) {
        this.props = props;
        this.windowKey = windowKey;
    }

    private String prop(String name) {
        return windowKey+"."+name;
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

    public static void applyWindowState(Properties props, String windowKey, Component comp)
    {
        final ConfigSerializer wrapper = new ConfigSerializer(props,windowKey);
        comp.setSize( wrapper.getSize(new Dimension(200,100 ) ) );
        comp.setLocation( wrapper.getLocation(new Point(0,0 ) ) );
        comp.setVisible( wrapper.isEnabled(true ) );
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

    public static void saveWindowState(Properties props, String windowKey, Component comp)
    {
        final ConfigSerializer wrapper = new ConfigSerializer(props,windowKey);
        wrapper.setSize( comp.getSize() );
        wrapper.setLocation( comp.getLocation() );
        wrapper.setEnabled( comp.isVisible() );
    }
}
