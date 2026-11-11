package com.fridgeguardian.batch;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Component;

/** 유통기한 알림 Job을 지정한 날짜(runDate)로 시작합니다. 같은 날짜로 이미 성공했다면 다시 실행하지 않습니다. */
@Component
public class ExpiryJobRunner {
    private static final Logger log = LoggerFactory.getLogger(ExpiryJobRunner.class);
    private final JobOperator jobOperator;
    private final Job expiryNotificationJob;
    private final Clock clock;

    public ExpiryJobRunner(JobOperator jobOperator, Job expiryNotificationJob, Clock clock) {
        this.jobOperator = jobOperator; this.expiryNotificationJob = expiryNotificationJob; this.clock = clock;
    }

    public Optional<JobExecution> runToday() { return runFor(LocalDate.now(clock)); }

    public Optional<JobExecution> runFor(LocalDate runDate) {
        JobParameters parameters = new JobParametersBuilder().addString("runDate", runDate.toString()).toJobParameters();
        try {
            JobExecution execution = jobOperator.start(expiryNotificationJob, parameters);
            log.info("유통기한 알림 배치({}) 결과: {}", runDate, execution.getStatus());
            return Optional.of(execution);
        } catch (JobInstanceAlreadyCompleteException alreadyDone) {
            log.info("유통기한 알림 배치({})는 이미 완료되어 다시 실행하지 않습니다.", runDate);
            return Optional.empty();
        } catch (Exception error) {
            // 스케줄러 스레드가 죽지 않도록 기록만 하고, 다음 날 실행은 계속됩니다.
            log.error("유통기한 알림 배치({}) 실행에 실패했습니다.", runDate, error);
            return Optional.empty();
        }
    }
}
