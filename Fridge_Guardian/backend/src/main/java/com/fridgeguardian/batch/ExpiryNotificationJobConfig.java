package com.fridgeguardian.batch;

import com.fridgeguardian.notification.Notification;
import com.fridgeguardian.notification.NotificationRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 유통기한 알림 Job.
 * 매일 자정에 한국 날짜(runDate) 기준으로 임박(오늘~3일 뒤)·전날 만료된 재료를 찾아 알림 큐(notifications)에 적재합니다.
 *
 * D-Day와 상태(임박/여유/만료)는 DB에 저장하지 않고 유통기한에서 요청 때마다 계산합니다.
 * 그래서 이 Job이 실패해도 화면의 D-Day는 항상 정확하고, 이 Job은 '알림 대상 추출'만 책임집니다.
 * 같은 날짜에 다시 실행해도 JobInstance(runDate)와 UNIQUE(재료, 종류, 날짜) 제약이 중복 적재를 막습니다.
 */
@Configuration
public class ExpiryNotificationJobConfig {
    public static final String JOB_NAME = "expiryNotificationJob";
    static final int CHUNK_SIZE = 200;

    @Bean
    Job expiryNotificationJob(JobRepository jobRepository, Step expiryNotificationStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(expiryNotificationStep).build();
    }

    @Bean
    Step expiryNotificationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                JpaPagingItemReader<ExpiringIngredient> expiryReader,
                                ExpiryProcessor expiryProcessor,
                                ItemWriter<Notification> notificationWriter) {
        // Spring Batch 6의 chunk 지향 Step. (chunk(size, transactionManager)는 6.0에서 deprecated)
        return new StepBuilder("expiryNotificationStep", jobRepository)
            .<ExpiringIngredient, Notification>chunk(CHUNK_SIZE)
            .transactionManager(transactionManager)
            .reader(expiryReader).processor(expiryProcessor).writer(notificationWriter).build();
    }

    @Bean
    @StepScope
    JpaPagingItemReader<ExpiringIngredient> expiryReader(EntityManagerFactory entityManagerFactory,
                                                         @Value("#{jobParameters['runDate']}") String runDate) {
        LocalDate today = LocalDate.parse(runDate);
        return new JpaPagingItemReaderBuilder<ExpiringIngredient>().name("expiryReader")
            .entityManagerFactory(entityManagerFactory).pageSize(CHUNK_SIZE)
            // 기한 미등록(NULL)은 제외합니다. 만료는 '어제 만료된 재료'만 골라 매일 새로 만료된 재료를 한 번만 알립니다.
            .queryString("""
                select new com.fridgeguardian.batch.ExpiringIngredient(i.id, i.user.id, i.name, i.expirationDate)
                from Ingredient i
                where i.expirationDate is not null and i.expirationDate >= :expiredSince and i.expirationDate <= :soonUntil
                order by i.id""")
            .parameterValues(Map.of("expiredSince", today.minusDays(1), "soonUntil", today.plusDays(3)))
            .build();
    }

    // @StepScope 프록시가 리스너 애너테이션을 인식할 수 있도록 인터페이스가 아닌 구체 클래스를 빈으로 등록합니다.
    @Bean
    @StepScope
    ExpiryProcessor expiryProcessor(NotificationRepository notifications,
                                    @Value("#{jobParameters['runDate']}") String runDate) {
        return new ExpiryProcessor(notifications, LocalDate.parse(runDate));
    }

    static class ExpiryProcessor implements ItemProcessor<ExpiringIngredient, Notification> {
        private final NotificationRepository notifications;
        private final LocalDate today;

        ExpiryProcessor(NotificationRepository notifications, LocalDate today) {
            this.notifications = notifications; this.today = today;
        }

        @Override
        public Notification process(ExpiringIngredient item) {
            Notification.Type type = item.expirationDate().isBefore(today) ? Notification.Type.EXPIRED : Notification.Type.EXPIRING;
            // 이미 적재된 알림이면 건너뜁니다(null 반환 = 필터링). 재실행해도 중복이 생기지 않습니다.
            if (notifications.existsByIngredientIdAndTypeAndNotifyDate(item.id(), type, today)) return null;
            return new Notification(item.userId(), item.id(), item.name(), type, today);
        }
    }

    @Bean
    ItemWriter<Notification> notificationWriter(NotificationRepository notifications) {
        return (Chunk<? extends Notification> chunk) -> notifications.saveAll(chunk.getItems());
    }
}
