package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentSocialDraft.Format;
import com.padle.core.padelcoreservice.model.Owner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class TournamentSocialPackageService {
    private static final int MAX_CARD_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CAPTION_LENGTH = 12_000;
    private static final String METADATA = "version, saved_at, saved_by, format, "
            + "finalist_card IS NOT NULL AS has_finalist, bracket_card IS NOT NULL AS has_bracket";
    private final JdbcClient jdbc;
    private final TournamentAccessService access;
    private final TournamentSocialDraftService drafts;

    public record Metadata(long version, OffsetDateTime savedAt, String savedBy, Format format, List<String> cards) {}
    public record Upload(long version, Format format, String instagram, String telegram,
                         MultipartFile primary, MultipartFile finalist, MultipartFile bracket) {}
    public record Download(String filename, byte[] bytes) {}

    private enum Slot {
        PRIMARY("primary_card"), FINALIST("finalist_card"), BRACKET("bracket_card");

        private final String column;

        Slot(String column) {
            this.column = column;
        }

        String filename(Format format) {
            return switch (this) {
                case FINALIST -> "02-subcampeones.jpg";
                case BRACKET -> "03-cuadro-final.jpg";
                case PRIMARY -> switch (format) {
                    case TEAM_PLAYOFF -> "01-campeones.jpg";
                    case KING_OF_COURT -> "01-rey-de-la-cancha.jpg";
                    case AMERICANO -> "01-americano.jpg";
                    case GENERAL -> "01-evento.jpg";
                };
            };
        }

        static Slot fromFilename(String filename) {
            for (Format format : Format.values()) {
                for (Slot slot : values()) {
                    if (slot.filename(format).equals(filename)) {
                        return slot;
                    }
                }
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    public Optional<Metadata> find(Owner owner, long id) {
        authorize(owner, id);
        return jdbc.sql("SELECT " + METADATA + " FROM tournament_social_packages WHERE tournament_id = :id")
                .param("id", id).query(TournamentSocialPackageService::metadata).optional();
    }

    public Metadata save(Owner owner, long id, Upload upload) {
        var draft = drafts.prepare(owner, id);
        validateUpload(draft.format(), upload);
        var primary = jpeg(upload.primary());
        var finalist = optionalJpeg(upload.finalist());
        var bracket = optionalJpeg(upload.bracket());
        return jdbc.sql(saveSql(upload.version()))
                .param("id", id).param("version", upload.version()).param("format", upload.format().name())
                .param("author", owner.getFullName()).param("instagram", upload.instagram()).param("telegram", upload.telegram())
                .param("primary", primary, Types.BINARY).param("finalist", finalist, Types.BINARY)
                .param("bracket", bracket, Types.BINARY)
                .query(TournamentSocialPackageService::metadata).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "PACKAGE_CHANGED"));
    }

    private static String saveSql(long version) {
        String statement = version == 0 ? """
                INSERT INTO tournament_social_packages
                    (tournament_id, version, saved_at, saved_by, format, instagram, telegram, primary_card, finalist_card, bracket_card)
                VALUES (:id, :version + 1, CURRENT_TIMESTAMP, :author, :format, :instagram, :telegram, :primary, :finalist, :bracket)
                ON CONFLICT (tournament_id) DO NOTHING
                """ : """
                UPDATE tournament_social_packages
                SET version = version + 1, saved_at = CURRENT_TIMESTAMP, saved_by = :author,
                    format = :format, instagram = :instagram, telegram = :telegram,
                    primary_card = :primary, finalist_card = :finalist, bracket_card = :bracket
                WHERE tournament_id = :id AND version = :version
                """;
        return statement + " RETURNING " + METADATA;
    }

    private static void validateUpload(Format available, Upload upload) {
        boolean paired = upload.format() == Format.TEAM_PLAYOFF;
        if (upload.version() < 0 || upload.format() == null
                || (upload.format() != Format.GENERAL && upload.format() != available)
                || paired != present(upload.finalist()) || (!paired && present(upload.bracket()))
                || !validCaption(upload.instagram()) || !validCaption(upload.telegram())) {
            throw invalid();
        }
    }

    private static boolean validCaption(String caption) {
        return caption != null && !caption.isBlank() && caption.length() <= MAX_CAPTION_LENGTH && caption.indexOf(0) < 0;
    }

    private static boolean present(MultipartFile part) {
        return part != null && !part.isEmpty();
    }

    private static byte[] optionalJpeg(MultipartFile file) {
        return present(file) ? jpeg(file) : null;
    }

    private static byte[] jpeg(MultipartFile file) {
        if (!present(file)) {
            throw invalid();
        }
        if (file.getSize() > MAX_CARD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PACKAGE_TOO_LARGE");
        }
        try {
            byte[] bytes = file.getBytes();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var reader = ImageIO.getImageReadersByFormatName("JPEG").next();
                try {
                    reader.setInput(input);
                    if (bytes.length < 4 || bytes[bytes.length - 2] != (byte) 0xff || bytes[bytes.length - 1] != (byte) 0xd9
                            || reader.getWidth(0) != 1080 || reader.getHeight(0) != 1350) {
                        throw invalid();
                    }
                    reader.read(0);
                    return bytes;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    public Download card(Owner owner, long id, long version, String filename) {
        authorize(owner, id);
        var slot = Slot.fromFilename(filename);
        return jdbc.sql("SELECT format, " + slot.column
                        + " FROM tournament_social_packages WHERE tournament_id = :id AND version = :version")
                .param("id", id).param("version", version)
                .query((row, n) -> {
                    if (!slot.filename(Format.valueOf(row.getString(1))).equals(filename)) {
                        return Optional.<Download>empty();
                    }
                    return Optional.ofNullable(row.getBytes(2)).map(bytes -> new Download(filename, bytes));
                })
                .optional().flatMap(result -> result)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Download archive(Owner owner, long id, long version) {
        authorize(owner, id);
        return jdbc.sql("""
                SELECT format, instagram, telegram, primary_card, finalist_card, bracket_card
                FROM tournament_social_packages WHERE tournament_id = :id AND version = :version
                """).param("id", id).param("version", version).query((row, n) -> {
            var format = Format.valueOf(row.getString("format"));
            var bytes = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                for (var slot : Slot.values()) {
                    byte[] card = row.getBytes(slot.column);
                    if (card != null) {
                        addEntry(zip, slot.filename(format), card);
                    }
                }
                addEntry(zip, "instagram-es.txt", row.getString("instagram").getBytes(StandardCharsets.UTF_8));
                addEntry(zip, "telegram-ru.txt", row.getString("telegram").getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException("Не удалось собрать архив публикации", exception);
            }
            return new Download("1padel-" + id + "-social.zip", bytes.toByteArray());
        }).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private void authorize(Owner owner, long id) {
        if (owner == null) {
            throw new AccessDeniedException("Tournament management required");
        }
        access.assertCanManageTournament(owner, id);
    }

    private static Metadata metadata(java.sql.ResultSet row, int number) throws java.sql.SQLException {
        var format = Format.valueOf(row.getString("format"));
        List<String> cards = new ArrayList<>();
        cards.add(Slot.PRIMARY.filename(format));
        if (row.getBoolean("has_finalist")) {
            cards.add(Slot.FINALIST.filename(format));
        }
        if (row.getBoolean("has_bracket")) {
            cards.add(Slot.BRACKET.filename(format));
        }
        return new Metadata(row.getLong("version"), row.getObject("saved_at", OffsetDateTime.class),
                row.getString("saved_by"), format, List.copyOf(cards));
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PACKAGE");
    }
}
