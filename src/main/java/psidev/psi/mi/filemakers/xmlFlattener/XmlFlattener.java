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
package psidev.psi.mi.filemakers.xmlFlattener;

import java.beans.XMLDecoder;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import psidev.psi.mi.filemakers.xmlFlattener.mapping.TreeMapping;
import psidev.psi.mi.filemakers.xmlFlattener.structure.XsdTreeStructImpl;
import psidev.psi.mi.filemakers.xsd.SimpleMessageManager;

/**
 * Executable class for the flattener, without graphical user interface
 * 
 * Main class for the flattener: load an XML schema as a tree, load a XML
 * document, get selected nodes from the mapping file and write a flat file.
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 * 
 */
public class XmlFlattener {

	private static final Log log = LogFactory.getLog(XmlFlattener.class);

	public XmlFlattener() {
		xsdTree = new XsdTreeStructImpl();
		xsdTree.setMessageManager(new SimpleMessageManager());
	}

	public static void main(String[] args) throws Exception {

		System.setProperty("java.awt.headless", "true");
		XmlFlattener f = new XmlFlattener();
		String mappingFileName = "";
		String schema = null;
		String flatFile = null;
		String xmlDocument = null;

		Options options = new Options();

		Option option = new Option("mapping", true, "Mapping file");
		option.setRequired(true);
		options.addOption(option);

		option = new Option("xmlDocument", true, "XML document to parse");
		option.setRequired(false);
		options.addOption(option);

		option = new Option("schema", true,
				"Xsd schema, for instance data/MIF25.xsd");
		option.setRequired(false);
		options.addOption(option);

		option = new Option("o", true, "output tab delimited file");
		option.setRequired(true);
		options.addOption(option);

		option = new Option(
				"validate",
				false,
				"validate the XML document (required to retrieved XML ids, e.g. with PSI-MI XML 1.0)");
		option.setRequired(false);
		options.addOption(option);

		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;

		try {
			// parse the command line arguments
			cmd = parser.parse(options, args, true);
		} catch (ParseException exp) {
			// Oops, something went wrong

			displayUsage(options);
			System.exit(1);
		}

		mappingFileName = cmd.getOptionValue("mapping");
		xmlDocument = cmd.getOptionValue("xmlDocument");
		schema = cmd.getOptionValue("schema");
		flatFile = cmd.getOptionValue("o");

		log.info("mapping : " + mappingFileName + ", output : " + flatFile);

		FileInputStream fin = new FileInputStream(mappingFileName);

		// Create XML decoder.
		XMLDecoder xdec = new XMLDecoder(fin);
		TreeMapping treeMapping = (TreeMapping) xdec.readObject();

		if (xmlDocument != null) {
			xmlDocument = xmlDocument.replaceAll("'", "");
			treeMapping.setDocumentURL(xmlDocument);
			log.info("xmlDocument : " + xmlDocument);
		}

		if (schema != null) {
			treeMapping.setSchemaURL(schema.replaceAll("'", ""));
			log.info("Xsd schema : " + schema);
		}

		if (cmd.hasOption("validate")) {
			log.info("XML document will be validated");
			f.xsdTree.setValidateDocument(true);
		}

		f.xsdTree.loadMapping(treeMapping);

		if (log.isErrorEnabled()) {
			log.error("Messages from XML Parsing:");
			for (String error : f.xsdTree.xmlErrorHandler.getErrors()) {
				log.error(error);
			}
		}

		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(
				flatFile)));

		f.xsdTree.write(writer);
		writer.flush();
		writer.close();
		log.info("Flat file successfully created");

	}

	private XsdTreeStructImpl xsdTree;

	private static void displayUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1) {
			formatter.printHelp("bin/xmlflattener.bat ", options);
		} else {
			formatter.printHelp("sh bin/xmlflattener ", options);
		}
	}

	public XsdTreeStructImpl getXsdTree() {
		return xsdTree;
	}

}