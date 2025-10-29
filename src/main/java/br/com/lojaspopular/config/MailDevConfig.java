package br.com.lojaspopular.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailDevConfig {

    @Bean
    @Profile("local")
    public JavaMailSender mockMailSender() {
        return new JavaMailSenderImpl() {
            @Override
            public void send(SimpleMailMessage simpleMessage) {
                System.out.println("📧 [MOCK] Email enviado para: " + String.join(",", simpleMessage.getTo()));
                System.out.println("📄 Assunto: " + simpleMessage.getSubject());
                System.out.println("📝 Corpo: " + simpleMessage.getText());
            }
        };
    }
}

