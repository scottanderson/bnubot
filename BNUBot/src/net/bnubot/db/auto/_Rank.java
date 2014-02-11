/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.db.auto;

import java.util.List;

import net.bnubot.db.Account;
import net.bnubot.db.Command;
import net.bnubot.db.CustomDataObject;

/**
 * Class _Rank was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 * @author cayenne-generated-file
 */
@SuppressWarnings("serial")
public abstract class _Rank extends CustomDataObject<Integer> {
    public static final String AP_D2LEVEL_PROPERTY = "apD2Level";
    public static final String AP_DAYS_PROPERTY = "apDays";
    public static final String AP_MAIL_PROPERTY = "apMail";
    public static final String AP_RECRUIT_SCORE_PROPERTY = "apRecruitScore";
    public static final String AP_W3LEVEL_PROPERTY = "apW3Level";
    public static final String AP_WINS_PROPERTY = "apWins";
    public static final String EXPIRE_DAYS_PROPERTY = "expireDays";
    public static final String GREETING_PROPERTY = "greeting";
    public static final String PREFIX_PROPERTY = "prefix";
    public static final String SHORT_PREFIX_PROPERTY = "shortPrefix";
    public static final String VERBSTR_PROPERTY = "verbstr";
    public static final String ACCOUNT_ARRAY_PROPERTY = "accountArray";
    public static final String COMMAND_ARRAY_PROPERTY = "commandArray";

    public static final String ID_PK_COLUMN = "id";

    public void setApD2Level(Integer apD2Level) {
        writeProperty(AP_D2LEVEL_PROPERTY, apD2Level);
    }
    public Integer getApD2Level() {
        return (Integer)readProperty(AP_D2LEVEL_PROPERTY);
    }

    public void setApDays(Integer apDays) {
        writeProperty(AP_DAYS_PROPERTY, apDays);
    }
    public Integer getApDays() {
        return (Integer)readProperty(AP_DAYS_PROPERTY);
    }

    public void setApMail(String apMail) {
        writeProperty(AP_MAIL_PROPERTY, apMail);
    }
    public String getApMail() {
        return (String)readProperty(AP_MAIL_PROPERTY);
    }

    public void setApRecruitScore(Integer apRecruitScore) {
        writeProperty(AP_RECRUIT_SCORE_PROPERTY, apRecruitScore);
    }
    public Integer getApRecruitScore() {
        return (Integer)readProperty(AP_RECRUIT_SCORE_PROPERTY);
    }

    public void setApW3Level(Integer apW3Level) {
        writeProperty(AP_W3LEVEL_PROPERTY, apW3Level);
    }
    public Integer getApW3Level() {
        return (Integer)readProperty(AP_W3LEVEL_PROPERTY);
    }

    public void setApWins(Integer apWins) {
        writeProperty(AP_WINS_PROPERTY, apWins);
    }
    public Integer getApWins() {
        return (Integer)readProperty(AP_WINS_PROPERTY);
    }

    public void setExpireDays(int expireDays) {
        writeProperty(EXPIRE_DAYS_PROPERTY, expireDays);
    }
    public int getExpireDays() {
        Object value = readProperty(EXPIRE_DAYS_PROPERTY);
        return (value != null) ? (Integer) value : 0;
    }

    public void setGreeting(String greeting) {
        writeProperty(GREETING_PROPERTY, greeting);
    }
    public String getGreeting() {
        return (String)readProperty(GREETING_PROPERTY);
    }

    public void setPrefix(String prefix) {
        writeProperty(PREFIX_PROPERTY, prefix);
    }
    public String getPrefix() {
        return (String)readProperty(PREFIX_PROPERTY);
    }

    public void setShortPrefix(String shortPrefix) {
        writeProperty(SHORT_PREFIX_PROPERTY, shortPrefix);
    }
    public String getShortPrefix() {
        return (String)readProperty(SHORT_PREFIX_PROPERTY);
    }

    public void setVerbstr(String verbstr) {
        writeProperty(VERBSTR_PROPERTY, verbstr);
    }
    public String getVerbstr() {
        return (String)readProperty(VERBSTR_PROPERTY);
    }

    public void addToAccountArray(Account obj) {
        addToManyTarget(ACCOUNT_ARRAY_PROPERTY, obj, true);
    }
    public void removeFromAccountArray(Account obj) {
        removeToManyTarget(ACCOUNT_ARRAY_PROPERTY, obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<Account> getAccountArray() {
        return (List<Account>)readProperty(ACCOUNT_ARRAY_PROPERTY);
    }

    public void addToCommandArray(Command obj) {
        addToManyTarget(COMMAND_ARRAY_PROPERTY, obj, true);
    }
    public void removeFromCommandArray(Command obj) {
        removeToManyTarget(COMMAND_ARRAY_PROPERTY, obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<Command> getCommandArray() {
        return (List<Command>)readProperty(COMMAND_ARRAY_PROPERTY);
    }
}
