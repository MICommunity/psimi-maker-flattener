package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;

import org.apache.poi.ss.usermodel.*;
import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileReader {
    private Workbook workbook;
    private static final Logger log = Logger.getLogger(FileReader.class.getName());

    private final MoleculeSetChecker moleculeSetChecker = new MoleculeSetChecker();
    private final UniprotCallerUtils uniprotCallerUtils = new UniprotCallerUtils();
    private final UniprotCaller uniprotCaller = new UniprotCaller();
    private final DataFormatter formatter = new DataFormatter();

    public FileReader() {
        configureLogger();
    }

    private void configureLogger() {
        log.setLevel(Level.INFO);
        log.setUseParentHandlers(false);
    }

    public Workbook readExcelFile(URL fileUrl) {
        try {
            File file = Paths.get(fileUrl.toURI()).toFile();
            workbook = uniprotCallerUtils.readFile(file.getAbsolutePath());
            log.log(Level.INFO, "Successfully loaded workbook from {0}", fileUrl);
            getSheetsNames(workbook);
        } catch (Exception e) {
            showErrorDialog("Unable to load file! Please provide a file in the Excel xls format!");
            log.log(Level.SEVERE, "Failed to load Excel file", e);
            throw new RuntimeException(e);
        }
        moleculeSetChecker.parseMoleculeSetFile();
        return workbook;
    }

    public void checkAndInsertUniprotResults(String sheetSelected, String organismId, String selectedColumn, URL fileUrl) {
        Sheet sheet = workbook.getSheet(sheetSelected);
        if (sheet == null) {
            showErrorDialog("Sheet not found");
            log.log(Level.WARNING, "Sheet {0} not found", sheetSelected);
            return;
        }
        int selectedColumnIndex = findColumnIndex(sheet, selectedColumn);
        if (selectedColumnIndex != -1) {
            insertColumnWithUniprotResults(sheet, selectedColumnIndex, organismId);
        } else {
            showErrorDialog("Column not found");
            log.log(Level.WARNING, "Column {0} not found", selectedColumn);
        }
        writeWorkbookToFile(fileUrl);
    }

    private int findColumnIndex(Sheet sheet, String selectedColumn) {
        Row headerRow = sheet.getRow(0);
        for (Cell cell : headerRow) {
            if (formatter.formatCellValue(cell).contains(selectedColumn)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private void writeWorkbookToFile(URL fileUrl) {
        try {
            File inputFile = Paths.get(fileUrl.toURI()).toFile();
            String inputFileName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf('.'));
            String outputFileName = inputFile.getParent() + File.separator + inputFileName + "_updated.xls";

            try (FileOutputStream fileOut = new FileOutputStream(outputFileName)) {
                workbook.write(fileOut);
                showInfoDialog("New column inserted with UniProt accession numbers in " + outputFileName);
                log.log(Level.INFO, "Successfully wrote updated file to {0}", outputFileName);
            }
        } catch (Exception e) {
            showErrorDialog("Error writing Excel file");
            log.log(Level.SEVERE, "Failed to write updated Excel file", e);
            throw new RuntimeException(e);
        }
    }

    public void insertColumnWithUniprotResults(Sheet sheet, int columnIndex, String organismId) {
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }
            shiftCellsToTheRight(row, columnIndex);
            Cell previousCell = row.getCell(columnIndex);
            if (previousCell != null) {
                String geneValue = formatter.formatCellValue(previousCell);
                Optional<String> uniprotResultOpt;
                if (rowIndex == 0) {
                    uniprotResultOpt = Optional.of("UniprotAc " + geneValue); // geneValue == header cell
                } else {
                    uniprotResultOpt = uniprotCaller.fetchUniprotResults(geneValue, organismId);
                }
                if (uniprotResultOpt.isPresent()) {
                    Cell newCell = row.createCell(columnIndex);
                    newCell.setCellValue(uniprotResultOpt.get());
                    if (moleculeSetChecker.isProteinPartOfMoleculeSet(uniprotResultOpt.get())) {
                        highlightCells(newCell);
                    }
                } else {
                    row.createCell(columnIndex);
                }
            }
        }
    }

    private void shiftCellsToTheRight(Row row, int columnIndex) {
        for (int colNum = row.getLastCellNum(); colNum > columnIndex; colNum--) {
            Cell oldCell = row.getCell(colNum - 1);
            Cell newCell = row.createCell(colNum);
            if (oldCell != null) {
                cloneCell(oldCell, newCell);
            }
        }
    }

    /**
     * Clone the content and style of the old cell into the new cell.
     */
    public static void cloneCell(Cell oldCell, Cell newCell) {
        newCell.setCellStyle(oldCell.getCellStyle());
        switch (oldCell.getCellType()) {
            case STRING:
                newCell.setCellValue(oldCell.getStringCellValue());
                break;
            case NUMERIC:
                newCell.setCellValue(oldCell.getNumericCellValue());
                break;
            case BOOLEAN:
                newCell.setCellValue(oldCell.getBooleanCellValue());
                break;
            case FORMULA:
                newCell.setCellFormula(oldCell.getCellFormula());
                break;
            case ERROR:
                newCell.setCellErrorValue(oldCell.getErrorCellValue());
                break;
            case BLANK:
            default:
                newCell.setBlank();
                break;
        }
    }

    public String[] getSheetsNames(Workbook workbook) {
        List<String> sheetsNames = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            sheetsNames.add(workbook.getSheetName(i));
        }
        return sheetsNames.toArray(new String[0]);
    }

    public String[] getColumnsNames(Sheet sheet) {
        List<String> columnsNames = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            columnsNames.add(formatter.formatCellValue(cell));
        }
        return columnsNames.toArray(new String[0]);
    }

    public void highlightCells(Cell cell) {
        if (moleculeSetChecker.isProteinPartOfMoleculeSet(cell.getStringCellValue())) {
            CellStyle cellStyle = cell.getSheet().getWorkbook().createCellStyle();
            cellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cell.setCellStyle(cellStyle);
        }
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(new JFrame(), message, "ERROR", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfoDialog(String message) {
        JOptionPane.showMessageDialog(new JFrame(), message, "SUCCESS", JOptionPane.INFORMATION_MESSAGE);
    }
}