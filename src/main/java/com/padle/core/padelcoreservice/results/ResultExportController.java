package com.padle.core.padelcoreservice.results;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.Principal;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/admin/tournaments/{id}/results-export")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class ResultExportController {
    private final ResultSnapshot snapshots;
    private final ResultFiles files;
    private final ResultExportAudit audit;

    @GetMapping("/preview")
    public ResponseEntity<ResultReport.Preview> preview(@PathVariable long id) {
        var report=snapshots.read(id,false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResultReport.Preview(
                report.preliminary(),report.participants().size(),report.matches().size(),report.warnings()));
    }

    @PostMapping
    public ResponseEntity<byte[]> download(@PathVariable long id,@RequestParam(defaultValue="XLSX") String format,
            @RequestParam(defaultValue="false") boolean contacts,@RequestParam(defaultValue="false") boolean acknowledged,Principal principal) throws IOException {
        if(contacts&&!acknowledged) throw new ResponseStatusException(BAD_REQUEST,"RESULT_EXPORT_CONTACT_CONFIRMATION_REQUIRED");
        var type=format(format);
        var report=snapshots.read(id,contacts);
        var body=files.write(report,type);
        audit.generated(principal.getName(),report,type);
        var extension=type==ResultFiles.Format.XLSX?"xlsx":"zip";
        var filename="1padel-results-"+id+"-"+(report.preliminary()?"provisional":"final")+(contacts?"-contacts":"")+"."+extension;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(type==ResultFiles.Format.XLSX?
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Content-Type-Options","nosniff").header("X-Results-State",report.preliminary()?"PROVISIONAL":"FINAL")
                .body(body);
    }

    private ResultFiles.Format format(String value) {
        try {return ResultFiles.Format.valueOf(value.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException invalid) {throw new ResponseStatusException(BAD_REQUEST,"RESULT_EXPORT_INVALID_FORMAT");}
    }
}
