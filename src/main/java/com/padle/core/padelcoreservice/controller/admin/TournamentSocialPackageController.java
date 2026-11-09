package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentSocialDraft.Format;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentSocialPackageService;
import com.padle.core.padelcoreservice.service.TournamentSocialPackageService.Download;
import com.padle.core.padelcoreservice.service.TournamentSocialPackageService.Metadata;
import com.padle.core.padelcoreservice.service.TournamentSocialPackageService.Upload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/tournaments/{id}/social-draft/package")
@PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN', 'ORGANIZER', 'ADMIN', 'CLUB_ADMIN')")
public class TournamentSocialPackageController {
    private final TournamentSocialPackageService service;

    @GetMapping
    public ResponseEntity<Metadata> find(@PathVariable long id, @AuthenticationPrincipal Owner owner) {
        return service.find(owner, id)
                .map(metadata -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metadata))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Metadata> save(@PathVariable long id, @AuthenticationPrincipal Owner owner,
                                         @RequestParam long version, @RequestParam Format format,
                                         @RequestParam String instagram, @RequestParam String telegram,
                                         @RequestParam MultipartFile primary,
                                         @RequestParam(required = false) MultipartFile finalist,
                                         @RequestParam(required = false) MultipartFile bracket) {
        var upload = new Upload(version, format, instagram, telegram, primary, finalist, bracket);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(owner, id, upload));
    }

    @GetMapping("/{version}/cards/{filename}")
    public ResponseEntity<byte[]> card(@PathVariable long id, @PathVariable long version, @PathVariable String filename,
                                       @RequestParam(defaultValue = "false") boolean download,
                                       @AuthenticationPrincipal Owner owner) {
        var file = service.card(owner, id, version, filename);
        return response(file, MediaType.IMAGE_JPEG, download ? ContentDisposition.attachment() : ContentDisposition.inline());
    }

    @GetMapping("/{version}/zip")
    public ResponseEntity<byte[]> archive(@PathVariable long id, @PathVariable long version,
                                          @AuthenticationPrincipal Owner owner) {
        return response(service.archive(owner, id, version), MediaType.parseMediaType("application/zip"),
                ContentDisposition.attachment());
    }

    private static ResponseEntity<byte[]> response(Download download, MediaType type, ContentDisposition.Builder disposition) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(download.filename()).build().toString())
                .body(download.bytes());
    }
}
