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
 */
public abstract class _Rank extends CustomDataObject {

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
        writeProperty("apD2Level", apD2Level);
    }
    public Integer getApD2Level() {
        return (Integer)readProperty("apD2Level");
    }

    public void setApDays(Integer apDays) {
        writeProperty("apDays", apDays);
    }
    public Integer getApDays() {
        return (Integer)readProperty("apDays");
    }

    public void setApMail(String apMail) {
        writeProperty("apMail", apMail);
    }
    public String getApMail() {
        return (String)readProperty("apMail");
    }

    public void setApRecruitScore(Integer apRecruitScore) {
        writeProperty("apRecruitScore", apRecruitScore);
    }
    public Integer getApRecruitScore() {
        return (Integer)readProperty("apRecruitScore");
    }

    public void setApW3Level(Integer apW3Level) {
        writeProperty("apW3Level", apW3Level);
    }
    public Integer getApW3Level() {
        return (Integer)readProperty("apW3Level");
    }

    public void setApWins(Integer apWins) {
        writeProperty("apWins", apWins);
    }
    public Integer getApWins() {
        return (Integer)readProperty("apWins");
    }

    public void setExpireDays(int expireDays) {
        writeProperty("expireDays", expireDays);
    }
    public int getExpireDays() {
        Object value = readProperty("expireDays");
        return (value != null) ? (Integer) value : 0;
    }

    public void setGreeting(String greeting) {
        writeProperty("greeting", greeting);
    }
    public String getGreeting() {
        return (String)readProperty("greeting");
    }

    public void setPrefix(String prefix) {
        writeProperty("prefix", prefix);
    }
    public String getPrefix() {
        return (String)readProperty("prefix");
    }

    public void setShortPrefix(String shortPrefix) {
        writeProperty("shortPrefix", shortPrefix);
    }
    public String getShortPrefix() {
        return (String)readProperty("shortPrefix");
    }

    public void setVerbstr(String verbstr) {
        writeProperty("verbstr", verbstr);
    }
    public String getVerbstr() {
        return (String)readProperty("verbstr");
    }

    public void addToAccountArray(Account obj) {
        addToManyTarget("accountArray", obj, true);
    }
    public void removeFromAccountArray(Account obj) {
        removeToManyTarget("accountArray", obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<Account> getAccountArray() {
        return (List<Account>)readProperty("accountArray");
    }


    public void addToCommandArray(Command obj) {
        addToManyTarget("commandArray", obj, true);
    }
    public void removeFromCommandArray(Command obj) {
        removeToManyTarget("commandArray", obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<Command> getCommandArray() {
        return (List<Command>)readProperty("commandArray");
    }


}
