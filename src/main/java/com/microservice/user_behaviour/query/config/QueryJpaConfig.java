package com.microservice.user_behaviour.query.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@Profile("query")
@EnableJpaRepositories(
    basePackages = "com.microservice.user_behaviour.query.repository",
    entityManagerFactoryRef = "queryEntityManagerFactory",
    transactionManagerRef = "queryTransactionManager"
)
@EntityScan(basePackages = "com.microservice.user_behaviour.model")
public class QueryJpaConfig {

    @Primary
    @Bean(name = "queryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "queryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("queryDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean entityManagerFactory = 
            new LocalContainerEntityManagerFactoryBean();
        
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan("com.microservice.user_behaviour.model");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        entityManagerFactory.setJpaVendorAdapter(vendorAdapter);
        
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        jpaProperties.put("hibernate.ddl-auto", "none");
        jpaProperties.put("hibernate.show_sql", "false");
        jpaProperties.put("hibernate.format_sql", "false");
        
        entityManagerFactory.setJpaProperties(jpaProperties);
        
        return entityManagerFactory;
    }

    @Primary
    @Bean(name = "queryTransactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("queryEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
} 