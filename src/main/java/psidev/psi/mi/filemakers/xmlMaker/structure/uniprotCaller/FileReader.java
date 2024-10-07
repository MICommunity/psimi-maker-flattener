package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

public class FileReader {

    private static final Log log = LogFactory.getLog(FileReader.class);
    Workbook workbook;

    public Workbook readExcelFile(URL fileUrl) {
        log.info("Reading file from URL: " + fileUrl);
        System.out.println("Reading Excel file");
        try {
            File file = Paths.get(fileUrl.toURI()).toFile();
            workbook = WorkbookFactory.create(file);
            System.out.println("Number of sheets: " + workbook.getNumberOfSheets());
            getSheetsNames(workbook);
        } catch (Exception e) {
            System.out.println("Error reading Excel file");
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
            System.out.println("Found " + selectedColumn + " in column: " + selectedColumnIndex);
            insertColumnWithUniprotResults(sheet, selectedColumnIndex + 1, formatter, organismId);
            System.out.println("New column inserted with UniProt results.");
        } else {
            System.out.println(selectedColumn + " column not found.");
        }

        try (FileOutputStream fileOut = new FileOutputStream("updated_PublicationFile.xls")) {
            workbook.write(fileOut);
        } catch (IOException e) {
            System.out.println("Error writing Excel file");
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
                String uniprotResult = uniprotCaller.fetchUniprotResults(geneValue, organismId);
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
