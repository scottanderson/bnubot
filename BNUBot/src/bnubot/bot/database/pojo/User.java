package bnubot.bot.database.pojo;

import java.io.Serializable;
import java.util.Date;

@SuppressWarnings("serial")
public class User implements Serializable {
	private Long id;
	private String login;
	private Account account;
	private String created;
	private String lastSeen;

	/** full constructor */
	public User(String login, Account account, String created, String lastSeen) {
		this.id = null;
		this.login = login;
		this.account = account;
		this.created = created;
		this.lastSeen = lastSeen;
	}

	/** default constructor */
	public User() {
		id = null;
		login = null;
		account = null;
		created = null; //new Date();
		lastSeen = null; //new Date();
	}
	
	public String toString() {
		return "Users[id=" + id + ",login=\"" + login + "\",created=\"" + created + "\",lastSeen=\"" + lastSeen + "\"]"; 
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public String getCreated() {
		return created;
	}

	public void setCreated(String created) {
		this.created = created;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(String lastSeen) {
		this.lastSeen = lastSeen;
	}

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}
}
