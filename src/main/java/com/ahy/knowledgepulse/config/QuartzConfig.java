package com.ahy.knowledgepulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        Properties props = new Properties();
        props.put("org.quartz.scheduler.instanceName", "KnowledgePulseScheduler");
        props.put("org.quartz.threadPool.threadCount", "5");
        props.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        factory.setQuartzProperties(props);
        return factory;
    }
}
