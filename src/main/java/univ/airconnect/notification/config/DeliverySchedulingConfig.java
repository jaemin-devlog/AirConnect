package univ.airconnect.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Provider latency must not starve durable chat retries or other scheduled maintenance. */
@Configuration(proxyBeanMethods = false)
public class DeliverySchedulingConfig {
    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() { return scheduler("scheduled-"); }

    @Bean(name = "pushDeliveryScheduler")
    ThreadPoolTaskScheduler pushDeliveryScheduler() { return scheduler("push-delivery-"); }

    @Bean(name = "chatDeliveryScheduler")
    ThreadPoolTaskScheduler chatDeliveryScheduler() { return scheduler("chat-delivery-"); }

    private ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
}
