package com.anssa8bu.ssafy.episode;

import com.anssa8bu.ssafy.device.entity.*;
import com.anssa8bu.ssafy.episode.entity.*;
import com.anssa8bu.ssafy.episode.vo.*;
import com.anssa8bu.ssafy.skill.entity.*;
import com.anssa8bu.ssafy.storage.entity.*;
import com.anssa8bu.ssafy.user.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EpisodeEntityIntegrationTest {
    @Autowired EntityManager em;

    @Test @Transactional @Rollback
    void persistsFlushesClearsAndReloadsEpisodeJsonAndArchive() {
        UUID userId=UUID.randomUUID(), deviceId=UUID.randomUUID(), nodeId=UUID.randomUUID(), policyId=UUID.randomUUID(), skillId=UUID.randomUUID();
        em.persist(new User(userId,"episode-"+userId+"@test.local","hash","tester",UserStatus.ACTIVE));
        em.persist(new Device(deviceId,userId,DeviceType.UMI_CAMERA,"handle-"+deviceId,"umi",DeviceConnectionStatus.OFFLINE));
        em.persist(new StorageNode(nodeId,"worker-"+nodeId,"node-"+nodeId,"local",StorageType.LOCAL,StorageNodeStatus.ONLINE,1_000_000,900_000));
        em.persist(new RetentionPolicy(policyId,"test-"+policyId,7,1,30,3,true,true,false,true));
        em.persist(new Skill(skillId,"test_"+skillId.toString().replace("-",""),SkillTier.TUTORIAL,SkillStatus.PLANNED,"test",null,false,null));
        Instant started=Instant.parse("2026-09-08T01:00:00Z"), ended=started.plusSeconds(5);
        CaptureMetadata metadata=new CaptureMetadata(Map.of("model","phone"),Map.of("width",640),Map.of("handedness","right"),Map.of("time","ns"));
        QualitySummary quality=new QualitySummary(Map.of("missingRatio",0.0));
        UUID episodeId=UUID.randomUUID();
        Episode episode=new Episode(episodeId,new EpisodeIdentity(userId,deviceId,nodeId,policyId,skillId,"session-1",EpisodeSource.REAL),
                null,EpisodeLabelStatus.UNLABELED,null,null,"1.0.0","1.0","1.0",EpisodeStatus.READY,
                new EpisodeTiming(started,ended,100,110,510),new EpisodeSampleSummary(4,4,20,4,0,0,0),
                EpisodeQualityStatus.VALID,metadata,quality,ended.plusSeconds(86400));
        em.persist(episode);
        ArchiveManifestSummary manifest=new ArchiveManifestSummary(Map.of("meta.json","a".repeat(64)),1,Map.of("format","umi"));
        UUID archiveId=UUID.randomUUID();
        em.persist(new EpisodeArchive(archiveId,episodeId,nodeId,"episodes/"+userId+"/20260908T010005Z/"+archiveId+"/archive.zip","session-1.zip","1.0",100,200,7,"b".repeat(64),manifest,EpisodeArchiveStatus.AVAILABLE));
        em.flush(); em.clear();
        Episode loaded=em.find(Episode.class,episodeId); EpisodeArchive loadedArchive=em.find(EpisodeArchive.class,archiveId);
        assertThat(loaded.getId()).isEqualTo(episodeId); assertThat(loaded.getSource()).isEqualTo(EpisodeSource.REAL);
        assertThat(loaded.getLabeledAt()).isNull(); assertThat(loaded.getStartedAt()).isEqualTo(started); assertThat(loaded.getCaptureMetadata()).isEqualTo(metadata);
        assertThat(loaded.getQualitySummary()).isEqualTo(quality); assertThat(loaded.getRowVersion()).isZero();
        assertThat(loadedArchive.getEpisodeId()).isEqualTo(episodeId); assertThat(loadedArchive.getManifestSummary()).isEqualTo(manifest);
        assertThat(loadedArchive.getContentType()).isEqualTo("application/zip"); assertThat(loadedArchive.getCompressionMethod()).isEqualTo(ArchiveCompressionMethod.STORE);
    }
}
