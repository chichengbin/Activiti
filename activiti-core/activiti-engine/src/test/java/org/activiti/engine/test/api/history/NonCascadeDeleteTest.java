package org.activiti.engine.test.api.history;

import static org.assertj.core.api.Assertions.assertThat;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.impl.history.HistoryLevel;
import org.activiti.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NonCascadeDeleteTest {

    private static String PROCESS_DEFINITION_KEY = "oneTaskProcess";

    protected ProcessEngine processEngine;
    protected ProcessEngineConfiguration processEngineConfiguration;
    protected RepositoryService repositoryService;
    protected RuntimeService runtimeService;
    protected HistoryService historyService;

    protected ProcessEngineConfiguration createProcessEngineConfiguration() {
        return ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
    }

    @Before
    public void setUp() {
        processEngineConfiguration = createProcessEngineConfiguration();
        processEngine = processEngineConfiguration.buildProcessEngine();
        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();
        historyService = processEngine.getHistoryService();
    }

    @After
    public void tearDown() throws Exception {
        processEngine.close();
    }

    @Test
    public void testHistoricProcessInstanceQuery() {
        String deploymentId = repositoryService.createDeployment()
            .addClasspathResource("org/activiti/engine/test/api/runtime/oneTaskProcess.bpmn20.xml")
            .deploy().getId();

        String processInstanceId = runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY).getId();
        TaskService taskService = processEngine.getTaskService();
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        taskService.complete(task.getId());

        if (processEngineConfiguration.getHistoryLevel().isAtLeast(HistoryLevel.ACTIVITY)) {
            HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
            assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);

            // Delete deployment and historic process instance remains.
            repositoryService.deleteDeployment(deploymentId, false);

            HistoricProcessInstance processInstanceAfterDelete = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
            assertThat(processInstanceAfterDelete.getProcessDefinitionKey()).isNull();
            assertThat(processInstanceAfterDelete.getProcessDefinitionName()).isNull();
            assertThat(processInstanceAfterDelete.getProcessDefinitionVersion()).isNull();

            // clean
            historyService.deleteHistoricProcessInstance(processInstanceId);
        }
    }

}
