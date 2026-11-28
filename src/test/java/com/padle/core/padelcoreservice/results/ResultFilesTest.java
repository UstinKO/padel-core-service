package com.padle.core.padelcoreservice.results;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFilesTest {
    @Test void spreadsheetKeepsUntrustedTextAsTextAndUnknownScoresBlank() throws Exception {
        var file=new ResultFiles().write(report(),ResultFiles.Format.XLSX);
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(file))) {
            var participants=workbook.getSheet("Participants");
            assertThat(participants.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(participants.getRow(1).getCell(1).getStringCellValue()).isEqualTo("=HYPERLINK(\"https://example.invalid\")");
            for(var sheet:workbook) for(var row:sheet) for(var cell:row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            assertThat(workbook.getSheet("Matches").getRow(1).getCell(13).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(workbook.getSheet("Matches").getRow(1).getCell(14).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(workbook.getSheet("Matches").getRow(1).getCell(14).getNumericCellValue()).isZero();
            assertThat(participants.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test void csvQuotesSeparatorsNewlinesAndNeutralizesFormulaPrefixes() throws Exception {
        var file=new ResultFiles().write(report(),ResultFiles.Format.CSV);
        try(var zip=new ZipInputStream(new ByteArrayInputStream(file),StandardCharsets.UTF_8)) {
            zip.getNextEntry();zip.readAllBytes();zip.getNextEntry();
            var csv=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(csv).contains("\"'=HYPERLINK(\"\"https://example.invalid\"\")\"","\"'@SUM(1,2)\"","\"'+5491100000000\"");
        }
    }

    private ResultReport report() {
        var event=new ResultReport.Event(42,"QA, torneo\nEjemplo",LocalDate.of(2026,11,27),LocalTime.of(14,0),"America/Argentina/Buenos_Aires",
                "Black Padel QA","Club QA","C7","AMERICANO","INDIVIDUAL","EN_CURSO",true,null);
        var person=new ResultReport.Person(123L,"=HYPERLINK(\"https://example.invalid\")","@SUM(1,2)","+5491100000000","CONFIRMED","","","",true);
        var game=new ResultReport.Game("AMERICANO",1,null,42,1,"ROUND_ROBIN","1",null,"123","Ejemplo",null,"124","Otro",
                null,0,"POINTS",null,"","IN_PROGRESS",null);
        return new ResultReport(event,List.of(new ResultReport.Participant(person,ResultReport.Standing.EMPTY,null,"")),List.of(game),
                List.of("PROVISIONAL"),Instant.parse("2026-11-27T17:00:00Z"),true,true);
    }
}
