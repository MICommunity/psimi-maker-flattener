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
package psidev.psi.mi.filemakers.xmlMaker.mapping;

import java.util.HashMap;

import javax.xml.bind.annotation.XmlTransient;

/**
 * 
 * The bean that can keep the mapping information about the flat files.
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 *  
 */
public class FlatFileMapping {

	@XmlTransient
	public String fileURL;

	/**
	 * line separator: if null, lines will be read one by one in the file else
	 * the readline function will read the file until it find the separator
	 */
	@XmlTransient
	public String lineSeparator = null;

	/**
	 * associate a path to a separator
	 */
	@XmlTransient
	public HashMap<String, String> separators = new HashMap<String, String>();

	@XmlTransient
	public boolean fisrtLineForTitle;
	
		
	
	public String getLineSeparator() {
		return lineSeparator;
	}

	public void setLineSeparator(String lineSeparator) {
		this.lineSeparator = lineSeparator;
	}

	public HashMap<String, String> getSeparators() {
		return separators;
	}

	public void setSeparators(HashMap<String, String> separators) {
		this.separators = separators;
	}


	public boolean isFisrtLineForTitle() {
		return fisrtLineForTitle;
	}

	public void setFisrtLineForTitle(boolean fisrtLineForTitle) {
		this.fisrtLineForTitle = fisrtLineForTitle;
	}

	public String getFileURL() {
		return fileURL;
	}

	public void setFileURL(String fileURI) {
		this.fileURL = fileURI;
	}

}