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
package psidev.psi.mi.filemakers.xmlFlattener.structure;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.tree.TreeNode;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exolab.castor.xml.schema.Annotated;
import org.exolab.castor.xml.schema.Structure;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import psidev.psi.mi.filemakers.xmlFlattener.mapping.TreeMapping;
import psidev.psi.mi.filemakers.xsd.AbstractXsdTreeStruct;
import psidev.psi.mi.filemakers.xsd.Utils;
import psidev.psi.mi.filemakers.xsd.XsdNode;

/**
 * 
 * This class overides the abstract class AbstractXslTreeStruct to provide a
 * tree representation of a XML schema, with management of transformation of an
 * XML file to a flat file.
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 * 
 */
public class XsdTreeStructImpl extends AbstractXsdTreeStruct {

	private static final Log log = LogFactory
     .getLog(XsdTreeStructImpl.class);
	
	/**
	 * Clean tree. If set to true, all unused nodes will be deleted from the tree.
	 * This is not desirable for the GUI, because the user may want to change the   
	 */
	private static boolean allowCleanTree = true;
	
//	public static final int NUMERIC_NUMEROTATION = 0;
//
//	public static final int HIGH_ALPHABETIC_NUMEROTATION = 1;
//
//	public static final int LOW_ALPHABETIC_NUMEROTATION = 2;
//
//	public static final int NO_NUMEROTATION = 3;
//
//	public int numerotation_type = NUMERIC_NUMEROTATION;

	private int curElementsCount = 0;

	/**
	 * the XML document to parse and transform into a flat file
	 */
	private Document document = null;

	private URL documentURL = null;

	/**
	 * the separator for the flat file
	 */
	private String separator = "\t";

	/**
	 * the node associated to a line of the flat file. if null, the printer will
	 * look for the deeper node that is an ancestor of every selection.
	 */
	private XsdNode lineXsdNode = null;

	/**
	 * true if the user has choosed a node that contains what he wants to see on
	 * a line of the flat file
	 */
	private boolean lineNodeIsSelected = false;

	/**
	 * the elements of the XML document associated to a line of the flat file.
	 * if null, the printer will look for the deeper node that is an ancestor of
	 * every selection.
	 */
	private ArrayList<Node> lineElements = null;

	/**
	 * type of marshaling: for creating a line with columns titles
	 */
	public final static int TITLE = 0;

	/**
	 * Indicate if the document should be validated. Validating a document may
	 * take time, but it is necessary for instance for using xml id (e.g. PSI-MI
	 * xml 1.0)
	 */
	private static boolean validateDocument = false;

	/**
	 * type of marshaling: for not creating a line with columns titles
	 */
	public final static int FULL = 1;

	/**
	 * create a new instance of XslTree The nodes will not be automatically
	 * duplicated even if the schema specify that more than one element of this
	 * type is mandatory
	 */
	public XsdTreeStructImpl() {
		super(false, false);
	}

	/**
	 * this map contains regular expression used to filter XML node if a node do
	 * not validate the regexp, itself or its parent element (in case of
	 * attribute) will be ignored
	 */
	private HashMap<XsdNode, String> elementFilters = new HashMap<XsdNode, String>();

	/**
	 * set the separator for the flat file
	 * 
	 * @param s
	 *            the separator
	 */
	public void setSeparator(String s) {
		separator = s;
	}

	/**
	 * check for errors on this node (lack of associations...) and return an
	 * array of understandable Strings describing the errors
	 * 
	 * @param node
	 *            the node to check
	 * @return an array of Strings describing the errors found
	 */
	public boolean check(XsdNode node) {
		return true;
	}

	/**
	 * this method should reinitialize every variable making reference to the
	 * actual tree, such as any <code>List</code> used to make associations to
	 * externals objects.
	 * 
	 * set selection as a new <code>ArrayList</code>
	 */
	public void emptySelectionLists() {
		ArrayList<XsdNode> selectionsCopy = new ArrayList<XsdNode>();
		selectionsCopy.addAll(selections);
		Iterator<XsdNode> it = selectionsCopy.iterator();
		while (it.hasNext()) {
			XsdNode node = it.next();
			unselectNode(node);
		}
		lineXsdNode = null;
		elementFilters = new HashMap<XsdNode, String>();
	}

	// HashMap referencedElements = new HashMap();

	/**
	 * Open a frame to choose an XML document and load it.
	 * 
	 */
	public void loadDocument(URL url) throws FileNotFoundException,
			NullPointerException, MalformedURLException, IOException,
			SAXException {
		maxCounts = new HashMap<XsdNode, Integer>();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(validateDocument);

		factory.setAttribute(SCHEMA_LANGUAGE, XML_SCHEMA);
		factory.setAttribute(SCHEMA_SOURCE, schemaURL);
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			log.debug("XML document url: "+url.toString());
			builder.setErrorHandler(xmlErrorHandler);
			document = builder.parse(url.toString());
			this.documentURL = url;

			/* get all references */
			log.debug("get keys/keyRefs");
			buidKeyMaps();
			for (String refer : refType2referedType.keySet()) {
				String refered = refType2referedType.get(refer);
				log.debug("found reftype: " + refer + " refers "
						+ refered);
			}
			log.debug("done");

			log.debug("document parsed ... get elements");
			setLineNode(lineXsdNode);

			Utils.lastVisitedDirectory = url.getPath();
			Utils.lastVisitedDocumentDirectory = url.getPath();
		} catch (ParserConfigurationException e) {
			/** TODO: manage excepton */
		}
	}

	private HashMap<String, Node> xsKeyNodes = new HashMap<String, Node>();

	private void getKeyNodes(String keyName, String keySelector, String keyField) {

		if (document == null)
			return;

		String[] path = keySelector.split("/");

		/* the node that contains all keys */
		/* find the parent node */
		Node nodeContainer = getContainer(document, path, 0);

		if (nodeContainer == null)
			return;
		log.debug("found list of refered node: "
				+ nodeContainer.getNodeName());

		for (int i = 0; i < nodeContainer.getChildNodes().getLength(); i++) {
			Node child = nodeContainer.getChildNodes().item(i);
			String name = child.getNodeName();
			/* get refId name */
			String idFieldName = getReferedIdFieldName(name);
			if (child.hasAttributes()) {
				for (int j = 0; j < child.getAttributes().getLength(); j++) {
					if (child.getAttributes().item(j).getNodeName().equals(
							idFieldName)) {
						String ref = child.getAttributes().item(j)
								.getNodeValue();
						xsKeyNodes.put(keyName + "#" + ref, child);// keyName
						log.debug("add: " + keyName + "#" + ref);
					}
				}
			}

		}

	}

	private Node getContainer(Node node, String[] path, int startIdx) {

		if (startIdx == path.length - 1)
			return node;
		if (false == node.hasChildNodes()) {
			return null;
		}

		for (int j = 0; j < node.getChildNodes().getLength(); j++) {
			if (node.getChildNodes().item(j).getNodeName().equals(
					path[startIdx])) {
				return getContainer(node.getChildNodes().item(j), path,
						++startIdx);
			}
		}
		return null;
	}

	private String getReferedIdFieldName(String key) {
		return "id";
	}

	private void buidKeyMaps() {

		log.debug("get keys");
		for (Node node : keyz) {
			String keyName = null;
			String keySelector = null;
			String keyField = null;

			if (node.hasAttributes()) {
				for (int i = 0; i < node.getAttributes().getLength(); i++) {
					if (node.getAttributes().item(i).getNodeName().equals(
							"name")) {
						keyName = node.getAttributes().item(i).getNodeValue();

					}
				}
			}

			for (int i = 0; i < node.getChildNodes().getLength(); i++) {
				Node child = node.getChildNodes().item(i);

				if (child.getNodeName().equals("xs:selector")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
							keySelector = getXpath(child.getParentNode()
									.getParentNode())
									+ "/"
									+ child.getAttributes().item(j)
											.getNodeValue();
						}
					}
				} else if (child.getNodeName().equals("xs:field")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
							keyField = child.getAttributes().item(j)
									.getNodeValue().replace("@", "");
						}
					}
				}
			}
			getKeyNodes(keyName, keySelector, keyField);

		}

		log.debug("get keyRefs");
		for (Node node : keyRefs) {
			String keyRefName = null;
			String keyRefRefer = null;
			String keyRefSelector = null;
			String keyRefField = null;

			if (node.hasAttributes()) {
				for (int i = 0; i < node.getAttributes().getLength(); i++) {
					if (node.getAttributes().item(i).getNodeName().equals(
							"name")) {
						keyRefName = node.getAttributes().item(i)
								.getNodeValue();
					} else if (node.getAttributes().item(i).getNodeName()
							.equals("refer")) {
						keyRefRefer = node.getAttributes().item(i)
								.getNodeValue();
					}
				}
			}
			for (int i = 0; i < node.getChildNodes().getLength(); i++) {
				Node child = node.getChildNodes().item(i);
				if (child.getNodeName().equals("xs:selector")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
							keyRefSelector = child.getAttributes().item(j)
									.getNodeValue();
						}
					}
				} else if (child.getNodeName().equals("xs:field")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
							keyRefField = child.getAttributes().item(j)
									.getNodeValue();
						}
					}
				}
			}
			refType2referedType.put(getSchemaXpath(node.getParentNode()) + "/"
					+ keyRefSelector, keyRefRefer);
		}
	}

	/**
	 * 
	 * @uml.property name="lineNode"
	 */
	public void setLineNode(XsdNode lineNode) {
		this.lineXsdNode = lineNode;
		maxCounts = new HashMap<XsdNode, Integer>();

		lineElements = getNodes(lineNode.getPath());
		treeModel.reload(lineNode);

		lineNodeIsSelected = true;
	}

	public String getInfos(XsdNode node) {
		String infos = super.getInfos(node);
		infos += "selected: " + selections.contains(node) + "\n";
		return infos;
	}

	/**
	 * this <code>HashMap</code> keep the maximum amount of a node type found in
	 * the file for a type of node. The key is the String association of the
	 * name of the parent and the name of the node
	 */
	public HashMap<XsdNode, Integer> maxCounts = new HashMap<XsdNode, Integer>();

	/**
	 * follow a path in the document to find corresponding element
	 * 
	 * @return the node in the XML document that found by following the path
	 * 
	 */
	public ArrayList<Node> getNodes(TreeNode[] path) {
		if (document == null)
			return null;

		Node value = document.getDocumentElement();
		ArrayList<Node> list = getXmlElements(path, value, 0);
		curElementsCount = list.size();
		log.debug(curElementsCount + " elements found for selection.");
		return list;

	}

	public ArrayList<Node> getXmlElements(TreeNode[] path, Node xmlNode,
			int pathIndex) {
		ArrayList<Node> list = new ArrayList<Node>();

		if (pathIndex < path.length - 1) {
			NodeList children = xmlNode.getChildNodes();

			for (int j = 0; j < children.getLength(); j++) {
				if (((XsdNode) path[pathIndex + 1]).toString().compareTo(
						children.item(j).getNodeName()) == 0) {
					list.addAll(getXmlElements(path, children.item(j),
							pathIndex + 1));
				}
			}

			return list;
		} else {
			list.add(xmlNode);
			return list;
		}
	}

	/**
	 * 
	 * @param element
	 *            an element of the XML document
	 * @return value of this element if it exists, an empty String else
	 */
	public String getElementValue(Element element) {
		try {
			NodeList children = element.getChildNodes();
			String value = "";
			for (int i = 0; i < children.getLength(); i++) {
				if (children.item(i).getNodeType() == Node.ATTRIBUTE_NODE) {
					if (elementFilters.containsKey(children.item(i))) {
						try {
							String value2 = children.item(i).getNodeValue();
							/** TODO: done for managing filter */
							if (false == value2.matches((String) elementFilters
									.get(children.item(i)))) {
								return "";
							}
						} catch (NullPointerException e) {
							log.debug(e);
							return "";
						}
					}

				}

				if (children.item(i).getNodeName() == "#text")
					value = children.item(i).getNodeValue();
			}

			return value;
		} catch (NullPointerException e) {
			/** element is null */
			return "";
		}

	}

	/**
	 * look in the XML schema for the deepest node that is an ancestor of every
	 * nodes selected @ return the deepest node in the XML schema that is an
	 * Ancestor of every nodes selected
	 */
	public void setXmlRoot() {
		if (lineNodeIsSelected)
			return;

		if (selections.size() == 0) {
			lineXsdNode = (XsdNode) treeModel.getRoot();
			lineElements = getNodes(lineXsdNode.getPath());
			return;
		}

		XsdNode value = rootNode;
		XsdNode tmp = null;
		XsdNode select = null;
		XsdNode lastDuplicable = null;
		// go down while no more than one child is used and current node
		// is not selected

		// keep last node duplicable

		Enumeration<XsdNode> children = value.children();
		int nb = 0;
		while (nb <= 1 && !selections.contains(tmp)) {

			if (children.hasMoreElements()) {
				XsdNode child;
				child = children.nextElement();
				if (child.isUsed()) {
					tmp = child;
					nb++;
				}
				if (selections.contains(child)) {
					select = child;
				}
			} else {
				nb = 0;
				if (tmp.isDuplicable())
					lastDuplicable = tmp;
				value = tmp;
				children = value.children();
			}
		}
		if (nb <= 1)
			value = select;
		log.debug("[PSI makers: flattener] root selected: "
				+ value.toString());

		lineXsdNode = lastDuplicable;

		lineElements = getNodes(lineXsdNode.getPath());

	}

	/**
	 * true if currently printed element is the first of a line. Used to know
	 * if a separator is needed when printing the flat file.
	 */
	public boolean firstElement = true;

	/**
	 * create a flat file separated by specified separator containing every
	 * element contained in the XML document and selected on the tree, with a
	 * first line that contains the titles of columns
	 * 
	 * @param out
	 *            the <code>writer</code> where to print the file
	 * @throws IOException
	 */
	public void write(Writer out) throws IOException {
		/*
		 * get the first interesting node, ie the deepest one that is an
		 * ancestor of every selected node, in the schema and corresponding
		 * nodes in the the document
		 */
		setXmlRoot();
		firstElement = true;
		/* marshall once for title */

		if (allowCleanTree)
			lineXsdNode.clean();

		out.write(getTitle(lineXsdNode) + "\n");
		out.flush();

		firstElement = true;
		/* Marshal each element */
		for (int i = 0; i < lineElements.size(); i++) {
			firstElement = true;
			writeNode(lineXsdNode, (Element) lineElements.get(i), out, false);
			out.write("\n");
			out.flush();
		}
	}

	/**
	 * get the maximum amount of element of a type as child of another type of
	 * element in the XML document. It is used to know how many columns have to
	 * be created in the flat file, as even empty ones have to be printed.
	 */
	public int getMaxCount(XsdNode xsdNode) {
		XsdNode originalNode = xsdNode;
		/* if no document loaded */
		if (lineElements == null) {
			return 0;
		}
		/* max count already computed */
		if (maxCounts.containsKey(xsdNode)) {
			return maxCounts.get(xsdNode).intValue();
		}

		int count = 0;
		int max = 0;

		/** for attributes get number of parent element */
		if (((Annotated) xsdNode.getUserObject()).getStructureType() == Structure.ATTRIBUTE) {
			xsdNode = (XsdNode) xsdNode.getParent();
		}

		for (Node lineElement : lineElements) {
			count = getMaxCount(lineElement, lineXsdNode, xsdNode, xsdNode
					.pathFromAncestorEnumeration(lineXsdNode));
			if (count > max)
				max = count;
		}

		/* the fields are kept even if no element have been found */
		if (max < xsdNode.min) {
			max = xsdNode.min;
		}
		if (max == 0) {
			max = 1;
		}

		/* keep the result */
		maxCounts.put(originalNode, new Integer(max));

		return max;
	}

	/**
	 * get the maximum amount of element of a type as child of another type of
	 * element in the XML document. It is used to know how many columns have to
	 * be created in the flat file, as even empty ones have to be printed.
	 * 
	 * @param element
	 *            the element in the XML document
	 * @param parent
	 *            the parent of the node on the tree
	 * @param target
	 *            the node on the tree
	 * @param path
	 *            the path to access to the node
	 */
	public int getMaxCount(Node element, XsdNode parent, XsdNode target,
			Enumeration<XsdNode> path) {
		if (target == parent) {
			if (elementFilters.containsKey(parent)) {
				String value = ((Element) element).getAttributeNode(
						target.toString()).getNodeValue();
				/** TODO: done for managing filter */
				if (false == value.matches(elementFilters.get(target))) {
					log.debug(target.getName() + " filtered");
					return 0;
				}
			}
			return 1;
		}

		int currentMax = 0;
		int totalCount = 0;
		int max = 0;

		path.nextElement();
		XsdNode nextNode = path.nextElement();

		/* get all childrens and refs */
		NodeList childrens = element.getChildNodes();

		for (int indexChildrens = 0; indexChildrens < childrens.getLength(); indexChildrens++) {
			Node xmlChild = childrens.item(indexChildrens);

			/**
			 * check for the name: a single node in the tree could have numerous
			 * corresponding elements in the XML document, that could be either
			 * the node itself or a reference.
			 */
			if (xmlChild.getNodeType() == Structure.ATTRIBUTE) {

				Enumeration<XsdNode> xsdChildrens = nextNode.children();
				while (xsdChildrens.hasMoreElements()) {
					XsdNode xsdChild = xsdChildrens.nextElement();

					if (elementFilters.containsKey(xsdChild)) {
						try {
							String value = ((org.apache.xerces.dom.DeferredTextImpl) xmlChild)
									.getNodeValue();
							/** TODO: done for managing filter */
							if (false == value.matches(elementFilters
									.get(xmlChild))) {
								return 0;
							}
						} catch (Exception e) {
							/** TODO : manage exception */
						}
					}
				}
			}

			/* direct childrens */
			if (xmlChild.getNodeName().equals(nextNode.toString())) {
				currentMax = getMaxCount(xmlChild, nextNode, target, target
						.pathFromAncestorEnumeration(nextNode));
				totalCount += currentMax;
				if (((XsdNode) target.getParent()).toString().compareTo(
						parent.toString()) == 0) {
					max += currentMax;
				} else {
					if (currentMax > max)
						max = currentMax;
				}
			}

			/* references */
			else if (isRefType(xmlChild.getNodeName())) {
				Element ref = getElementById(((Element) xmlChild) // document.
						.getAttribute(refAttribute));
				if (ref != null
						&& ref.getNodeName().compareTo(nextNode.toString()) == 0) {
					// count++;
					currentMax = getMaxCount(ref, nextNode, target, target
							.pathFromAncestorEnumeration(nextNode));
					totalCount += currentMax;
					if (((XsdNode) target.getParent()).toString().compareTo(
							parent.toString()) == 0) {
						max += currentMax;
					} else if (currentMax > max) {
						max = currentMax;
					}
				}
			}

			/* key ref */
			else if (isXsRefPath(xmlChild)) {
				Element ref = this.getElementByKeyRef(xmlChild); // ((Element)
				// child)
				// //document.
				// .getAttribute(refAttribute), child.getNodeName());
				if (ref != null
						&& ref.getNodeName().compareTo(nextNode.toString()) == 0) {
					// count++;
					currentMax = getMaxCount(ref, nextNode, target, target
							.pathFromAncestorEnumeration(nextNode));
					totalCount += currentMax;
					if (((XsdNode) target.getParent()).toString().compareTo(
							parent.toString()) == 0) {
						max += currentMax;
					} else if (currentMax > max) {
						max = currentMax;
					}
				}
			}
			/* ID */
			else if (isRefType(xmlChild.getNodeName())) {
				Element ref = getElementById(((Element) xmlChild) // document.
						.getAttribute(refAttribute));
				if (ref != null
						&& ref.getNodeName().compareTo(nextNode.toString()) == 0) {
					// count++;
					currentMax = getMaxCount(ref, nextNode, target, target
							.pathFromAncestorEnumeration(nextNode));
					totalCount += currentMax;
					if (((XsdNode) target.getParent()).toString().compareTo(
							parent.toString()) == 0) {
						max += currentMax;
					} else if (currentMax > max) {
						max = currentMax;
					}
				}
			}

		}
		return max;
	}

	/**
	 * add to the flat file the content of a node
	 * 
	 * @param xsdNode
	 *            the node in the tree to parse
	 * @param xmlElement
	 *            the element in tghe XML document
	 * @param marshallingType
	 *            title or full parsing
	 * @param out
	 * @throws IOException
	 */
	public boolean writeNode(XsdNode xsdNode, Node xmlElement, Writer out,
			boolean empty) throws IOException {

		/** first check if the element do not have to be filtered */
		if (false == empty) {
			Enumeration<XsdNode> children = xsdNode.children();
			while (children.hasMoreElements()) {
				XsdNode child = children.nextElement();

				if (((Annotated) child.getUserObject()).getStructureType() == Structure.ATTRIBUTE) {
					if (elementFilters.containsKey(child)) {
						String value = ((Element) xmlElement).getAttributeNode(
								child.toString()).getNodeValue();
						/** TODO: done for managing filter */
						Pattern p = Pattern.compile(elementFilters.get(child));
						Matcher m = p.matcher(value);
						boolean match = m.matches();

						if (false == match) {
							return false;
						}
					}
				}
			}
		}

		if (false == xsdNode.isUsed()) {
			return false;
		}

		if (selections.contains(xsdNode)) {
			if (xmlElement != null || empty) {
				String value = getElementValue((Element) xmlElement);
				/** TODO: done for managing filter */
				/** if empty marshaling, we do not care about filters */
				if (elementFilters.containsKey(xsdNode) && false == empty
						&& elementFilters.get(xsdNode) != null
						&& elementFilters.get(xsdNode).length() > 0) {
					if (value.matches(elementFilters.get(xsdNode))) {
						if (firstElement)
							firstElement = false;
						else
							out.write(separator);
						out.write(getElementValue((Element) xmlElement));
					}
				} else {
					if (firstElement)
						firstElement = false;
					else
						out.write(separator);
					out.write(getElementValue((Element) xmlElement));
				}
			}
		}

		// Enumeration
		Enumeration<XsdNode> children = xsdNode.children();
		while (children.hasMoreElements()) {
			XsdNode child = children.nextElement();
			if (child.isUsed()) {
				switch (((Annotated) child.getUserObject()).getStructureType()) {
				case Structure.ELEMENT:
					/** number of element found */
					int cpt = 0;
					/** number of elements really marshalled, ie not filtered */
					int nbElementFound = 0;
					/* create a NodeList with all childs with tagname */
					if (xmlElement != null) {
						NodeList allElements = xmlElement.getChildNodes();
						ArrayList<Node> elements = new ArrayList<Node>();
						/**
						 * number of element found: could be lower than
						 * elements's length due to filters
						 */
						for (int i = 0; i < allElements.getLength(); i++) {
							if (allElements.item(i).getNodeName().compareTo(
									child.toString()) == 0) {
								elements.add(allElements.item(i));
							}

							/* get refence by xs:key */
							else if (isXsRefPath(allElements.item(i))) {
								Element ref = // document.
								getElementByKeyRef(allElements.item(i));

								log.debug("ref: "+allElements.item(i).getNodeName()+": "+allElements.item(i).getChildNodes().item(0).getNodeValue());
								if (ref != null
										&& ref.getNodeName().compareTo(
												child.toString()) == 0) {
									elements.add(ref);
								}
							}

							/* get references by XML id */
							else if (isRefType(allElements.item(i)
									.getNodeName())) {
								Element ref = // document.
								getElementById(((Element) allElements.item(i))
										.getAttribute(refAttribute));
								if (ref != null
										&& ref.getNodeName().compareTo(
												child.toString()) == 0) {
									elements.add(ref);
								}
							}

						}
						while (cpt < elements.size()) {
							boolean notEmptyElement = writeNode(child, elements
									.get(cpt), out, false);
							cpt++;
							if (notEmptyElement)
								nbElementFound++;
						}
					}
					int maxCount = getMaxCount(child);
					while (nbElementFound < maxCount) {
						writeNode(child, null, out, true);
						nbElementFound++;
					}
					break;
				case Structure.ATTRIBUTE:
					if (firstElement)
						firstElement = false;
					else
						out.write(separator);

					if (xmlElement != null) {
						try {
						out.write(((Element) xmlElement).getAttributeNode(
								child.toString()).getNodeValue());
						} catch (Exception e) {
							log.debug(child.getName()+"/"+xmlElement.getNodeName(), e);
						}
					}
					break;
				default:
					log.debug("[PSI makers: flattener] ERROR: the node is neither an attribute nor an element");
				}
			}
		}
		return true;
	}

	ArrayList<Integer> currentPath = new ArrayList<Integer>();

	private String getCurrentPath() {
		String path = "";
		for (Integer i : currentPath) {
			if (i > 0) {
				if (false == "".equals(path))
					path += ".";
				path += i;
			}
		}

		if (false == "".equals(path))
			path = "-" + path;

		return path;
	}

	/**
	 * Such as write but return a String instead of writing in a file. Only for
	 * the marshalling type title.
	 * 
	 * @param xsdNode
	 * @param element
	 * @param marshallingType
	 * @return
	 */
	public String getTitle(XsdNode xsdNode) {
		String out = "";

		if (false == xsdNode.isUsed()) {
			return out;
		}

		if (selections.contains(xsdNode)) {
			if (firstElement)
				firstElement = false;
			else
				out += separator;
			out += xsdNode.getName() + getCurrentPath();// nextNumber(node);
		}

		Enumeration<XsdNode> children = xsdNode.children();

		if (xsdNode.isDuplicable() && false == currentPath.isEmpty()) {
			Integer i = currentPath.remove(currentPath.size() - 1);
			currentPath.add(i + 1);
		}

		while (children.hasMoreElements()) {
			XsdNode xsdChild = children.nextElement();

			if (xsdChild.isUsed()) {
				switch (((Annotated) xsdChild.getUserObject())
						.getStructureType()) {
				case Structure.ELEMENT:

					int cpt = 0;
					/* create a NodeList with all childs with tagname */
					int maxCount = getMaxCount(xsdChild);

					if (xsdChild.isDuplicable()) {
						currentPath.add(0);
					}

					while (cpt < maxCount) {
						out += getTitle(xsdChild);
						cpt++;
					}

					if (xsdChild.isDuplicable()) {
						currentPath.remove(currentPath.size() - 1);
					}
					break;
				case Structure.ATTRIBUTE:
					if (firstElement)
						firstElement = false;
					else
						out += separator;

					out += xsdChild.getName() + getCurrentPath();// nextNumber(child);

					break;
				default:
					log.debug("[PSI makers: flattener] ERROR: the node is neither an attribute nor an element");
				}
			}
		}

		return out;
	}

	public ArrayList<XsdNode> selections = new ArrayList<XsdNode>();

	public void addName(XsdNode node, String name) {
		associatedNames.put(node, name);
		node.setName(name);
		treeModel.reload(node);
	}

	public void addFilter(XsdNode node, String regexp) {
		elementFilters.remove(node);
		if (regexp != null && !regexp.trim().equals(""))
			elementFilters.put(node, regexp.trim());
	}

	public void selectNode(XsdNode xsdNode) {
		selections.add(xsdNode);
		xsdNode.use();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(xsdNode);

		setXmlRoot();
	}

	public void unselectNode(XsdNode xsdNode) {
		selections.remove(xsdNode);
		xsdNode.unuse();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(xsdNode);

		setXmlRoot();
	}

	public TreeMapping getMapping() {
		TreeMapping mapping = new TreeMapping();

		if (this.documentURL != null)
			mapping.setDocumentURL(Utils.relativizeURL(this.documentURL)
					.getPath());
		if (this.getSchemaURL() != null)
			mapping.setSchemaURL(Utils.relativizeURL(this.getSchemaURL())
					.getPath());

		if (this.lineXsdNode != null)
			mapping.setLineNode(getPathForNode(this.lineXsdNode));
		mapping.setSeparator(this.separator);

		mapping.setExpendChoices(this.expendChoices);

		ArrayList<String> selections = new ArrayList<String>();
		for (int i = 0; i < this.selections.size(); i++) {
			selections.add(getPathForNode(this.selections.get(i)));
		}
		mapping.setSelections(selections);

		HashMap<String, String> associatedNames = new HashMap<String, String>();
		
		for (XsdNode node : this.associatedNames.keySet()) {
			associatedNames.put(getPathForNode(node), this.associatedNames
					.get(node));
		}
		mapping.setAssociatedNames(associatedNames);

		HashMap<String, String> elementFilters = new HashMap<String, String>();
		
		for (XsdNode node : this.elementFilters.keySet()) {
			elementFilters.put(getPathForNode(node), this.elementFilters
					.get(node));
		}
		mapping.setElementFilters(elementFilters);

		return mapping;
	}

	/**
	 * add to the flat file the content of a node
	 * 
	 * @param node
	 *            the node in the tree to parse
	 * @param element
	 *            the element in tghe XML document
	 * @param marshallingType
	 *            title or full parsing
	 * @param out
	 * @throws IOException
	 */
	public String marshallNode(XsdNode node, Node element) throws IOException {
		String marshalling = "";
		if (!node.isUsed()) {
			return marshalling;
		}

		if (selections.contains(node)) {
			if (firstElement)
				firstElement = false;
			else
				marshalling += separator;

			Enumeration<XsdNode> children = node.children();
			boolean filtered = false;
			while (children.hasMoreElements()) {
				XsdNode child = children.nextElement();

				if (((Annotated) child.getUserObject()).getStructureType() == Structure.ATTRIBUTE) {
					if (elementFilters.containsKey(child)) {
						// try {
						String value = ((Element) element).getAttributeNode(
								child.toString()).getNodeValue();
						/** TODO: done for managing filter */
						if (!value.matches(elementFilters.get(child))) {
							filtered = true;
						}
					}
				}
			}

			if (element != null && filtered == false) {
				marshalling += getElementValue((Element) element);
			}
		}

		Enumeration<XsdNode> children = node.children();
		while (children.hasMoreElements()) {
			XsdNode child = children.nextElement();
			if (child.isUsed()) {
				switch (((Annotated) child.getUserObject()).getStructureType()) {
				case Structure.ELEMENT:
					int cpt = 0;
					/* create a NodeList with all childs with tagname */
					if (element != null) {
						NodeList allElements = element.getChildNodes();
						ArrayList<Node> elements = new ArrayList<Node>();
						for (int i = 0; i < allElements.getLength(); i++) {
							if (allElements.item(i).getNodeName().compareTo(
									child.toString()) == 0) {
								elements.add(allElements.item(i));
							}

							/* get references */
							else if (isXsRefPath(allElements.item(i))) {
								Element ref = getElementByKeyRef((Element) allElements
										.item(i));
								System.out.println("ref2: "+ref.getNodeName());
								if (ref != null
										&& ref.getNodeName().compareTo(
												child.toString()) == 0) {
									elements.add(ref);
								}
							}

							/* get references */
							else if (isRefType(allElements.item(i)
									.getNodeName())) {
								Element ref = getElementById(((Element) allElements.item(i))
										.getAttribute(refAttribute));
								if (ref != null
										&& ref.getNodeName().compareTo(
												child.toString()) == 0) {
									elements.add(ref);
								}
							}
						}
						while (cpt < elements.size()) {
							marshalling += marshallNode(child, elements
									.get(cpt));
							cpt++;
						}
					}
					int maxCount = getMaxCount(child);
					while (cpt < maxCount) {
						marshalling += marshallNode(child, null);
						cpt++;
					}
					break;
				case Structure.ATTRIBUTE:
					if (firstElement)
						firstElement = false;
					else
						marshalling += separator;

					if (element != null) {
						marshalling += ((Element) element).getAttributeNode(
								child.toString()).getNodeValue();
					}
					break;
				default:
					log.debug("[PSI makers: flattener] ERROR: the node is neither an attribute nor an element");
				}
			}
		}
		return marshalling;
	}

	public void loadMapping(TreeMapping mapping) throws IOException,
			SAXException {
		if (mapping.documentURL != null)
			this.setDocumentURL(new File(mapping.documentURL).toURL());
		
		if (mapping.getSchemaURL() != null)
			this.setSchemaURL(new File(mapping.getSchemaURL()).toURL());

		File schema = new File(Utils.absolutizeURL(schemaURL).getPath());

		if (false == schema.exists()) {
			log.error("file "+mapping.getSchemaURL()+" not found");
			System.exit(1);
		}
		
		loadSchema(schema.toURI().toURL());

//		ArrayList<String> expandedChoices = new ArrayList<String>();
//		
//		expandedChoices.addAll(mapping.expendChoices);
//		
//		this.setExpendChoices(mapping.expendChoices);
//
//		for (String path : expandedChoices) {
//			super.extendPath(super.getNodeByPath(path));
//		}

		this.setExpendChoices(mapping.expendChoices);

		int i = 0;
		while (i < expendChoices.size()) {
			String path = expendChoices.get(i);
			i++;
			super.extendPath(super.getNodeByPath(path));
		}

		
		
		if (mapping.getLineNode() != null)
			this.setLineNode(getNodeByPath(mapping.getLineNode()));
		
		if (documentURL != null)
			this.loadDocument(documentURL);

		this.setSeparator(mapping.separator);

		for (i = 0; i < mapping.selections.size(); i++) {
			XsdNode xsdNode = getNodeByPath((String) mapping.selections.get(i));
			selectNode(xsdNode);
		}

		for (String path : mapping.associatedNames.keySet()) {
			String field = (String) mapping.associatedNames.get(path);
			XsdNode node = getNodeByPath(path);
			addName(node, field);
		}

		for (String path : mapping.elementFilters.keySet()) {
			String field = (String) mapping.elementFilters.get(path);
			XsdNode node = getNodeByPath(path);
			this.elementFilters.put(node, field);
		}

	}

	/**
	 * @return Returns the documentURI.
	 * 
	 * @uml.property name="documentURL"
	 */
	public URL getDocumentURL() {
		return documentURL;
	}

	/**
	 * @param documentURI
	 *            The documentURI to set.
	 * 
	 * @uml.property name="documentURL"
	 */
	public void setDocumentURL(URL documentURL) {
		this.documentURL = documentURL;
	}

	/**
	 * @param schemaURI
	 *            The schemaURI to set.
	 */
	public void setSchemaURI(URL schemaURL) {
		this.schemaURL = schemaURL;
	}

	/**
	 * @return Returns the lineNode.
	 */
	public XsdNode getLineNode() {
		return lineXsdNode;
	}

	/**
	 * keep current values for referenced fields
	 * 
	 * @uml.property name="associatedValues"
	 */
	public HashMap<XsdNode, String> associatedNames = new HashMap<XsdNode, String>();

	/**
	 * @return Returns the curElementCount.
	 */
	public int getCurElementCount() {
		return curElementsCount;
	}

//	private String nextNumber(XsdNode node) {
//		String alphabetHigh = "_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
//		String alphabetLow = "_abcdefghijklmnopqrstuvwxyz";
//		int count = getMaxCount(node, lineXsdNode) + 1;
//		if (count > 1) {
//			int num = node.nextNumber();
//			num = (num) % count;
//			if (numerotation_type == HIGH_ALPHABETIC_NUMEROTATION
//					&& num < alphabetHigh.length())
//				return "" + alphabetHigh.charAt(num);
//			if (numerotation_type == LOW_ALPHABETIC_NUMEROTATION
//					&& num < alphabetHigh.length())
//				return "" + alphabetLow.charAt(num);
//
//			if (numerotation_type == NUMERIC_NUMEROTATION)
//				return "" + num;
//			return "" + num;
//		}
//		return "";
//	}

	/**
	 * set count for each node to 0. the count is used for the display of the
	 * title line, and this function will be used before updating the preview or
	 * before printing the file.
	 */
	public void resetCount() {
		resetCount(lineXsdNode);
	}

	private void resetCount(XsdNode node) {
		node.cpt = 0;
		int nbChildren = node.getChildCount();
		for (int i = 0; i < nbChildren; i++) {
			resetCount((XsdNode) node.getChildAt(i));
		}
	}

	public int getMaxCount(XsdNode node, XsdNode parent) {
		if (node == parent) {
			return getMaxCount(node);
		}
		Enumeration<XsdNode> e = node.pathFromAncestorEnumeration(parent);
		e.nextElement();
		XsdNode nextNode = e.nextElement();
		return getMaxCount(parent) * getMaxCount(node, nextNode);
	}

	/**
	 * get Element referred by this id, according to the XML id specification
	 * 
	 * @param id
	 * @return
	 */
	private Element getElementById(String id) {
		Element ref = document.getElementById(id);
		return ref;
	}

	/**
	 * get element referred, according to the key/keyRef xs specification
	 * 
	 * @param node
	 * @return
	 */
	private Element getElementByKeyRef(Node node) {
		Element ref;

		String refType = getDocumentXpath(node);
		/* get ref attribute */
		String refId = node.getFirstChild().getNodeValue();
		if (refId == null || refId.equals("")) {
			for (int i = 0; i < node.getAttributes().getLength(); i++) {
				if (node.getAttributes().item(i).getNodeName().equals(
						refAttribute)) {
					refId = node.getAttributes().item(i).getNodeValue();
				}
			}
		}

		String referedType = refType2referedType.get(refType);

		ref = (Element) xsKeyNodes.get(referedType + "#" + refId);
		return ref;
	}

	private String getXpath(Node node) {
		String xpath = "";
		if (node.getParentNode() != null) {
			xpath = getXpath(node.getParentNode());
		}
		String name = getName(node);

		if (name != null && xpath != null && xpath.equals("") == false)
			xpath += "/";

		if (name != null)
			xpath += name;
		return xpath;
	}

	/**
	 * the name of an element in the schema is contained in the attribute 'name'
	 * 
	 * @param node
	 * @return
	 */
	private String getName(Node node) {
		if (node.hasAttributes() == false)
			return null;

		for (int i = 0; i < node.getAttributes().getLength(); i++) {
			if (node.getAttributes().item(i).getNodeName().equals("name"))
				return node.getAttributes().item(i).getNodeValue();
		}
		return null;
	}

	// private void getKeys(Node node) {
	// try {
	// for (int i = 0; i < node.getChildNodes().getLength(); i++) {
	// if (node.getChildNodes().item(i).getNodeName()
	// .indexOf("keyref") > 0) {
	// keyRefs.add(node.getChildNodes().item(i));
	// } else if (node.getChildNodes().item(i).getNodeName().indexOf(
	// "key") > 0) {
	// keyz.add(node.getChildNodes().item(i));
	// }
	// getKeys(node.getChildNodes().item(i));
	// }
	//	
	// } catch (Exception e) {
	// e.printStackTrace(System.err);
	// }
	// }

	/**
	 * Return all the children and gran children of a node of the Schema which
	 * are selected, but not the selected node under those ones.
	 */
	private ArrayList<XsdNode> getNextSelectedChildren(XsdNode xsdNode) {
		ArrayList<XsdNode> xsdNodes = new ArrayList<XsdNode>();
		Enumeration<XsdNode> children = xsdNode.children();

		while (children.hasMoreElements()) {
			XsdNode child = children.nextElement();

			if (child.isUsed()) {
				xsdNodes.add(child);
			} else {
				xsdNodes.addAll(getNextSelectedChildren(child));
			}
		}
		return xsdNodes;
	}

	public Document getDocument() {
		return document;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	public XsdNode getLineXsdNode() {
		return lineXsdNode;
	}

	public void setLineXsdNode(XsdNode lineXsdNode) {
		this.lineXsdNode = lineXsdNode;
	}

	public ArrayList<Node> getLineElements() {
		return lineElements;
	}

	public void setLineElements(ArrayList<Node> lineElements) {
		this.lineElements = lineElements;
	}

	public void setValidateDocument(boolean validateDocument) {
		XsdTreeStructImpl.validateDocument = validateDocument;
	}

	public HashMap<XsdNode, String> getElementFilters() {
		return elementFilters;
	}

	public static boolean isAllowCleanTree() {
		return allowCleanTree;
	}

	public static void setAllowCleanTree(boolean allowCleanTree) {
		XsdTreeStructImpl.allowCleanTree = allowCleanTree;
	}

}