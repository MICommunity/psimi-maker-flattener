/*  Copyright 2004 Arnaud CEOL

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.

 You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package psidev.psi.mi.filemakers.xmlMaker.structure;

import java.beans.XMLDecoder;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import psidev.psi.mi.filemakers.xmlMaker.mapping.FlatFileMapping;
import psidev.psi.mi.filemakers.xsd.FileMakersException;
import psidev.psi.mi.filemakers.xsd.Utils;

/**
 * The class provide graphical management for a tab delimited file <br>
 * It allows to load a file, choose the field delimiter, and recursively enter
 * into the fields and split them.
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 */

public class FlatFile {
	private static final Log log = LogFactory.getLog(FlatFile.class);

	public FlatFileMapping getMapping() {
		if (this.fileURL == null)
			return null;
		FlatFileMapping mapping = new FlatFileMapping();
		mapping.setFileURL(Utils.relativizeURL(this.fileURL).getPath());
		mapping.setLineSeparator(this.lineSeparator);
		mapping.setSeparators(this.separators);
		mapping.setFirstLineForTitle(this.firstLineForTitles());
		return mapping;
	}

	public int index = 0;

	/**
	 * true if first line of the file contains titles and should not be parsed
	 */
	public boolean firstLineForTitles = false;

	/**
	 * associate a path to a separator
	 */
	private HashMap<String, String> separators = new HashMap<>();

	/**
	 * current line
	 */
	public String line = "";

	/**
	 * current line number
	 */
	public int lineNumber = 0;

	public void setSeparator(String path, String separator) {
		separators.remove(path);
		separators.put(path, separator);
	}

	public String getSeparator(String path) {
		return separators.get(path);
	}

	/**
	 * part of previous line that has not been read used when the line
	 * separator is not the end of the line
	 */
	public String restOfPreviousLine = "";

	public boolean endOfFile = true;

	/**
	 * line separator: if null, lines will be read one by one in the file else
	 * the readline function will read the file until it find the separator
	 */
	public String lineSeparator = null;

	public String getElementAt(String path, String modelPath) {
		if (path.isEmpty())
			return line;
		if (modelPath == null)
			modelPath = path;
		String subpath = "";
		String[] paths = path.split("\\.");
		String[] modelPaths = modelPath.split("\\.");
		String field = line;

		for (int i = 0; i < paths.length; i++) {
			String separator = separators.get(subpath);
			String[] fields;
			if (separator == null) {
				fields = new String[1];
				fields[0] = field;
			} else
				fields = field.split(separator);
			try {
				field = fields[Integer.parseInt(paths[i])];
			} catch (java.lang.ArrayIndexOutOfBoundsException e) {
				/* no element in this subfield */
				return "";
			}
			if (i > 0)
				subpath += "." + modelPaths[i];
			else
				subpath += modelPaths[i];
		}

		return field;
	}

	/**
	 * set current line in this Object and recursively in all lists
	 */
	public void setLine(String aLine) {
		line = aLine;
	}

	public BufferedReader input;

	public URL fileURL;

	/**
	 * Initialize the reader and the file
	 */
	public void reload() throws FileMakersException, IOException {
		try {
			if (fileURL != null) {
				this.input = new BufferedReader(new InputStreamReader(fileURL
						.openStream()));
			}
			lineNumber = 0;
		} catch (FileNotFoundException fe) {
			throw new FileMakersException(
					"[PSI makers: PSI maker] Flat File: unable to load file");
		}
	}

	/**
	 * load a flat file. The file is chosen by the user in an option panel
	 */
	public void load(URL url) throws NullPointerException, IOException {
		lineNumber = 0;
		this.fileURL = url;
		this.input = new BufferedReader(new InputStreamReader((fileURL
				.openStream())));
		nextLine();
	}

	/**
	 * try to read a new line
	 */
	public void nextLine() {
		if (input == null) {
			endOfFile = true;
			return;
		}

		if (lineSeparator == null) {
			try {
				String newLine = input.readLine();
				if (newLine != null) {
					setLine(newLine);
					endOfFile = false;
					lineNumber++;
				} else {
					endOfFile = true;
				}
			} catch (IOException e) {
				System.out.println("next line pb: " + e);
				endOfFile = true;
			}
		} else /* read until lineSeparator is found */{
			String line = restOfPreviousLine;
			StringBuilder newLine = new StringBuilder();
			try {
				while (!line.contains(lineSeparator)) {
					newLine.append(line);
					line = input.readLine();
					lineNumber++;
				}
				newLine.append(line, 0, line.indexOf(lineSeparator));
				restOfPreviousLine = line.substring(line.indexOf(lineSeparator)
						+ lineSeparator.length());

                setLine(newLine.toString());
                endOfFile = false;

            } catch (IOException e) {
				if (newLine.length() != 0) {
					setLine(newLine.toString());
                }
                endOfFile = true;
            }
		}
	}

	/**
	 * try to read a new line until one with selected field not empty is found
	 */
	public void nextLineWithField(String path) {
		if (input == null)
			return;

		nextLine();
		getElementAt(path, null);
		while (getElementAt(path, null).isEmpty() && !endOfFile) {
			nextLine();
		}
	}

	/**
	 * @return true if a line has been read
	 */
	public boolean hasLine() {
		return !endOfFile;
	}

	/**
	 * go back to the beginning of the file
	 */
	public void restartFile() throws FileMakersException, IOException {
		reload();
		nextLine();
	}

	/**
	 * Return true if the first line of the file is used for titles. Used to
	 * know whether to parse the first line.
	 * 
	 * @return true if the first line of the file is used for titles
	 */
	public boolean firstLineForTitles() {
		return firstLineForTitles;
	}

	public void load(XMLDecoder ois) {
		firstLineForTitles = (Boolean) ois.readObject();
		lineSeparator = (String) ois.readObject();
		/** TODO: if file not found -> open a file chooser */
		separators = (HashMap<String, String>) ois.readObject();
	}

	/**
	 * @return Returns the index.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @param index
	 *            The index to set.
	 */
	public void setIndex(int index) {
		this.index = index;
	}

	/**
	 * @param separators
	 *            The separators to set.
	 */
	public void setSeparators(HashMap<String, String> separators) {
		this.separators = separators;
	}

	/**
	 * @return Returns the line.
	 */
	public String getLine() {
		return line;
	}

	/**
	 * @param firstLineForTitles
	 *            The firstLineForTitles to set.
	 */
	public void setFirstLineForTitles(boolean firstLineForTitles) {
		log.debug("title line: " + firstLineForTitles);
		this.firstLineForTitles = firstLineForTitles;
	}
}