package com.yn.employment.common.io;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tiny helper for streaming a single-sheet .xlsx download. Header row is bold
 * with a light-blue background; columns auto-sized; numeric values write as
 * Number cells (so Excel shows them right-aligned and chartable).
 */
public final class XlsxWriter {
    private XlsxWriter() {}

    public static void write(HttpServletResponse resp,
                             String filename,
                             String sheetName,
                             List<String> headers,
                             List<List<Object>> rows) throws IOException {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        try (Workbook wb = new XSSFWorkbook();
             OutputStream out = resp.getOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName == null ? "Sheet1" : sheetName);

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Header row
            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }

            // Data rows
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object v = values.get(c);
                    if (v == null) {
                        cell.setBlank();
                    } else if (v instanceof Number) {
                        cell.setCellValue(((Number) v).doubleValue());
                    } else if (v instanceof Boolean) {
                        cell.setCellValue((Boolean) v);
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }

            // Auto-size columns (cap to 50 chars)
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                int w = sheet.getColumnWidth(i);
                if (w > 50 * 256) sheet.setColumnWidth(i, 50 * 256);
            }
            // Freeze header row
            sheet.createFreezePane(0, 1);

            wb.write(out);
        }
    }
}
