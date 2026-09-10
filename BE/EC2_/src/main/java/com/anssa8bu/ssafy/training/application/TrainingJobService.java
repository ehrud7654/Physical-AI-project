package com.anssa8bu.ssafy.training.application;

import static com.anssa8bu.ssafy.training.dto.TrainingJobApiModels.*;

import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.device.entity.*;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.episode.entity.*;
import com.anssa8bu.ssafy.episode.repository.*;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingJobService {
  private final TrainingJobRepository jobs;
  private final TrainingJobEpisodeRepository inputs;
  private final TrainingProfileRepository profiles;
  private final SkillRepository skills;
  private final DeviceRepository devices;
  private final EpisodeRepository episodes;
  private final EpisodeArchiveRepository archives;
  private final IdempotencyGuard idempotency;
  private final Clock clock;

  public TrainingJobService(TrainingJobRepository jobs, TrainingJobEpisodeRepository inputs,
      TrainingProfileRepository profiles, SkillRepository skills, DeviceRepository devices,
      EpisodeRepository episodes, EpisodeArchiveRepository archives,
      IdempotencyGuard idempotency, Clock clock) {
    this.jobs=jobs; this.inputs=inputs; this.profiles=profiles; this.skills=skills;
    this.devices=devices; this.episodes=episodes; this.archives=archives;
    this.idempotency=idempotency; this.clock=clock;
  }

  @Transactional
  public JobResponse create(UUID owner, String key, CreateRequest request) {
    var claim=idempotency.claim(owner,"POST","/api/v1/training-jobs",key,request);
    if(claim.existingResourceId()!=null) return get(owner,claim.existingResourceId());
    Skill skill=skills.findBySkillKey(request.skillKey()).orElseThrow(()->new ResourceNotFoundException("Skill"));
    Device device=devices.findByIdAndOwnerId(request.umiDeviceId(),owner)
        .filter(d->d.getDeviceType()==DeviceType.UMI_CAMERA)
        .orElseThrow(()->new ResourceNotFoundException("UMI Device"));
    TrainingProfile profile=profiles.findFirstByStatusOrderByVersionDesc(TrainingProfileStatus.ACTIVE)
        .orElseThrow(()->new BusinessConflictException("ACTIVE_PROFILE_NOT_FOUND","활성 학습 프로필이 없습니다."));
    List<Episode> selected=selectEpisodes(owner,skill,request);
    int expected=request.inputMode()==TrainingInputMode.COLLECT_NEW
        ? Optional.ofNullable(request.expectedEpisodeCount()).orElse(0) : selected.size();
    if(expected<1) throw new BusinessConflictException("INVALID_EPISODE_COUNT","Episode 수는 1 이상이어야 합니다.");
    Map<String,Object> query=new LinkedHashMap<>();
    query.put("skillKey",skill.getSkillKey()); query.put("inputMode",request.inputMode().name());
    TrainingJob job=jobs.save(new TrainingJob(UUID.randomUUID(),owner,skill.getId(),device.getId(),
        profile.getId(),request.inputMode(),profile.getHyperparameters(),profile.getConfigSha256(),query,selected.size()));
    job.planInput(expected,!selected.isEmpty());
    for(int i=0;i<selected.size();i++) attach(job,selected.get(i),i+1);
    idempotency.complete(owner,"POST","/api/v1/training-jobs",claim,201,"TRAINING_JOB",job.getId());
    return map(job,skill);
  }

  @Transactional(readOnly=true)
  public List<JobResponse> list(UUID owner, UUID skillId, String skillKey, UUID umiDeviceId, String status) {
    return jobs.findAllByOwnerIdOrderByCreatedAtDesc(owner).stream()
        .filter(j->skillId==null||j.getSkillId().equals(skillId))
        .filter(j->umiDeviceId==null||j.getUmiDeviceId().equals(umiDeviceId))
        .filter(j->skillKey==null||skillKey.isBlank()||skill(j.getSkillId()).getSkillKey().equals(skillKey))
        .filter(j->status==null||status.isBlank()||j.getStatus().name().equals(status))
        .map(j->map(j,skill(j.getSkillId()))).toList();
  }

  @Transactional(readOnly=true)
  public JobResponse get(UUID owner, UUID id) {
    TrainingJob job=owned(owner,id); return map(job,skill(job.getSkillId()));
  }

  @Transactional
  public JobResponse cancel(UUID owner, UUID id) {
    TrainingJob job=owned(owner,id);
    try { job.requestCancel(); }
    catch(IllegalStateException e) { throw new BusinessConflictException("STATE_CONFLICT",e.getMessage()); }
    return map(job,skill(job.getSkillId()));
  }

  @Transactional
  public JobResponse updateInputPlan(UUID owner, UUID id, InputPlanUpdateRequest request) {
    TrainingJob job = owned(owner, id);
    try { job.updateExpectedEpisodeCount(request.expectedEpisodeCount()); }
    catch (IllegalStateException e) {
      throw new BusinessConflictException("STATE_CONFLICT", e.getMessage());
    }
    return map(job, skill(job.getSkillId()));
  }

  @Transactional
  public InputCompleteResponse completeInput(
      UUID owner, UUID id, String key, InputCompleteRequest request) {
    String path = "/api/v1/training-jobs/" + id + "/input-complete";
    var claim = idempotency.claim(owner, "POST", path, key, request);
    if (claim.existingResourceId() != null) {
      TrainingJob existing = owned(owner, claim.existingResourceId());
      return new InputCompleteResponse(existing.getId(), existing.getStatus());
    }
    TrainingJob job = owned(owner, id);
    try { job.beginInputValidation(request.receivedEpisodeCount()); }
    catch (IllegalStateException e) {
      String code = e.getMessage().contains("일치") ? "INPUT_COUNT_MISMATCH" : "STATE_CONFLICT";
      throw new BusinessConflictException(code, e.getMessage());
    }
    idempotency.complete(owner, "POST", path, claim, 202, "TRAINING_JOB", job.getId());
    return new InputCompleteResponse(job.getId(), job.getStatus());
  }

  @Transactional(readOnly=true)
  public List<EpisodeInputResponse> inputEpisodes(UUID owner, UUID id) {
    owned(owner,id);
    return inputs.findAllByIdTrainingJobIdOrderByInputOrder(id).stream()
        .map(input->new EpisodeInputResponse(input.getId().episodeId(),input.getInputOrder())).toList();
  }

  private List<Episode> selectEpisodes(UUID owner, Skill skill, CreateRequest request) {
    if(request.inputMode()==TrainingInputMode.COLLECT_NEW) {
      if(request.episodeIds()!=null&&!request.episodeIds().isEmpty())
        throw new BusinessConflictException("FIELD_NOT_ALLOWED","신규 수집에는 episodeIds를 사용할 수 없습니다.");
      return List.of();
    }
    if(request.episodeIds()==null||request.episodeIds().isEmpty()
        ||new HashSet<>(request.episodeIds()).size()!=request.episodeIds().size())
      throw new BusinessConflictException("EPISODE_INVALID","중복 없는 Episode가 하나 이상 필요합니다.");
    List<Episode> found=episodes.findAllByIdIn(request.episodeIds());
    if(found.size()!=request.episodeIds().size()) throw new ResourceNotFoundException("Episode");
    UUID storage=null;
    Map<UUID,Episode> byId=new HashMap<>();
    for(Episode episode:found) {
      if(!episode.getOwnerId().equals(owner)||!episode.getSkillId().equals(skill.getId())
          ||episode.getStatus()!=EpisodeStatus.READY
          ||!(episode.getQualityStatus()==EpisodeQualityStatus.VALID||episode.getQualityStatus()==EpisodeQualityStatus.WARNING)
          ||episode.getLabelStatus()!=EpisodeLabelStatus.LABELED||!Boolean.TRUE.equals(episode.getSuccess()))
        throw new BusinessConflictException("EPISODE_INVALID","학습에 사용할 수 없는 Episode입니다.");
      EpisodeArchive archive=archives.findByEpisodeId(episode.getId())
          .filter(a->a.getStatus()==EpisodeArchiveStatus.AVAILABLE)
          .orElseThrow(()->new BusinessConflictException("EPISODE_NOT_AVAILABLE","Episode 파일을 사용할 수 없습니다."));
      if(storage!=null&&!storage.equals(episode.getStorageNodeId()))
        throw new BusinessConflictException("EPISODE_STORAGE_NODE_MISMATCH","서로 다른 저장 노드의 Episode는 함께 사용할 수 없습니다.");
      storage=episode.getStorageNodeId(); byId.put(episode.getId(),episode);
    }
    return request.episodeIds().stream().map(byId::get).toList();
  }

  private void attach(TrainingJob job, Episode episode, int order) {
    EpisodeArchive archive=archives.findByEpisodeId(episode.getId()).orElseThrow();
    inputs.save(new TrainingJobEpisode(new TrainingJobEpisodeId(job.getId(),episode.getId()),order,
        archive.getSha256(),episode.getContractVersion(),episode.getSource(),episode.getLabelStatus(),
        episode.getSuccess(),episode.getQualityStatus(),clock.instant()));
  }
  private TrainingJob owned(UUID owner,UUID id){return jobs.findByIdAndOwnerId(id,owner)
      .orElseThrow(()->new ResourceNotFoundException("TrainingJob"));}
  private Skill skill(UUID id){return skills.findById(id).orElseThrow(()->new ResourceNotFoundException("Skill"));}
  private JobResponse map(TrainingJob j,Skill s){return new JobResponse(j.getId(),s.getSkillKey(),s.getDisplayName(),
      j.getUmiDeviceId(),j.getInputMode(),j.getStatus(),j.getProgressStage(),j.getProgressPercent(),
      j.getExpectedEpisodeCount(),j.getReceivedEpisodeCount(),j.getReceivedBytes(),j.getEpisodeCount(),
      j.getResultSkillVersionId(),j.getErrorCode()==null?null:new ErrorInfo(j.getErrorCode(),j.getErrorMessage(),"CONTACT_SUPPORT"),
      j.getCreatedAt(),j.getStartedAt(),j.getFinishedAt(),j.getUpdatedAt());}
}
