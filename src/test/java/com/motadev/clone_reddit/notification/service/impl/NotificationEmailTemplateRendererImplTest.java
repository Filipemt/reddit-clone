package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.notification.dto.RenderedNotificationEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEmailTemplateRendererImplTest {

    private NotificationEmailTemplateRendererImpl renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        renderer = new NotificationEmailTemplateRendererImpl(engine);
    }

    @Test
    void renderPostCreatedUsesTemplateAndSubject() {
        RenderedNotificationEmail rendered = renderer.render(
                "notification.post.created",
                Map.of("username", "alice", "title", "Hello")
        );

        assertThat(rendered.subject()).isEqualTo("New post in your community");
        assertThat(rendered.htmlBody()).contains("alice").contains("Hello");
    }

    @Test
    void renderRejectsUnknownType() {
        assertThatThrownBy(() -> renderer.render("notification.unknown", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported notification type");
    }
}
