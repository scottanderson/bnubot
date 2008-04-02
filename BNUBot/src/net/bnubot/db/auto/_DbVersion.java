package net.bnubot.db.auto;

import org.apache.cayenne.CayenneDataObject;

/**
 * Class _DbVersion was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _DbVersion extends CayenneDataObject {

    public static final String VERSION_PROPERTY = "version";

    public static final String VERSION_PK_COLUMN = "version";

    public void setVersion(Integer version) {
        writeProperty("version", version);
    }
    public Integer getVersion() {
        return (Integer)readProperty("version");
    }

}