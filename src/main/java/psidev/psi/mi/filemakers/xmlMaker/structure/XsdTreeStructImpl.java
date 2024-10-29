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


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import javax.swing.tree.TreeNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exolab.castor.xml.schema.Annotated;
import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.Group;
import org.exolab.castor.xml.schema.Order;
import org.exolab.castor.xml.schema.Structure;
import org.exolab.castor.xml.schema.XMLType;
import psidev.psi.mi.filemakers.xmlMaker.mapping.TreeMapping;
import psidev.psi.mi.filemakers.xsd.FileMakersException;
import psidev.psi.mi.filemakers.xsd.MessageManagerInt;
import psidev.psi.mi.filemakers.xsd.Utils;
import psidev.psi.mi.filemakers.xsd.XsdNode;

/**
 * 
 * This class overrides the abstract class AbstractXslTreeStruct to provide a
 * tree representation of an XML schema, with management of marshalling of
 * several flat files to a xml file that respects the schema
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 *  
 */
public class XsdTreeStructImpl extends
		psidev.psi.mi.filemakers.xsd.AbstractXsdTreeStruct {
	 	
	private static final Log log = LogFactory
	            .getLog(XsdTreeStructImpl.class);
	
	/** keep the number of the line currently parsed */
	private int lineNumber = 0;
	
	private final String UNBOUNDED = "unbounded";

	/* TODO: give choice for checking XML */

	/**
	 * current indentation in the XML document: a string containing only
	 * tabulations
	 */
	private String indentation = "";

	/**
	 * Observer for the marshalling
	 */
	public MarshallingObservable observable = new MarshallingObservable();
	public DictionaryContainer dictionaries = new DictionaryContainer();
	public ArrayList<XsdNode> unduplicableNodes = new ArrayList<>();
	public HashMap<XsdNode, String> validationRegexps = new HashMap<>();

	public String getRegexp(XsdNode node) {
		if (validationRegexps.containsKey(node))
			return validationRegexps.get(node);
		return "";
	}

	private String pathFilter;

	public FlatFileContainer flatFiles = new FlatFileContainer();

	public String name = "";

	/**
	 * id for autogeneration: type MINT-001
	 */
	public String id = "";

	/**
	 * last id used
	 * 
	 * @uml.property name="lastId"
	 */
	public int lastId = 0;

	/**
	 * text area for warnings and error messages
	 * 
	 * @uml.property name="associatedFields"
	 */
	public HashMap<XsdNode, String> associatedFields = new HashMap<>();

	/**
	 * 
	 * @uml.property name="associatedDuplicableFields"
	 */
	public HashMap<XsdNode, String> associatedDuplicableFields = new HashMap<>();

	/**
	 * keep current values for referenced fields
	 * 
	 * @uml.property name="associatedValues"
	 */
	public HashMap<XsdNode, String> associatedValues = new HashMap<>();

	/**
	 * associate a list dictionnary value to a node. The original value will be
	 * kept for this node if the value is not found in the dictionary.
	 * 
	 * @uml.property name="associatedDictionary"
	 */
	public HashMap<XsdNode, Integer> associatedOpenDictionary = new HashMap<>();

	/**
	 * associate a list dictionnary value to a node. No value will be returned
	 * for this node if the value is not found in the dictionary.
	 * 
	 * @uml.property name="associatedDictionary"
	 */
	public HashMap<XsdNode, Integer> associatedClosedDictionary = new HashMap<>();

	/**
	 * associate the index of the column containing the replacement value (i.e.
	 * the postition of the definition on a line.) in the dictionnary associated
	 * to a node.
	 * 
	 * @uml.property name="associatedDictionaryColumn"
	 */
	public HashMap<XsdNode, Integer> associatedDictionaryColumn = new HashMap<>();

	/**
	 * list of the nodes for wich the value has to be generated
	 * 
	 * @uml.property name="associatedAutogeneration"
	 */
	public ArrayList<XsdNode> associatedAutogeneration = new ArrayList<>();

	/**
	 * list of the nodes at which are associated each flat file
	 * 
	 * @uml.property name="associatedFlatFiles"
	 */
	public ArrayList<XsdNode> associatedFlatFiles = new ArrayList<>();

	public ArrayList<FlatFile> flatFilesStack = new ArrayList<>();

	public FlatFile getCurrentFlatFile() {
		if (!flatFilesStack.isEmpty())
			return flatFilesStack.get(flatFilesStack.size() - 1);
		return null;
	}

	/**
	 * create a new instance of XslTree The nodes will be automaticaly
	 * duplicated if the schema specify that more than one element of this type
	 * are mandatory
	 */
	public XsdTreeStructImpl() {
		super(false, true);
		associatedFlatFiles.add(null);
	}

	/**
	 * this method should reinitialize every variable makin reference to the
	 * actual tree, such as any <code>List</code> used to make associations to
	 * externals objects.
	 * reinitialized associations of nodes with columns, default values,
	 * dictionaries, autogeneration of value and associations to flat files
	 */
	public void emptySelectionLists() {
		associatedFields = new HashMap<>();
		associatedValues = new HashMap<>();
		associatedClosedDictionary = new HashMap<>();
		associatedOpenDictionary = new HashMap<>();
		associatedDictionaryColumn = new HashMap<>();
		associatedAutogeneration = new ArrayList<>();
		associatedFlatFiles = new ArrayList<>();
		expendChoices = new ArrayList<>();
	}

	/**
	 * set the FlatFile in which getting the values
	 * 
	 * @param f
	 *            a FlatFile
	 */
	public void pushFlatFile(FlatFile f) {
		flatFilesStack.add(f);
	}

	public void popFlatFile() {
		flatFilesStack.remove(flatFilesStack.size() - 1);
	}

	/**
	 * Check if the node has a root node for ancestor. It is usefull when
	 * associating a node to a flat file as two root nodes (nodes associated to
	 * a flat file) should not have children in common
	 * 
	 * @param node
	 * @return
//	 */

    /**
	 * associate the node selected to the FlatFile selected in the associated
	 * FlatFileTabbedPanel.
	 *  
	 */
	public void associateFlatFile(XsdNode node, int flatFile) {

		XsdNode previousAssociation;

		/*
		 * if the file was already associated, warn the user that all
		 * associations to this file will be lost
		 */
		while (associatedFlatFiles.size() <= flatFile) {
			associatedFlatFiles.add(null);
		}

		previousAssociation = associatedFlatFiles.get(flatFile);

		int previousFlatfileAssociated = associatedFlatFiles.indexOf(node);
		if (previousFlatfileAssociated > -1)
			associatedFlatFiles.set(previousFlatfileAssociated, null);

		check((XsdNode) treeModel.getRoot());
		if (previousAssociation != null)
			treeModel.reload(previousAssociation);

		associatedFlatFiles.set(flatFile, node);
		/* root node is mandatory */
		rootNode.use();
	}

	/**
	 * associate a default value to the node selected
	 */
	public void associateDefaultValue(XsdNode node, String value) {
		cancelAllAssociations(node);
		associatedValues.put(node, value);
		node.useOnlyThis();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	/**
	 * associate a dictionnary to the node selected. Each time a value will be
	 * requested for this node, it will be changed for its replacement value in
	 * target list if it exists
	 */
	public void associateDictionnary(XsdNode node, int dictionary, int column,
			boolean closedAssociation) {
		associatedClosedDictionary.remove(node);
		associatedOpenDictionary.remove(node);
		if (closedAssociation)
			associatedClosedDictionary.put(node, dictionary);
		else
			associatedOpenDictionary.put(node, dictionary);
		associatedDictionaryColumn.put(node, column);
	}

	/**
	 * associate the node selected to a cell by its pat representation
	 *  
	 */
	public void associateField(XsdNode node, String path,
			boolean isUnduplicableAssociation) {
		cancelAllAssociations(node);
		associatedFields.put(node, path);
		if (isUnduplicableAssociation)
			unduplicableNodes.add(node);
		node.use();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

    public void associateValidationRegexp(XsdNode node, String regexp) {
		validationRegexps.put(node, regexp);
	}

	public void associateDuplicableField(XsdNode node, String path) {
		associatedDuplicableFields.put(node, path);
		node.use();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	/**
	 * removes the association of the node selected with a dictionnary
	 */
	public void cancelAssociateDictionnary(XsdNode node) {
		associatedClosedDictionary.remove(node);
		associatedOpenDictionary.remove(node);
		associatedDictionaryColumn.remove(node);
	}

	/**
	 * removes the association of the node selected with a cell
	 */
	public void cancelAssociateField(XsdNode node) {
		if (!associatedFields.containsKey(node))
			return;
		associatedFields.remove(node);
		node.unuse();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	/**
	 * removes the association of the node selected with a cell
	 */
	public void cancelAssociateFlatFile(XsdNode node) {
		if (!associatedFlatFiles.contains(node))
			return;
		associatedFlatFiles.set(associatedFlatFiles.indexOf(node), null);
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	public void cancelDuplicableField(XsdNode node) {
		if (!associatedDuplicableFields.containsKey(node))
			return;
		associatedDuplicableFields.remove(node);
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	/**
	 * removes the association of the node selected with any default value
	 */
	public void cancelDefaultValue(XsdNode node) {
		if (!associatedValues.containsKey(node))
			return;
		associatedValues.remove(node);
		node.unuseOnlyThis();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	/**
	 * checks if the node is associated to a default value
	 * 
	 * @param node
	 *            a node
	 * @return true if such an association exists
	 */
	public boolean hasDefaultValue(XsdNode node) {
		return associatedValues.containsKey(node);
	}

	/**
	 * checks if the node is associated to a cell
	 * 
	 * @param node
	 *            a node
	 * @return true if such an association exists
	 */
	public boolean isAffected(XsdNode node) {
		return associatedFields.containsKey(node);
	}

	/**
	 * check if target nod eis mapped to anything.
	 * 
	 * @param node
	 * @return
	 */

    /**
	 * get the value for a node
	 * 
	 * @param node
	 *            a node
	 * @return the value in the field associated to this node if the association
	 *         exists (eventually replaced by a replacement value in a
	 *         dictionnary), if not a automaticaly generated value if the node
	 *         has been setted to request one, if not the default value if one
	 *         has been associated to the node. Else return null
	 */
	public String getValue(XsdNode node) {
		/* node affected to a field */
		if (isAffected(node)) {
			String path = associatedFields.get(node);
			String modelPath = path;
			/* remember not to use the filter for unduplicable nodes */
			if (pathFilter != null && !unduplicableNodes.contains(node)) {
				String[] filters = pathFilter.split("\\.");
				String[] paths = path.split("\\.");
				String filteredPath = "";
				for (int i = 0; i < filters.length; i++) {
					try {
					paths[i] = String.valueOf(Integer.parseInt(filters[i])
							+ Integer.parseInt(paths[i]));
					} catch (IndexOutOfBoundsException e) {					
						return "";
					}
				}
				for (int i = 0; i < paths.length - 1; i++) {
					filteredPath += paths[i] + ".";
				}
				filteredPath += paths[paths.length - 1];
				path = filteredPath;
			}
			String value = flatFiles.getValue(path, modelPath);
			if (value == null) {
				return null;
			}

			if (validationRegexps.containsKey(node)) {
				if (!value.matches(validationRegexps.get(node)))
					return null;
			}

			if (associatedClosedDictionary.containsKey(node)) {
				String replacementValue = dictionaries.getReplacementValue(
                        associatedClosedDictionary.get(node), value,
                        associatedDictionaryColumn.get(node));

				if (replacementValue == null) {
					getMessageManager().sendMessage(printPath(node.getPath()) 
							+ ": no value found for " + value 
							+ " in dictionary! (line : " + lineNumber + ")", MessageManagerInt.warningMessage);
					return null;
				}
				return getXmlValue(replacementValue.trim());
			} else if (associatedOpenDictionary.containsKey(node)) {
				String replacementValue = dictionaries.getReplacementValue(
                        associatedOpenDictionary.get(node), value,
                        associatedDictionaryColumn.get(node));

				if (replacementValue != null) {
					value = replacementValue;
				}
			}
			if (value.trim().isEmpty())
				return null;

			return getXmlValue(value.trim());
		}

		/* node with value autogenerated */
		if (associatedAutogeneration.contains(node)) {
			String value = id + lastId;
			lastId++;
			return getXmlValue(value);
		}

		/* node with default value */
		if (hasDefaultValue(node)) {
			return getXmlValue(associatedValues.get(node));
		}
		return "";
	}

	/**
	 * return a new String where specials characters are public
	 */
	public String getXmlValue(String value) {
		return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;")
				.replaceAll(">", "&gt;").replaceAll("'", "&apos;").replaceAll(
						"\"", "&quot;");
	}

	/**
	 * get informations about the node in an understandable String
	 */
	public String getInfos(XsdNode node) {
		if (node == null)
			return "No node selected!";

		String infos = super.getInfos(node);
		// column associated
		infos += getAssociationInfo(node);
		return infos;
	}

	public String getAssociationInfo(XsdNode node) {
		String infos = "";

		String duplicableField = associatedDuplicableFields.get(node);

		if (duplicableField != null) {
			infos += "this node will be automaticaly duplicated: "
					+ "\nfile: "
					+ Utils
							.relativizeURL(
									flatFiles
											.getFlatFile(Integer
													.parseInt(duplicableField
															.substring(
																	0,
																	duplicableField
																			.indexOf(".")))).fileURL)
							.getPath()
					+ "\nfield: "
					+ duplicableField
							.substring(duplicableField.indexOf(".") + 1)
					+ ".[1..*]\n";
		}

		String field = associatedFields.get(node);
		if (field != null) {
			infos += "associated field: "
					+ "\nfile: "
					+ Utils.relativizeURL(
							flatFiles.getFlatFile(Integer
									.parseInt(field.substring(0, field
											.indexOf(".")))).fileURL)
							.getPath() + "\nfield: "
					+ field.substring(field.indexOf(".") + 1) + "\n";
		}
		// default value
		if (hasDefaultValue(node)) {
			infos += "associated value: " + associatedValues.get(node) + "\n";
		}
		// dictionnary
		if (associatedOpenDictionary.containsKey(node)) {
			infos += "find replacement value in dictionnary: "
					+ dictionaries.getName(associatedOpenDictionary.get(node))
					+ " or keep orginal value.\n";
		}
		// dictionnary
		if (associatedClosedDictionary.containsKey(node)) {
			infos += "find replacement value in dictionnary: "
					+ dictionaries
							.getName(associatedClosedDictionary
                                    .get(node)) + "\n";
		}
		if (associatedAutogeneration.contains(node)) {
			infos += "A value will be automaticaly generated for this node.";
		}

		if (associatedFlatFiles.contains(node)) {
			infos += Utils.relativizeURL(
					flatFiles.getFlatFile(associatedFlatFiles
							.indexOf(node)).fileURL).getPath()
					+ "\n";
		}
		if (unduplicableNodes.contains(node)) {
			infos += "unduplicable";
		}
		if (this.validationRegexps.containsKey(node)) {
			infos += "validated by regular expression:"
					+ validationRegexps.get(node);
		}
		return infos;
	}

	public boolean checkAttribute(XsdNode node) {
		if (node.isRequired 
				/** TODO: check if it works, 2006-05-25 */ 
				&& !isAffected(node) && !hasDefaultValue(node)
				&& !associatedAutogeneration.contains(node))
				{
			node.isCheckedOk = false;
			return false;
		} else {
			node.isCheckedOk = true;
			return true;
		}
	}

	public boolean checkElement(XsdNode node) {
		if (!node.transparent && !node.isUsed() && !node.isRequired) {
			node.isCheckedOk = true;
			return true;
		}

		if (node.transparent) {
			boolean checkedOk = true;
			for (Iterator<XsdNode> it = getChildren(node); it.hasNext(); ) {
				XsdNode child = it.next();
				if (!check(child)) {
					checkedOk = false;
				}
			}
			node.isCheckedOk = checkedOk;
			return checkedOk;
		}

		XMLType type = ((ElementDecl) node.getUserObject()).getType();

		if (type == null) {
			return true;
		}

		/* simpleType */
		if (type.isSimpleType()) {
			if (node.isRequired  
					&& !isAffected(node) && !hasDefaultValue(node)
					&& !associatedAutogeneration.contains(node)
					) {
				node.isCheckedOk = false;
				return false;
			} else {
				node.isCheckedOk = true;
				return true;
			}
		} else { /* complexType, ie: attributes + group */
			return checkGroup(node);
		}
	}

	/*
	 * a group can only be a choice (else it would be expanded if we find it, it
	 * means user has to make a choice
	 */
	public boolean checkGroup(XsdNode node) {

		boolean hasUsedChild = false;
		
		if (node.transparent) {
			boolean checkedOk = true;
			Enumeration<TreeNode> children = node.children();

			while (children.hasMoreElements()) {
				XsdNode child = (XsdNode) children.nextElement();
				if (!check(child)) {
					checkedOk = false;
				}
			}
			node.isCheckedOk = checkedOk;
			return checkedOk;
		}

		
		Enumeration<TreeNode> children = node.children();
		
		// if it doesn't have children, treat it as an attribute
		if (!children.hasMoreElements()) {
			return checkAttribute(node);
		}
	
		
		boolean errors = false;
		/* check if number of subelts is correct */
		HashMap<String, Object> maxOccurs = new HashMap<>();
		HashMap<String, Integer> minOccurs = new HashMap<>();

	
		while (children.hasMoreElements()) {
			XsdNode child = (XsdNode) children.nextElement();
			int nbDuplications = 1;
			String previousFilter = pathFilter;
			String filter = "";

			if (associatedDuplicableFields.containsKey(child)) {
				nbDuplications = flatFiles
						.nbElements(associatedDuplicableFields
								.get(child));

				for (int i = 0; i < associatedDuplicableFields
						.get(child).split("\\.").length; i++) {
					filter += "0.";
				}
				filter += "0";
				pathFilter = filter;
			}

			for (int i = 0; i < nbDuplications; i++) {
				if (i > 0) {
					int lastFilterIdx = Integer.parseInt(filter
							.substring(filter.lastIndexOf(".") + 1))
							+ i;
					pathFilter = filter.substring(0,
							filter.lastIndexOf(".") + 1)
							+ lastFilterIdx;
				}
				boolean isChildOk = check(child);

				switch (((Annotated) child.getUserObject()).getStructureType()) {
				case Structure.ATTRIBUTE:
					if (!isChildOk) {
						errors = true;
					}
					break;
				case Structure.GROUP:
					if (((Group) child.getUserObject()).getOrder().getType() == Order.CHOICE && !child.isExtended) {
						errors = true;
					} else if (!isChildOk) {
						errors = true;
					}
					break;
				case Structure.ELEMENT:
					/* initialisation if first occurence of the element */
					if (!maxOccurs.containsKey(child.toString())) {
						int max = child.max;
						if (max != -1) {
							maxOccurs.put(child.toString(), max);
						} else {
							maxOccurs.put(child.toString(), UNBOUNDED);
						}
						minOccurs.put(child.toString(), child.min);
					}

					if (child.isCheckedOk) {
						try {
							maxOccurs.put(child.toString(), (Integer) maxOccurs.get(child.toString()) - 1);
						} catch (ClassCastException e) {
							/*
							 * ok, max is unbounded and exception is throws when
							 * trying to cast String to Integer
							 */
						}
						minOccurs.put(child.toString(), minOccurs.get(child.toString()) - 1);
					}
				}

				pathFilter = previousFilter;
				
				if (child.isUsed())
					hasUsedChild=true;
			}

		}
		Iterator<String> names = minOccurs.keySet().iterator();

		Iterator<Integer> mins = minOccurs.values().iterator();
		Iterator<Object> maxs = maxOccurs.values().iterator();
		while (names.hasNext()) {
			names.next();
			// if a min is > 0, it means that an element is missing
			/////////////////////// dat one
			if (mins.next() > 0) {
				errors = true;
			}

			/* if a max is < 0, it means there are too much elements */
			try {
				if ((Integer) maxs.next() < 0) {
					errors = true;
				}

			} catch (ClassCastException e) {
				/*
				 * ok, max is unbounded and exception is throws when trying to
				 * cast String to Integer
				 */
			}
		}
		node.isCheckedOk = (!errors);
		
		
		if (node.isRequired && !hasUsedChild)
			node.isCheckedOk = false;

		return node.isCheckedOk;
	}

	/**
	 * return XML code to close the element
	 * 
	 * @param node
	 *            a node
	 * @param isEmptyElement
	 *            if the node does not have neither attribute nor value or sub
	 *            elements
	 * @return XML code
	 */
	public String closeElement(XsdNode node, boolean isEmptyElement) {
		if (isEmptyElement)
			return "";
		return "</" + node.toString() + ">";
	}

	/**
	 * write the XML code to close the element
	 * 
	 * @param node
	 *            a node
	 * @param isEmptyElement
	 *            if the node does not have neither attribute nor value or sub
	 *            elements
	 * @param out
	 *            the writer used to write the code
	 */

    /**
	 * check if these are enough associations according to the shema
	 *
	 * condition for being "checkedOK": attributes: if is associated to a value
	 * or not required simpleType elements: if is associated to a value element,
	 * complex type: if all sub Elements are checkedOk group: if the count of
	 * subElements "checkedOk" is good
	 *
	 * condition for errors: elements or group is not "checkedOk"
	 *
	 */
	public boolean check(XsdNode node) {
		switch (((Annotated) node.getUserObject()).getStructureType()) {

		case Structure.ATTRIBUTE:
			return checkAttribute(node);
		case Structure.ELEMENT:
			return checkElement(node);
		case Structure.GROUP:
			return checkGroup(node);
		default:
			getMessageManager().sendMessage(printPath(node.getPath()) +" type not found: "
					+ ((Annotated) node.getUserObject()).getStructureType(), MessageManagerInt.errorMessage);
			return false;
		}
	}


    /**
	 * return XML code to open the element
	 * 
	 * @param node
	 *            a node
	 * @param isEmptyElement
	 *            if the node does not have neither attribute nor value or sub
	 *            elements
	 * @param attributes
	 *            a string containing the XML code for the attributes of this
	 *            element
	 * @return the XML code for the element
	 */
	public String openElement(XsdNode node, String attributes,
			boolean isEmptyElement) {
		if (isEmptyElement)
			return "\n<" + node.toString() + attributes + "/>";
		return "\n<" + node.toString() + " " + attributes + ">";
	}

	public String openElement(XsdNode node, ArrayList<String> attributes,
			boolean isEmptyElement) {
		
		String attributesString = "";
		Iterator<String> it = attributes.iterator();
		while (it.hasNext()) {
			String attributeName = it.next();
			String attributeValue = it.next();
			if (!attributeValue.isEmpty())
				attributesString += " " +attributeName+"=\""+attributeValue+"\"";
		}
		
		
		// if root node :
		if (node == treeModel.getRoot()) {
			attributesString += " xsi:schemaLocation=\""+schema.getTargetNamespace()+" "+schemaURL+"\"";
			attributesString += " xmlns=\""+schema.getTargetNamespace()+"\"";
			attributesString += " xmlns:xsi=\""+schema.getSchemaNamespace()+"\"";
		}
		
		attributesString = attributesString.trim();
		if (!attributesString.isEmpty())
			attributesString = " " + attributesString;
		else if (isEmptyElement)
			return null;
		if (isEmptyElement)
			return "\n" + indentation + "<" + node.toString() + attributesString + "/>";
		return "\n" + indentation + "<" + node.toString() + attributesString + ">";
	}


	public String previewAttribute(XsdNode node) {
		String value = getValue(node);

		if (value != null && !value.isEmpty())
			return " " + ((AttributeDecl) node.getUserObject()).getName()
					+ "=\"" + value + "\"";
		else
			return null;

	}

	public String previewElement(XsdNode node) {

		if (!node.isUsed())
			return null;

		String attributes = "";
		String elements = "";
		String value;
		/*
		 * get every child of the node get the structureType of the userElement
		 * and use the appropriate marshaller
		 */
		Enumeration<TreeNode> children = node.children();
		while (children.hasMoreElements()) {
			XsdNode child = (XsdNode) children.nextElement();
			switch (((Annotated) child.getUserObject()).getStructureType()) {
			case Structure.ATTRIBUTE:
				String attribute = previewAttribute(child);
				if (attribute != null)
					attributes += attribute;
				break;
			case Structure.ELEMENT:
				String element = previewElement(child);
				if (element != null)
					elements += element;
				break;
			case Structure.GROUP:
				String group;
				group = previewGroup(child);
				if (group != null)
					elements += group;
				break;
			}
		} /* get the value affected to this element */
		value = getValue(node);

		boolean isEmptyElement = ((value == null || value.isEmpty()) && elements.isEmpty());
		
		if (!elements.isEmpty())
			elements = elements + "\n";
		if (!attributes.isEmpty() && isEmptyElement)
			return null;

		if (value == null)
			value = "";

		return openElement(node, attributes, isEmptyElement) + value + elements
				+ closeElement(node, isEmptyElement);
	}

	public String previewGroup(XsdNode node) {

		String group = "";
		Enumeration<TreeNode> elements = node.children();
		while (elements.hasMoreElements()) {
			String element = previewNode((XsdNode) elements.nextElement());
			if (element != null)
				group += element;
		}
		return group;
	}

	public String previewNode(XsdNode node) {
		switch (((Annotated) node.getUserObject()).getStructureType()) {
		case Structure.ATTRIBUTE:
			return previewAttribute(node);
		case Structure.GROUP:
			return previewGroup(node);
		case Structure.ELEMENT:
			return previewElement(node);
		default:
			return "<error: unmanaged elementt/>";
		}
	}

	public void cancelAllAssociations(XsdNode node) {
		unduplicableNodes.remove(node);
		validationRegexps.remove(node);
		associatedAutogeneration.remove(node);
		associatedFields.remove(node);
		associatedValues.remove(node);
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	public void associateAutoGenerateValue(XsdNode node) {
		cancelAllAssociations(node);
		associatedAutogeneration.add(node);

		name = node.toString();

		node.useOnlyThis();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	public void cancelAutogenerate(XsdNode node) {
		if (!associatedAutogeneration.contains(node))
			return;

		associatedAutogeneration.remove(node);
		node.unuseOnlyThis();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(node);
	}

	public TreeMapping getMapping() {
		
		TreeMapping mapping = new TreeMapping();

		mapping.setId(this.id);
		mapping.setAutoDuplicate(this.autoDuplicate);
		mapping.setManageChoices(this.manageChoices);
		
		if ("http".equals(getSchemaURL().getProtocol())) {
			mapping
			.setSchemaURL(this.getSchemaURL().toString());			
		}else {
			mapping
				.setSchemaURL(Utils.relativizeURL(this.getSchemaURL())
						.getPath());
		}
		ArrayList<String> associatedAutogeneration = new ArrayList<>();
        for (XsdNode xsdNode : this.associatedAutogeneration) {
            associatedAutogeneration
                    .add(getPathForNode(xsdNode));
        }
		mapping.setAssociatedAutogeneration(associatedAutogeneration);

		ArrayList<String> unduplicableNodes = new ArrayList<>();
        for (XsdNode unduplicableNode : this.unduplicableNodes) {
            unduplicableNodes
                    .add(getPathForNode(unduplicableNode));
        }
		mapping.setUnduplicableNodes(unduplicableNodes);

		mapping.setExpendChoices(this.expendChoices);

		HashMap<String, String> associatedFields = new HashMap<>();
		for (XsdNode node : this.associatedFields.keySet()) {
			associatedFields.put(getPathForNode(node), this.associatedFields
					.get(node));
		}
		mapping.setAssociatedFields(associatedFields);

		HashMap<String, String> associatedDuplicableFields = new HashMap<>();
		for (XsdNode node : this.associatedDuplicableFields.keySet()) {
			associatedDuplicableFields.put(getPathForNode(node),
					this.associatedDuplicableFields.get(node));
		}
		mapping.setAssociatedDuplicableFields(associatedDuplicableFields);

		HashMap<String, String> associatedValues = new HashMap<>();
		for (XsdNode  node : this.associatedValues.keySet()) {
			associatedValues.put(getPathForNode(node), this.associatedValues
					.get(node));
		}
		mapping.setAssociatedValues(associatedValues);

		HashMap<String, String> validationRegexps = new HashMap<>();
		for (XsdNode node : this.validationRegexps.keySet()){
			validationRegexps.put(getPathForNode(node), this.validationRegexps
					.get(node));
		}
		mapping.setValidationRegexps(validationRegexps);

		HashMap<String, Integer>  associatedOpenDictionary = new HashMap<> ();
		for (XsdNode node : this.associatedOpenDictionary.keySet()) {
			associatedOpenDictionary.put(getPathForNode(node),
					this.associatedOpenDictionary.get(node));
		}
		mapping.setAssociatedOpenDictionary(associatedOpenDictionary);

		HashMap<String, Integer>  associatedClosedDictionary = new HashMap<> ();
		for (XsdNode node : this.associatedClosedDictionary.keySet()) {
			associatedClosedDictionary.put(getPathForNode(node),
					this.associatedClosedDictionary.get(node));
		}
		mapping.setAssociatedClosedDictionary(associatedClosedDictionary);

		HashMap<String, Integer> associatedDictionaryColumn = new HashMap<>();
		for (XsdNode node : this.associatedDictionaryColumn.keySet()) {
			associatedDictionaryColumn.put(getPathForNode(node),
					this.associatedDictionaryColumn.get(node));
		}
		mapping.setAssociatedDictionaryColumn(associatedDictionaryColumn);

		ArrayList<String> associatedFlatFiles = new ArrayList<>();
		for (XsdNode node : this.associatedFlatFiles) {
			associatedFlatFiles
					.add(getPathForNode(node));
		}
		mapping.setAssociatedFlatFiles(associatedFlatFiles);
		return mapping;
	}

    public void loadMapping(TreeMapping mapping) throws MalformedURLException {
		
		this.setId(mapping.getId());
		this.setAutoDuplicate(mapping.isAutoDuplicate());
		this.setManageChoices(mapping.isManageChoices());

		int i = 0;
		while (i < mapping.getExpendChoices().size()) {
			String path = mapping.getExpendChoices().get(i);
			i++;
			String choice = mapping.getExpendChoices().get(i);
			i++;
			if (choice != null) {
				redoChoice(path, choice);
			} else { /* duplication */
				duplicateNode(getNodeByPath(path));
			}
			
		}

		for (String path : mapping.getAssociatedAutogeneration()) {
			XsdNode node = getNodeByPath(path);
			node.useOnlyThis();
			associateAutoGenerateValue(node);
		}

        for (String path : mapping.getAssociatedValues().keySet()){

            XsdNode node = getNodeByPath(path);
			if (null == node) {
				// try again
				/**
				 * TODO: this has been done because 
				 * an exception was raised on the first attempt.
				 * We should find out why and fix it.
				 */
				log.error("try again: " + node);
				node = getNodeByPath(path);
			}
			
			if (node == null) {
				System.err.println("No node for associated value: " + path);
			} else {
				node.useOnlyThis();
				this.associatedValues.put(node, mapping.getAssociatedValues().get(path));
			}
		}

		for (String path :mapping.getValidationRegexps().keySet()) {
			XsdNode node = getNodeByPath(path);
			this.validationRegexps.put(node, mapping.getValidationRegexps()
					.get(path));
		}

		for (String path : mapping.getAssociatedOpenDictionary().keySet()) {
			this.associatedOpenDictionary.put(getNodeByPath(path),
					mapping.getAssociatedOpenDictionary().get(path));
		}


		for (String path : mapping.getAssociatedClosedDictionary().keySet()) {
			this.associatedClosedDictionary.put(getNodeByPath(path),
					mapping.getAssociatedClosedDictionary().get(path));
		}

		for (String path : mapping.getAssociatedDictionaryColumn().keySet()) {
			associatedDictionaryColumn.put(getNodeByPath(path),
					mapping.getAssociatedDictionaryColumn().get(path));
		}

		for (i = 0; i < mapping.getAssociatedFlatFiles().size(); i++) {
			rootNode.use();
			this.associatedFlatFiles
					.add(getNodeByPath(mapping.getAssociatedFlatFiles()
							.get(i)));
		}

		for (String path : mapping.getAssociatedDuplicableFields().keySet()) {
			XsdNode node = getNodeByPath(path);
			node.useOnlyThis();
			associatedDuplicableFields.put(getNodeByPath(path),
					mapping.getAssociatedDuplicableFields().get(path));
		}

		for (String path : mapping.getAssociatedFields().keySet()) {
			XsdNode node = getNodeByPath(path);
			node.useOnlyThis();
			associatedFields.put(getNodeByPath(path), mapping.getAssociatedFields()
					.get(path));
		}

		for (String path: mapping.getUnduplicableNodes()) {
			XsdNode node = getNodeByPath(path);
			unduplicableNodes.add(node);
		}
		
		log.error("Load mapping done");
		
	}

	/**
	 * @return Returns the associatedFlatFiles.
	 * 
	 * @uml.property name="associatedFlatFiles"
	 */
	public ArrayList<XsdNode> getAssociatedFlatFiles() {
		return associatedFlatFiles;
	}

	/**
	 * @return Returns the dictionaries.
	 * 
	 * @uml.property name="dictionaries"
	 */
	public DictionaryContainer getDictionaries() {
		return dictionaries;
	}

	/**
	 * @param dictionaries
	 *            The dictionaries to set.
	 * 
	 * @uml.property name="dictionaries"
	 */
	public void setDictionaries(DictionaryContainer dictionaries) {
		this.dictionaries = dictionaries;
	}

	/**
	 * @return Returns the flatFiles.
	 * 
	 * @uml.property name="flatFiles"
	 */
	public FlatFileContainer getFlatFiles() {
		return flatFiles;
	}

	/**
	 * @param flatFiles
	 *            The flatFiles to set.
	 * 
	 * @uml.property name="flatFiles"
	 */
	public void setFlatFiles(FlatFileContainer flatFiles) {
		this.flatFiles = flatFiles;
	}

	/**
	 * @return Returns the id.
	 * 
	 * @uml.property name="id"
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 *            The id to set.
	 * 
	 * @uml.property name="id"
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @param lastId
	 *            The lastId to set.
	 * 
	 * @uml.property name="lastId"
	 */
	public void setLastId(int lastId) {
		this.lastId = lastId;
	}

	/**
	 * @return Returns the name.
	 * 
	 * @uml.property name="name"
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            The name to set.
	 * 
	 * @uml.property name="name"
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @param expendChoices
	 *            The expendChoices to set.
	 */
	public void setExpendChoices(ArrayList<String> expendChoices) {
		super.expendChoices = expendChoices;
	}

	/**
	 * @return Returns the expendChoices.
	 */
	public ArrayList<String> getExpandChoices() {
		return super.expendChoices;
	}

	////////////////////////////////////////////////////////////////////////////////////////////

	public void print2(File outFile) throws IOException {
		Writer out = new BufferedWriter(new FileWriter(outFile));

		observable.setMessage("output file: " + outFile.getName());
		out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		out
				.write("<!-- created using XmlMakerFlattener v2 (http://code.google.com/p/xmlmakerflattener/) -->");
		getMessageManager().sendMessage("start marshalling to file :"
				+ outFile.getName() + " at " + new Date() , MessageManagerInt.simpleMessage);

		try {
			out.write(xmlMake());
		} catch (FileMakersException fme) {
			getMessageManager().sendMessage("Exception in main loop: " + fme, MessageManagerInt.errorMessage);
			/* TODO : manage exception */
		} catch (java.lang.NullPointerException npe) {
			getMessageManager().sendMessage("marshalling failed", MessageManagerInt.errorMessage);
		}
		
		getMessageManager().sendMessage("marshalling done, finished at " + new Date()
				, MessageManagerInt.simpleMessage);

		out.flush();
		out.close();

		observable.setMessage("marshalling done");
		observable.notifyObservers(observable.getMessage());
		observable.deleteObservers();
	}

	/**
	 * write the whole XML file
	 *
     */
	public String xmlMake() throws IOException, FileMakersException {
		return xmlMake((XsdNode) treeModel.getRoot());
	}

	/**
	 * write the XML code for a node
	 *  
	 */
	public String xmlMake(XsdNode node) throws IOException, FileMakersException {
		lastId = 0;		
		return xmlMakeElement(node);	
	}


	public String xmlMakeElement(XsdNode node) throws IOException,
			FileMakersException {
		
		if (!node.isUsed()) {
			return "";
		}
		
		String xmlCode = "";

		Iterator<XsdNode> children = getChildren(node);

		ArrayList<XsdNode> attributeList = new ArrayList<>();
		ArrayList<XsdNode> elementList = new ArrayList<>();
		ArrayList<XsdNode> groupList = new ArrayList<>();

		/*
		 * get every childs of the node get the structureType of the userElement
		 * and use the apropriate marshaller
		 */
		while (children.hasNext()) {
			XsdNode child = children.next();
			switch (((Annotated) child.getUserObject()).getStructureType()) {
			case Structure.ATTRIBUTE:
				attributeList.add(child);
				break;
			case Structure.ELEMENT: 
				if (child.isUsed()) {
					elementList.add(child);
				}
				break;
			case Structure.GROUP:
				getMessageManager().sendMessage("There should not be any group...." + child, MessageManagerInt.warningMessage);
				if (child.isUsed())
					groupList.add(child);
				break;
			}
		}

		indentation += "\t";

		HashMap<String, Object> maxOccurs = new HashMap<>();
		HashMap<String, Integer> minOccurs = new HashMap<>();

        for (XsdNode child : elementList) {
            /* initialisation if first occurence of the element */
            /* TODO: check if it works for duplicated nodes... */
            if (!maxOccurs.containsKey(child.toString())) {
                if (child.max != -1) {
                    maxOccurs.put(child.toString(), child.max);
                } else {
                    maxOccurs.put(child.toString(), UNBOUNDED);
                }
                minOccurs.put(child.toString(), child.min);
            }

            if (associatedDuplicableFields.get(child) != null) {
                /* marshall all subelemets */
                /* make filter */
                /* how many sub elements */

                String tmpPath = associatedDuplicableFields.get(child);
                if (pathFilter != null && !unduplicableNodes.contains(node)) {
                    String[] filters = pathFilter.split("\\.");
                    String[] paths = tmpPath.split("\\.");
                    String filteredPath = "";
                    for (int j = 0; j < filters.length; j++) {
                        paths[j] = String.valueOf(Integer.parseInt(filters[j])
                                + Integer.parseInt(paths[j]));
                    }
                    for (int j = 0; j < paths.length - 1; j++) {
                        filteredPath += paths[j] + ".";
                    }
                    filteredPath += paths[paths.length - 1];
                    tmpPath = filteredPath;
                }
                int nbDuplications = flatFiles.nbElements(tmpPath);

                String previousFilter = pathFilter;
                /* do not forget to apply previous filter to the new one!!! */
                String filter = "";

                for (int j = 0; j < (associatedDuplicableFields.get(child)).split("\\.").length; j++) {
                    if (previousFilter != null && previousFilter.split("\\.").length > j)
                        filter += previousFilter.split("\\.")[j] + ".";
                    else
                        filter += "0.";
                }

                filter += "0";
                for (int j = 0; j < nbDuplications; j++) {
                    int lastFilterIdx = Integer.parseInt(filter.substring(filter
                            .lastIndexOf(".") + 1))
                            + j;
                    pathFilter = filter.substring(0, filter.lastIndexOf(".") + 1)
                            + lastFilterIdx;
                    String xmlChildCode = xmlMakeElement(child);
                    /* update number of nodes found */
                    if (xmlChildCode != null) {
                        try {
                            maxOccurs.put(child.toString(), (Integer) maxOccurs.get(child
                                    .toString()) - 1);
                        } catch (ClassCastException e) {
                            /*
                             * ok, max is unbounded and exception is thrown when
                             * trying to cast String to Integer
                             */
                        }
                        minOccurs.put(child.toString(), minOccurs.get(child.toString()) - 1);
                    }
                    xmlCode += xmlChildCode;
                }
                pathFilter = previousFilter;
            } else if (associatedFlatFiles.contains(child)) {
                /* marshall all line */

                pushFlatFile(flatFiles.getFlatFile(associatedFlatFiles
                        .indexOf(child)));

                getMessageManager().sendMessage("[CREATE XML] from file: " + getCurrentFlatFile().fileURL
                        .getFile(), MessageManagerInt.simpleMessage);

                observable
                        .setCurrentFlatFile(getCurrentFlatFile().fileURL
                                .getFile());
                observable.setElement(node.toString());
                observable.indentation++;

                boolean endOfFile = false;
                getCurrentFlatFile().restartFile();

                /* if the first line contains title, pass througth it */
                if (getCurrentFlatFile().firstLineForTitles()) {
                    getCurrentFlatFile().nextLine();
                }
                int previousLineNumber = lineNumber;
                lineNumber = 0;

                while (!endOfFile) {
                    observable.setCurrentLine(lineNumber++);
                    try { /* get each line */
                        if (!getCurrentFlatFile().hasLine()) {
                            throw new IOException(
                                    "!getCurrentFlatFile().hasLine()");
                        }
                        String xmlChildCode = xmlMakeElement(child);

                        /* update number of nodes found */
                        if (xmlChildCode != null) {
                            try {
                                maxOccurs.put(child.toString(), (Integer) maxOccurs.get(child.toString()) - 1);
                            } catch (ClassCastException e) {
                                /*
                                 * ok, max is unbounded and exception is thrown when
                                 * trying to cast String to Integer
                                 */
                            }
                            minOccurs.put(child.toString(), minOccurs.get(child.toString()) - 1);
                        }
                        xmlCode += xmlChildCode;
                        getCurrentFlatFile().nextLine();
                    } catch (IOException e) { /* end of the file */
                        endOfFile = true;
                        getCurrentFlatFile().restartFile();
                    }
                }
                lineNumber = previousLineNumber;
                popFlatFile();
            } else {
                /* marshall element */
                String xmlChildCode = xmlMakeElement(child);

                /* update number of nodes found */
                if (xmlChildCode != null && !xmlChildCode.isEmpty()) {
                    try {
                        maxOccurs.put(child.toString(), (Integer) maxOccurs.get(child.toString()) - 1);
                    } catch (ClassCastException e) {
                        /*
                         * ok, max is unbounded and exception is throws when
                         * trying to cast String to Integer
                         */
                    }
                    minOccurs.put(child.toString(), minOccurs.get(child.toString()) - 1);
                    xmlCode += xmlChildCode;
                }
            }
        }

		
		indentation = indentation.substring(1);
		
		/* check number of each element */
		boolean errors = false;

		Iterator<Integer> mins = minOccurs.values().iterator();
		Iterator<Object> maxs = maxOccurs.values().iterator();
		for (String name : minOccurs.keySet()) {
			if (mins.next() > 0) {
				getMessageManager().sendMessage(printPath(node.getPath()) + ": a " + name + " is missing! (line : " + lineNumber + ")", MessageManagerInt.warningMessage);
				errors = true;
			}

			/* if a max is < 0, it means there are too many elements */
			try {
			Integer max = (Integer) maxs.next();
				if (max < 0) {
					getMessageManager().sendMessage(printPath(node.getPath()) + ": a " + name + " should be removed! (line : " + lineNumber + ")", MessageManagerInt.errorMessage);
					errors = true;
				}

			} catch (ClassCastException e) {
				/*
				 * ok, max is unbounded and exception is throws when trying to
				 * cast String to Integer
				 */
			}
		}
		
		/* attributes */
		ArrayList<String> checkedAttributes = new ArrayList<>();
		for (XsdNode attribute : attributeList) {
			checkedAttributes.add(attribute.getName());
			if (getValue(attribute) == null || getValue(attribute).isEmpty()) {
				if (attribute.isRequired) {
					getMessageManager().sendMessage(printPath(node.getPath()) + " attibute  " + attribute + " is required for " + node + " (line : " + lineNumber + ")", MessageManagerInt.warningMessage);
					errors = true;
				} 
				else {
					checkedAttributes.add("");
				}
			} else {
				checkedAttributes.add(getValue(attribute));
			}
		}

		if (errors) 
			return "";

		if (!xmlCode.trim().isEmpty()) {
			xmlCode += "\n"+ indentation;
		}
		
		String value = getValue(node);
		if (value != null && !value.trim().isEmpty()) {
			xmlCode += value;
		}

		boolean isEmptyElement = xmlCode.isEmpty();
		
		if (!isEmptyElement)
			return openElement(node, checkedAttributes, false) + xmlCode + closeElement(node, false);
		else return openElement(node, checkedAttributes, true);
	}
}