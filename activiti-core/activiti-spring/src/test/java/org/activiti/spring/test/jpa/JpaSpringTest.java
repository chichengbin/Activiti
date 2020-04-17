package org.activiti.spring.test.jpa;

import static org.activiti.engine.impl.util.CollectionUtil.map;
import static org.activiti.engine.impl.util.CollectionUtil.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.Deployment;
import org.activiti.spring.impl.test.SpringActivitiTestCase;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

/**
 *
 */
@ContextConfiguration
public class JpaSpringTest extends SpringActivitiTestCase {

    @Test
    @Deployment(resources = "org/activiti/spring/test/jpa/JPASpringTest.bpmn20.xml")
    public void testJpaVariableHappyPath() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("LoanRequestProcess", map(
            "customerName", "John Doe",
            "amount", 15000L
        ));

        // Variable should be present containing the loanRequest created by the spring bean
        Object value = runtimeService.getVariable(processInstance.getId(), "loanRequest");
        assertThat(value).isNotNull();
        assertThat(value).isInstanceOf(LoanRequest.class);
        LoanRequest request = (LoanRequest) value;
        assertThat(request.getCustomerName()).isEqualTo("John Doe");
        assertThat(request.getAmount().longValue()).isEqualTo(15000L);
        assertThat(request.isApproved()).isFalse();

        // We will approve the request, which will update the entity
        Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(task).isNotNull();
        taskService.complete(task.getId(), singletonMap("approvedByManager", Boolean.TRUE));

        // If approved, the processsInstance should be finished, gateway based on loanRequest.approved value
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isEqualTo(0);
    }

    @Test
    @Deployment(resources = "org/activiti/spring/test/jpa/JPASpringTest.bpmn20.xml")
    public void testJpaVariableDisapprovalPath() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("LoanRequestProcess", map(
            "customerName", "Jane Doe",
            "amount", 50000
        ));

        // Variable should be present containing the loanRequest created by the spring bean
        Object value = runtimeService.getVariable(processInstance.getId(), "loanRequest");
        assertThat(value).isNotNull();
        assertThat(value).isInstanceOf(LoanRequest.class);
        LoanRequest request = (LoanRequest) value;
        assertThat(request.getCustomerName()).isEqualTo("Jane Doe");
        assertThat(request.getAmount().longValue()).isEqualTo(50000L);
        assertThat(request.isApproved()).isFalse();

        // We will disapprove the request, which will update the entity
        Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(task).isNotNull();
        taskService.complete(task.getId(), singletonMap("approvedByManager", Boolean.FALSE));

        runtimeService.getVariable(processInstance.getId(), "loanRequest");
        request = (LoanRequest) value;
        assertThat(request.isApproved()).isFalse();

        // If disapproved, an extra task will be available instead of the process ending
        task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getName()).isEqualTo("Send rejection letter");
    }

}
