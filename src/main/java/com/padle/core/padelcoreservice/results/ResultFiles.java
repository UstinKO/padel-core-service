package com.padle.core.padelcoreservice.results;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ResultFiles {
    public enum Format { XLSX, CSV }
    record Table(String name,List<String> columns,List<? extends List<?>> rows) {}

    public byte[] write(ResultReport report,Format format) throws IOException {
        var tables=tables(report);
        return format==Format.XLSX?excel(tables):csv(tables);
    }

    private List<Table> tables(ResultReport report) {
        var e=report.tournament();
        var event=new Table("Tournament",List.of("schema_version","tournament_id","name","date","start_time","time_zone","organizer","club",
                "category","format","modality","participants","unique_player_profiles","status","active","results_state","captured_at_utc","contacts_included","warnings"),
                List.of(row(1,Long.toString(e.id()),e.name(),e.date(),e.time(),e.timeZone(),e.organizer(),e.club(),e.category(),e.format(),e.modality(),
                        report.participants().size(),report.participants().stream().filter(p->p.person().id()!=null).count(),e.status(),e.active(),report.preliminary()?"PROVISIONAL":"FINAL",report.capturedAt().toString(),report.contacts(),String.join("; ",report.warnings()))));
        var columns=Stream.concat(Stream.of("player_id","first_name","last_name"),report.contacts()?Stream.of("phone"):Stream.empty());
        var participantColumns=Stream.concat(columns,Stream.of("team_ids","team_numbers","partner_ids","reached_stage","final_place","current_place",
                "participation_status","registration_active","matches_played","wins","losses","draws","match_points","court_bonus","manual_adjustment","total_points","standing_unit")).toList();
        var participants=new Table("Participants",participantColumns,report.participants().stream().map(p->participant(p,report.contacts())).toList());
        var matches=new Table("Matches",List.of("source","match_id","result_id","tournament_id","round","stage","court","team1_id","team1_player_ids","team1_names",
                "team2_id","team2_player_ids","team2_names","score1","score2","score_unit","sets","winner_reference","winner_reference_type","winner_names","status","technical_result"),
                report.matches().stream().map(g->row(g.source(),Long.toString(g.id()),id(g.resultId()),Long.toString(g.tournamentId()),g.round(),g.stage(),g.court(),id(g.team1Id()),
                        g.team1Players(),g.team1Names(),id(g.team2Id()),g.team2Players(),g.team2Names(),g.score1(),g.score2(),g.scoreUnit(),g.sets(),g.winnerPlayers(),
                        "BRACKET".equals(g.source())?"LEGACY_ID":"PLAYER_IDS",winnerNames(g),g.status(),g.technicalResult())).toList());
        return List.of(event,participants,matches);
    }

    private String winnerNames(ResultReport.Game game) {
        if(game.winnerPlayers().isBlank()||"BRACKET".equals(game.source())) return "";
        if(game.winnerPlayers().equals(game.team1Players())) return game.team1Names();
        if(game.winnerPlayers().equals(game.team2Players())) return game.team2Names();
        return "";
    }

    private String id(Long id) { return id==null?"":id.toString(); }
    private List<?> row(Object... values) { return Arrays.asList(values); }
    private List<?> participant(ResultReport.Participant p,boolean contacts) {
        var person=p.person();var s=p.standing();
        var first=Stream.of(id(person.id()),person.firstName(),person.lastName());
        var phone=contacts?Stream.of(person.phone()):Stream.empty();
        var rest=Stream.of(person.teamIds(),person.teamNumbers(),person.partnerIds(),p.reachedStage(),p.finalPlace(),s.position(),person.status(),person.registrationActive(),
                s.played(),s.wins(),s.losses(),s.draws(),s.matchPoints(),s.courtBonus(),s.adjustment(),s.totalPoints(),s.unit());
        return Stream.of(first,phone,rest).flatMap(stream->stream).toList();
    }

    private byte[] excel(List<Table> tables) throws IOException {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var header=workbook.createCellStyle();var font=workbook.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);header.setWrapText(true);header.setVerticalAlignment(VerticalAlignment.CENTER);header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var date=workbook.createCellStyle();date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));date.setVerticalAlignment(VerticalAlignment.TOP);
            var time=workbook.createCellStyle();time.setDataFormat(workbook.createDataFormat().getFormat("hh:mm"));time.setVerticalAlignment(VerticalAlignment.TOP);
            var text=workbook.createCellStyle();text.setWrapText(true);text.setVerticalAlignment(VerticalAlignment.TOP);
            for(var table:tables) sheet(workbook,table,header,date,time,text);
            workbook.write(out);return out.toByteArray();
        }
    }

    private void sheet(Workbook workbook,Table table,CellStyle header,CellStyle date,CellStyle time,CellStyle text) {
        var sheet=workbook.createSheet(table.name());var top=sheet.createRow(0);top.setHeightInPoints(34);
        for(int column=0;column<table.columns().size();column++) {
            var cell=top.createCell(column);cell.setCellValue(table.columns().get(column));cell.setCellStyle(header);
            sheet.setColumnWidth(column,Math.min(42,Math.max(18,table.columns().get(column).length()+3))*256);
        }
        for(int index=0;index<table.rows().size();index++) {
            var row=sheet.createRow(index+1);row.setHeightInPoints(rowHeight(table, index,sheet));
            for(int column=0;column<table.columns().size();column++) cell(row.createCell(column),table.rows().get(index).get(column),date,time,text);
        }
        sheet.createFreezePane(0,1);sheet.setAutoFilter(new CellRangeAddress(0,table.rows().size(),0,table.columns().size()-1));
        sheet.setDisplayGridlines(false);
    }

    private void cell(Cell cell,Object value,CellStyle date,CellStyle time,CellStyle text) {
        if(value==null||(value instanceof String string&&string.isEmpty())) return;
        cell.setCellStyle(text);
        if(value instanceof Number number) cell.setCellValue(number.doubleValue());
        else if(value instanceof Boolean bool) cell.setCellValue(bool);
        else if(value instanceof LocalDate day) {cell.setCellValue(day.atStartOfDay());cell.setCellStyle(date);}
        else if(value instanceof LocalTime hour) {cell.setCellValue(hour.toSecondOfDay()/86400d);cell.setCellStyle(time);}
        else {cell.setCellValue(value.toString());cell.setCellStyle(text);}
    }

    private byte[] csv(List<Table> tables) throws IOException {
        try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)) {
            for(var table:tables) {
                zip.putNextEntry(new ZipEntry(table.name()+".csv"));
                var lines=Stream.concat(Stream.of(table.columns()),table.rows().stream()).map(this::csvLine).collect(Collectors.joining("\r\n","\ufeff","\r\n"));
                zip.write(lines.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            zip.finish();return out.toByteArray();
        }
    }

    private float rowHeight(Table table,int index,Sheet sheet) {
        int lines=IntStream.range(0,table.columns().size()).map(column->{
            var value=table.rows().get(index).get(column);
            int width=Math.max(1,sheet.getColumnWidth(column)/256-2);
            return value==null?1:Arrays.stream(value.toString().split("\\n",-1))
                    .mapToInt(line->Math.max(1,(line.length()+width-1)/width)).sum();
        }).max().orElse(1);
        return Math.min(409,Math.max(32,lines*16));
    }

    private boolean dangerous(String text) {
        if(text.isEmpty()) return false;
        String stripped=text.stripLeading();
        return "=+-@\t\r\n".indexOf(text.charAt(0))>=0
                ||(!stripped.isEmpty()&&"=+-@".indexOf(stripped.charAt(0))>=0);
    }

    private String csvLine(List<?> cells) { return cells.stream().map(this::csvCell).collect(Collectors.joining(",")); }
    private String csvCell(Object value) {
        String text=value==null?"":value.toString();
        if(value instanceof String && dangerous(text)) text="'"+text;
        return '"'+text.replace("\"","\"\"")+'"';
    }
}
