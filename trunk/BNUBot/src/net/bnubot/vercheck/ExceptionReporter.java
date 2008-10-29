/**
 * This file is distributed under the GPL
 * $Id$
 */
package net.bnubot.vercheck;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import net.bnubot.Main;
import net.bnubot.util.Out;
import net.bnubot.util.SHA1Sum;

import org.xml.sax.InputSource;

/**
 * @author scotta
 */
public class ExceptionReporter {

	public static void main(String[] args) throws Exception {
		File logFile = new File("log.txt");

		Out.setOutputStream(new PrintStream(logFile));
		Out.info(Main.class, "asdf");
		Out.error(Main.class, "test");
		Out.exception(new Exception(new Exception("test")));

		ExceptionReporter.reportErrors(logFile);
		System.exit(1);
	}

	public static void reportErrors(File f) throws Exception {
		// FIXME ask the user if it's okay to submit errors
		BufferedReader br = new BufferedReader(new FileReader(f));
		String line;
		do {
			line = br.readLine();
			if (line.startsWith("["))
				continue;
			if (!line.contains("Exception: "))
				continue;

			String exception = line;

			line = br.readLine();
			do {
				if (!line.startsWith("\t") && !line.startsWith("Caused by: "))
					break;
				exception += "\n" + line;
				line = br.readLine();
			} while (line != null);

			reportException(exception);
		} while (line != null);
	}

	private static void reportException(String exception) throws Exception {
		SHA1Sum sha1 = new SHA1Sum(exception.getBytes());

		Map<String, String> params = new HashMap<String, String>();
		params.put("svn", CurrentVersion.version().getSvnRevision().toString());
		params.put("sha1", sha1.toString());
		params.put("exception", exception);

		InputSource source = new InputSource(doPost(params));
		XMLElementDecorator elem = XMLElementDecorator.parse(source);
		System.out.println(elem.toString());

		XMLElementDecorator error = elem.getChild("error");
		if(error != null)
			throw new Exception(error.getString());

		elem = elem.getChild("exception");
		if(elem.getChild("new").getBoolean())
			Out.info(ExceptionReporter.class, "You've reported a new exception for the first time!");
	}

	private static InputStream doPost(Map<String, String> postData) throws Exception {
		// Construct data
		String data = null;
		for(String key : postData.keySet()) {
			if(data == null)
				data = "";
			else
				data += "&";
			data += URLEncoder.encode(key, "UTF-8") + "=";
			data += URLEncoder.encode(postData.get(key), "UTF-8");
		}

		// Send data
		URL url = new URL("http://www.clanbnu.net/bnubot/exception.php");
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
		wr.write(data);
		wr.flush();
		wr.close();

		// Get the response
		return conn.getInputStream();
	}

}
