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
import java.beans.XMLEncoder;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class displayed a TabbedPane that contains the TabFiles
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 */
public class FlatFileContainer {
	 
	private static final Log log = LogFactory
     .getLog(FlatFileContainer.class);
	
	public ArrayList<FlatFile> flatFiles = new ArrayList<FlatFile>();

	public String id = "just 4 testin";

	public FlatFile getFlatFile(int index) {
		return (FlatFile) flatFiles.get(index);
	}

	public void addFlatFile(FlatFile f) {
		flatFiles.add(f);
	}

	public String getValue(String path, String modelPath) {
		if (modelPath == null)
			modelPath = path;
		int tabNum = Integer.parseInt(path.substring(0, path.indexOf(".")));
		return ((FlatFile) flatFiles.get(tabNum)).getElementAt(path
				.substring(path.indexOf(".") + 1), modelPath.substring(path
				.indexOf(".") + 1));
	}

	public String getSeparator(String path) {
		int tabNum = Integer.parseInt(path.substring(0, path.indexOf(".")));
		return ((FlatFile) flatFiles.get(tabNum)).getSeparator(path
				.substring(path.indexOf(".") + 1));
	}

	public void save(XMLEncoder oos) {
		oos.writeObject(flatFiles);
	}

	public void load(XMLDecoder ois) {
		flatFiles = (ArrayList<FlatFile>) ois.readObject();
	}

	/**
	 * @return Returns the flatFiles.
	 */
	public ArrayList<FlatFile> getFlatFiles() {
		return flatFiles;
	}

	/**
	 * @param flatFiles
	 *            The flatFiles to set.
	 */
	public void setFlatFiles(ArrayList<FlatFile> flatFiles) {
		this.flatFiles = flatFiles;
	}

	/**
	 * @return Returns the id.
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 *            The id to set.
	 */
	public void setId(String id) {
		this.id = id;
	}

	public int nbElements(String path) {
		/* use the path filter */
		try {
			return (getValue(path, null).split(getSeparator(path))).length;
		} catch (Exception e) {
			log.info("separator not yet defined");
			return 1;
		}
	}

}