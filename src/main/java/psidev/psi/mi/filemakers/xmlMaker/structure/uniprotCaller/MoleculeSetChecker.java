package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Row;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

public class MoleculeSetChecker {

    private static final String MOLECULE_SET_PATH = "/Users/susiehuget/Desktop/Data/molecule_sets.xls";
    private static final int PROTEINS_CELL_INDEX = 3;
    private static final int MOLECULE_SET_AC_COLUMN_INDEX = 0;
    private static final Logger log = Logger.getLogger(MoleculeSetChecker.class.getName());

    private final DataFormatter formatter = new DataFormatter();
    private final Map<String, String> proteinAndMoleculeSet = new HashMap<>();
    private final UniprotCallerUtils uniprotCallerUtils = new UniprotCallerUtils();

    public MoleculeSetChecker() {
        configureLogger();
        parseMoleculeSetFile();
    }

    private Workbook readFile() throws IOException {
        try (FileInputStream file = new FileInputStream(new File(MOLECULE_SET_PATH))) {
            return WorkbookFactory.create(file);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to read molecule set file", e);
            throw e;
        }
    }

    public void parseMoleculeSetFile() {
        try (Workbook workbook = uniprotCallerUtils.readFile(MOLECULE_SET_PATH)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                String proteins = formatter.formatCellValue(row.getCell(PROTEINS_CELL_INDEX));
                String moleculeSetAc = formatter.formatCellValue(row.getCell(MOLECULE_SET_AC_COLUMN_INDEX));
                if (!proteins.isEmpty() && !moleculeSetAc.isEmpty()) {
                    for (String protein : proteins.split(",")) {
                        protein = protein.trim(); // Trim spaces
                        proteinAndMoleculeSet.putIfAbsent(protein, moleculeSetAc);
                    }
                }
            }
            log.log(Level.INFO, "Molecule set file parsed");
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to parse molecule set file", e);
        }
    }

    public boolean isProteinPartOfMoleculeSet(String proteinAc) {
        return proteinAndMoleculeSet.containsKey(proteinAc);
    }

    private void configureLogger() {
        log.setLevel(Level.INFO);
        log.addHandler(new ConsoleHandler());
        log.setUseParentHandlers(false);
    }

}