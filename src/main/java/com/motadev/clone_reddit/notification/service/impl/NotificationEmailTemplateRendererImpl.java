package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.notification.dto.RenderedNotificationEmail;
import com.motadev.clone_reddit.notification.service.NotificationEmailTemplateRendererI;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Component
public class NotificationEmailTemplateRendererImpl implements NotificationEmailTemplateRendererI {

    private static final Map<String, TemplateSpec> TEMPLATES = Map.of(
            "notification.post.created",
            new TemplateSpec("mail/notification-post-created", "New post in your community"),
            "notification.post.commented",
            new TemplateSpec("mail/notification-post-commented", "New comment on your post"),
            "notification.comment.replied",
            new TemplateSpec("mail/notification-comment-replied", "New reply to your comment"),
            "notification.vote.upvoted",
            new TemplateSpec("mail/notification-vote-upvoted", "Your content received an upvote")
    );

    private final TemplateEngine templateEngine;

    public NotificationEmailTemplateRendererImpl(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public RenderedNotificationEmail render(String notificationType, Map<String, Object> variables) {
        TemplateSpec spec = TEMPLATES.get(notificationType);
        if (spec == null) {
            throw new IllegalArgumentException("Unsupported notification type: " + notificationType);
        }

        Context context = new Context();
        if (variables != null) {
            context.setVariables(variables);
        }
        String htmlBody = templateEngine.process(spec.templateName(), context);
        return new RenderedNotificationEmail(spec.subject(), htmlBody);
    }

    private record TemplateSpec(String templateName, String subject) {
    }
}
