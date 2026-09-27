package com.anssa8bu.ssafy.jetson.application;

import static com.anssa8bu.ssafy.jetson.dto.JetsonStateModels.*;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.deployment.entity.*;
import com.anssa8bu.ssafy.deployment.repository.*;
import com.anssa8bu.ssafy.jetson.dto.JetsonCommand;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.task.repository.TaskStepRepository;
import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JetsonStateService {
  private final JetsonIdentityService identity; private final DeploymentRepository deployments;
  private final RobotSkillDeploymentRepository robotSkills; private final SkillVersionRepository versions;
  private final SkillRepository skills; private final TaskStepRepository steps;
  public JetsonStateService(JetsonIdentityService i, DeploymentRepository d,
      RobotSkillDeploymentRepository rs, SkillVersionRepository v, SkillRepository s, TaskStepRepository ts) {
    identity=i; deployments=d; robotSkills=rs; versions=v; skills=s; steps=ts;
  }
  @Transactional(readOnly=true)
  public CurrentDeployment current(Jwt jwt) {
    var robot=identity.requireRobot(jwt);
    if(robot.getCurrentDeploymentId()==null) throw new ResourceNotFoundException("CurrentDeployment");
    Deployment d=deployments.findByIdAndRobotId(robot.getCurrentDeploymentId(), robot.getId())
        .orElseThrow(() -> new ResourceNotFoundException("CurrentDeployment"));
    List<JetsonCommand.Step> workflow=steps.findAllByTaskVersionIdOrderByStepOrder(d.getTaskVersionId()).stream()
        .map(s -> new JetsonCommand.Step(s.getId(),s.getParentStepId(),s.getStepOrder(),s.getStepType().name(),
            s.getSkillVersionId(),s.getTimeoutSeconds(),s.getRepeatCount(),s.getParameters())).toList();
    return new CurrentDeployment(d.getId(),d.getTaskVersionId(),workflow,active(jwt),d.getAppliedAt());
  }
  @Transactional(readOnly=true)
  public List<ActiveSkill> active(Jwt jwt) {
    var robot=identity.requireRobot(jwt);
    return robotSkills.findAllByRobotIdAndStatusOrderByDeployedAtDesc(robot.getId(),RobotSkillDeploymentStatus.ACTIVE)
        .stream().map(r -> { var v=versions.findById(r.getSkillVersionId()).orElseThrow();
          var s=skills.findById(r.getSkillId()).orElseThrow();
          return new ActiveSkill(s.getId(),s.getSkillKey(),v.getId(),v.getVersion(),r.getDeploymentId(),r.getStatus(),r.getDeployedAt());
        }).toList();
  }
}
