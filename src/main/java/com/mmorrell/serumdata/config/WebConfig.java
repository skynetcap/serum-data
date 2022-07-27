package com.mmorrell.serumdata.config;

import com.mmorrell.serumdata.util.RpcUtil;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.BeanNameViewResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

@EnableWebMvc
@EnableScheduling
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final int HTTP_TIMEOUT_SECONDS = 30;

    @Bean
    public ViewResolver viewResolver() {
        InternalResourceViewResolver bean = new InternalResourceViewResolver();

        bean.setViewClass(JstlView.class);
        bean.setPrefix("/templates/");
        bean.setSuffix(".jsp");

        return bean;
    }

    @Bean
    public BeanNameViewResolver beanNameViewResolver() {
        return new BeanNameViewResolver();
    }

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        WebMvcConfigurer.super.addResourceHandlers(registry);
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(24);
        threadPoolTaskScheduler.setThreadNamePrefix("SerumThread");
        return threadPoolTaskScheduler;
    }

    /**
     * Used for updating market listings every 5 minutes.
     */
    @Bean
    public RpcClient backgroundRpcClient() {
        return new RpcClient(RpcUtil.getPublicEndpoint(), HTTP_TIMEOUT_SECONDS);
    }
}