package com.anssa8bu.ssafy.episode.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import com.anssa8bu.ssafy.episode.vo.ArchiveManifestSummary;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name="episode_archives")
public class EpisodeArchive extends AuditableEntity {
    @Id @Column(nullable=false,updatable=false) private UUID id;
    @Column(name="episode_id",nullable=false,updatable=false) private UUID episodeId;
    @Column(name="storage_node_id",nullable=false,updatable=false) private UUID storageNodeId;
    @Column(name="storage_key",nullable=false,updatable=false,length=500) private String storageKey;
    @Column(name="original_filename",nullable=false,updatable=false,length=255) private String originalFilename;
    @Column(name="content_type",nullable=false,updatable=false,length=150) private String contentType;
    @Column(name="archive_format_version",nullable=false,updatable=false,length=100) private String archiveFormatVersion;
    @Enumerated(EnumType.STRING) @Column(name="compression_method",nullable=false,updatable=false,length=20) private ArchiveCompressionMethod compressionMethod;
    @Column(name="size_bytes",nullable=false,updatable=false) private long sizeBytes;
    @Column(name="uncompressed_size_bytes",nullable=false,updatable=false) private long uncompressedSizeBytes;
    @Column(name="entry_count",nullable=false,updatable=false) private int entryCount;
    @Column(nullable=false,updatable=false,length=64) private String sha256;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="manifest_summary",nullable=false,columnDefinition="jsonb") private ArchiveManifestSummary manifestSummary;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EpisodeArchiveStatus status;
    @Column(name="stored_at") private Instant storedAt; @Column(name="validated_at") private Instant validatedAt; @Column(name="deleted_at") private Instant deletedAt;
    @Column(name="error_code",length=100) private String errorCode; @Column(name="error_message",columnDefinition="text") private String errorMessage;
    protected EpisodeArchive() {}
    public EpisodeArchive(UUID id, UUID episodeId, UUID storageNodeId, String storageKey, String originalFilename,
                          String archiveFormatVersion, long sizeBytes, long uncompressedSizeBytes, int entryCount,
                          String sha256, ArchiveManifestSummary manifestSummary, EpisodeArchiveStatus status) {
        if(sizeBytes<=0||uncompressedSizeBytes<=0||entryCount<7) throw new IllegalArgumentException("invalid archive size or entry count");
        if(sha256==null||!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid sha256");
        if(storageKey==null||storageKey.isBlank()||storageKey.startsWith("/")||storageKey.contains("\\")||storageKey.matches(".*(^|/)\\.\\.?(/|$).*")) throw new IllegalArgumentException("invalid storageKey");
        this.id=Objects.requireNonNull(id); this.episodeId=Objects.requireNonNull(episodeId); this.storageNodeId=Objects.requireNonNull(storageNodeId);
        this.storageKey=storageKey; this.originalFilename=requireText(originalFilename); this.contentType="application/zip";
        this.archiveFormatVersion=requireText(archiveFormatVersion); this.compressionMethod=ArchiveCompressionMethod.STORE;
        this.sizeBytes=sizeBytes; this.uncompressedSizeBytes=uncompressedSizeBytes; this.entryCount=entryCount; this.sha256=sha256;
        this.manifestSummary=Objects.requireNonNull(manifestSummary); this.status=Objects.requireNonNull(status);
    }
    private static String requireText(String v){if(v==null||v.isBlank()) throw new IllegalArgumentException("value must not be blank");return v;}
    public UUID getId(){return id;} public UUID getEpisodeId(){return episodeId;} public UUID getStorageNodeId(){return storageNodeId;}
    public String getStorageKey(){return storageKey;} public String getContentType(){return contentType;} public ArchiveCompressionMethod getCompressionMethod(){return compressionMethod;}
    public String getSha256(){return sha256;} public ArchiveManifestSummary getManifestSummary(){return manifestSummary;} public EpisodeArchiveStatus getStatus(){return status;}
}
