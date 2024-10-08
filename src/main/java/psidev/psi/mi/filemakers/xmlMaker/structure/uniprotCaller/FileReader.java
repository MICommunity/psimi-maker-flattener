package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;


import org.apache.poi.ss.usermodel.*;
import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

public class FileReader {
    Workbook workbook;

    public Workbook readExcelFile(URL fileUrl) {
        try {
            File file = Paths.get(fileUrl.toURI()).toFile();
            workbook = WorkbookFactory.create(file);
            getSheetsNames(workbook);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(new JFrame(),
                    "Unable to load file! Please provide a file under the xls format!",
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return workbook;
    }

    /**
     * Method to check if a 'gene' column exists, insert a new column, and populate the new column with uniprot results.
     */
    //TODO: Should we add try catch for the header and the sheets selection? Since we are fetching them directly from
    // file aleardy?

    public void checkAndInsertUniprotResults(String sheetSelected, String organismId, String selectedColumn) {
        Sheet sheet = workbook.getSheet(sheetSelected);
        Row headerRow = sheet.getRow(0);
        DataFormatter formatter = new DataFormatter();
        int selectedColumnIndex = -1;

        for (Cell cell : headerRow) {
            if (formatter.formatCellValue(cell).contains(selectedColumn)) {
                selectedColumnIndex = cell.getColumnIndex();
                break;
            }
        }

        if (selectedColumnIndex != -1) {
            insertColumnWithUniprotResults(sheet, selectedColumnIndex, formatter, organismId);
        } else {
            JOptionPane.showMessageDialog(new JFrame(),
                    "Column not found",
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        }

        try (FileOutputStream fileOut = new FileOutputStream("updated.xls")) {
            workbook.write(fileOut);
            JOptionPane.showMessageDialog(new JFrame(),
                    "New column inserted with UniProt accession numbers!",
                    "SUCCESS",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(new JFrame(),
                    "Error writing Excel file",
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }
    }

    /**
     * Insert a column to the right of the 'gene' column and populate it with the results from uniprotCaller.
     */
    public static void insertColumnWithUniprotResults(Sheet sheet, int columnIndex, DataFormatter formatter, String organismId) {
        UniprotCaller uniprotCaller = new UniprotCaller();
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            for (int colNum = row.getLastCellNum(); colNum >= columnIndex; colNum--) {
                Cell oldCell = row.getCell(colNum);
                Cell newCell = row.createCell(colNum + 1);

                if (oldCell != null) {
                    cloneCell(oldCell, newCell);
                }
            }

            Cell previousCell = row.getCell(columnIndex);
            if (previousCell != null) {
                String geneValue = formatter.formatCellValue(previousCell);
                String uniprotResult;
                if (rowIndex == 0) {
                    uniprotResult = "UniprotAc " + geneValue;
                }
                else {
                    uniprotResult = uniprotCaller.fetchUniprotResults(geneValue, organismId);
                }
                Cell newCell = row.createCell(columnIndex);
                newCell.setCellValue(uniprotResult);
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

    public String[] getSheetsNames(Workbook workbook){
        ArrayList<String> sheetsNamesArrayList = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++){
            sheetsNamesArrayList.add(workbook.getSheetName(i));
        }
        return sheetsNamesArrayList.toArray(new String[0]);
    }

    public String[] getColumnsNames(Sheet sheet){
        ArrayList<String> columnsNamesArrayList = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            columnsNamesArrayList.add(formatter.formatCellValue(cell));
        }
        return columnsNamesArrayList.toArray(new String[0]);
    }
}
