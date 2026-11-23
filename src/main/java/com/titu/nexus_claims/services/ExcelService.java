package com.titu.nexus_claims.services;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ExcelService {

    public InputStream workbookToInputStream(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        workbook.write(byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();

        return new ByteArrayInputStream(byteArray);
    }

//    private void addListToWorkbook(List<UserDTO> userList, XSSFWorkbook workbook) {
//        XSSFSheet sheet = workbook.getSheetAt(0);
//        int newRowIndex = sheet.getLastRowNum() + 1;
//        sheet.createRow(newRowIndex);
//        Row row = sheet.getRow(newRowIndex);
//
//        row.createCell(0).setCellValue(userList.getName());
//        row.createCell(1).setCellValue(tableList.getProtocol().toString());
//
//        List<String> list = tableList.getItems();
//
//        list.forEach(item -> {
//            int index = sheet.getLastRowNum();
//            Row rowItem = sheet.getRow(index);
//            rowItem.createCell(2).setCellValue(item);
//
//            sheet.createRow(index + 1);
//        });
//    }

    public XSSFWorkbook getEmptyFile() {
        XSSFWorkbook workbook = new XSSFWorkbook();

        addTableListHeader(workbook);

        return workbook;
    }

    private void addTableListHeader(XSSFWorkbook workbook) {
        workbook.createSheet("user_list");
        XSSFSheet sheet =  workbook.getSheetAt(0);
        sheet.createRow(0);

        Row headerRow = sheet.getRow(0);
        headerRow.createCell(1).setCellValue("First Name");
        headerRow.createCell(2).setCellValue("Last Name");
        headerRow.createCell(3).setCellValue("Address");
        headerRow.createCell(4).setCellValue("CNP");
    }

}
