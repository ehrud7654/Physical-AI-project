package com.anssa8bu.ssafy.training.entity; import jakarta.persistence.*; import java.io.Serializable; import java.util.UUID;
@Embeddable public record TrainingJobEpisodeId(@Column(name="training_job_id") UUID trainingJobId,@Column(name="episode_id") UUID episodeId) implements Serializable {}
