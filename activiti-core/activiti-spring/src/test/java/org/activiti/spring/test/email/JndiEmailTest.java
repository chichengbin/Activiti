package org.activiti.spring.test.email;

import static java.util.Collections.emptyMap;
import static org.activiti.engine.impl.test.TestHelper.cleanUpDeployments;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;

import javax.mail.NoSuchProviderException;
import javax.mail.Provider;
import javax.mail.Provider.Type;
import javax.mail.Session;
import javax.naming.NamingException;

import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.spring.impl.test.SpringActivitiTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.jndi.SimpleNamingContextBuilder;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class JndiEmailTest extends SpringActivitiTestCase {

    private static Logger logger = LoggerFactory.getLogger(JndiEmailTest.class);

    @Before
    public void setUp() {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.provider.class", MockEmailTransport.class.getName());
        props.put("mail.smtp.class", MockEmailTransport.class.getName());
        props.put("mail.smtp.provider.vendor", "test");
        props.put("mail.smtp.provider.version", "0.0.0");

        Provider provider = new Provider(Type.TRANSPORT, "smtp", MockEmailTransport.class.getName(), "test", "1.0");
        Session mailSession = Session.getDefaultInstance(props);
        try {
            mailSession.setProvider(provider);
            SimpleNamingContextBuilder.emptyActivatedContextBuilder()
                .bind("java:comp/env/Session", mailSession);
        } catch (NamingException e) {
            logger.error("Naming error in email setup", e);
        } catch (NoSuchProviderException e) {
            logger.error("provider error in email setup", e);
        }
    }

    @After
    public void tearDown() {
        cleanUpDeployments(repositoryService);
    }

    @Test
    public void testEmailUsingJndi() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("EmailJndiProcess", emptyMap());
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isEqualTo(0);
    }

}
