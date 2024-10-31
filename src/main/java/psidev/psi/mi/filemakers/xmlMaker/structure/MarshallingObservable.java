/*
 * Copyright 2004 Arnaud CEOL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package psidev.psi.mi.filemakers.xmlMaker.structure;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * A class representing an observable structure for marshalling operations.
 * Used to notify observers of changes in properties like current file, message,
 * element, and line number during the XML document generation process.
 * Updated to replace java.util.Observable with java.beans.PropertyChangeSupport.
 * This allows for finer-grained notifications and property-based change handling.
 *
 * @author Arnaud
 */
public class MarshallingObservable {

	private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

	private int currentLine = 0;
	private String currentFlatFile = "";
	private String element = "";
	private String message = "";
	public int indentation;

	/**
	 * Registers a PropertyChangeListener to this observable.
	 *
	 * @param listener The listener to add
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		propertyChangeSupport.addPropertyChangeListener(listener);
	}

	/**
	 * Removes a PropertyChangeListener from this observable.
	 *
	 * @param listener The listener to remove
	 */
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		propertyChangeSupport.removePropertyChangeListener(listener);
	}

	/**
	 * @return Returns the message.
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the message and notifies listeners of the change.
	 *
	 * @param message The message to set
	 */
	public void setMessage(String message) {
		String oldMessage = this.message;
		this.message = message;
		propertyChangeSupport.firePropertyChange("message", oldMessage, message);
	}

	/**
	 * @return Returns the element.
	 */
	public String getElement() {
		return element;
	}

	/**
	 * Sets the element and notifies listeners of the change.
	 *
	 * @param element The element to set
	 */
	public void setElement(String element) {
		String oldElement = this.element;
		this.element = element;
		propertyChangeSupport.firePropertyChange("element", oldElement, element);
	}

	/**
	 * @return Returns the current flat file being processed.
	 */
	public String getCurrentFlatFile() {
		return currentFlatFile;
	}

	/**
	 * Sets the current flat file being processed and notifies listeners of the change.
	 *
	 * @param currentFlatFile The currentFlatFile to set
	 */
	public void setCurrentFlatFile(String currentFlatFile) {
		String oldFlatFile = this.currentFlatFile;
		this.currentFlatFile = currentFlatFile;
		propertyChangeSupport.firePropertyChange("currentFlatFile", oldFlatFile, currentFlatFile);
	}

	/**
	 * @return Returns the current line being processed.
	 */
	public int getCurrentLine() {
		return currentLine;
	}

	/**
	 * Sets the current line being processed and notifies listeners of the change.
	 *
	 * @param currentLine The currentLine to set
	 */
	public void setCurrentLine(int currentLine) {
		int oldLine = this.currentLine;
		this.currentLine = currentLine;
		propertyChangeSupport.firePropertyChange("currentLine", oldLine, currentLine);
	}

	/**
	 * @return Returns the indentation level.
	 * Tracks nested file levels in XML parsing.
	 */
	public int getIndentation() {
		return indentation;  // Return the current indentation level.
	}

	/**
	 * Sets the indentation level and notifies listeners of the change.
	 *
	 * @param indentation The indentation level to set
	 */
	public void setIndentation(int indentation) {
		int oldIndentation = this.indentation;
		this.indentation = indentation;
		propertyChangeSupport.firePropertyChange("indentation", oldIndentation, indentation);
	}
}
