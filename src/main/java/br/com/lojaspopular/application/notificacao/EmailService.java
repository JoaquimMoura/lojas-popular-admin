package br.com.lojaspopular.application.notificacao;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;

  	/*Map<String, Object> vars = Map.of(
		  "nomeCliente", pedido.getUsuario().getNome(),
		  "numeroPedido", pedido.getId(),
		  "dataPedido", pedido.getCriadoEm(),
		  "status", pedido.getStatus().name(),
		  "total", pedido.getTotal(),
		  "linkPagamento", pedido.getPaymentLink()
		  );
   */
  
  /* 
   * emailService.enviarEmailComTemplate(
		  pedido.getUsuario().getEmail(),
		  "Confirmação do Pedido #" + pedido.getId(),
		  "email-confirmacao-pedido",
		  vars
		  );
   */
  
  public void enviarEmail(String para, String assunto, String template, Object dados) {
    try {
    	
      Context context = new Context();
      context.setVariable("dados", dados);
      String corpo = templateEngine.process("email/" + template, context);

      MimeMessage mensagem = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
      helper.setTo(para);
      helper.setSubject(assunto);
      helper.setText(corpo, true);
      mailSender.send(mensagem);
    } catch (Exception e) {
      throw new RuntimeException("Erro ao enviar e-mail: " + e.getMessage(), e);
    }
  }
}
