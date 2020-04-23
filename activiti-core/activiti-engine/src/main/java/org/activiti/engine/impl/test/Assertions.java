package org.activiti.engine.impl.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.history.HistoryLevel;
import org.activiti.engine.runtime.ProcessInstance;

public class Assertions {
    protected Assertions() {
    }

    public static void assertProcessEndedHistoryData(ProcessEngine processEngine, final String processInstanceId) {
        // Verify historical data if end times are correctly set
        ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
        HistoryService historyService = processEngine.getHistoryService();

        if (processEngineConfiguration.getHistoryLevel().isAtLeast(HistoryLevel.AUDIT)) {

            // process instance
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
            assertThat(historicProcessInstance.getId()).isEqualTo(processInstanceId);
            assertThat(historicProcessInstance.getStartTime()).as("Historic process instance has no start time").isNotNull();
            assertThat(historicProcessInstance.getEndTime()).as("Historic process instance has no end time").isNotNull();

            // tasks
            List<HistoricTaskInstance> historicTaskInstances = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId).list();
            if (historicTaskInstances != null && historicTaskInstances.size() > 0) {
                for (HistoricTaskInstance historicTaskInstance : historicTaskInstances) {
                    assertThat(historicTaskInstance.getProcessInstanceId()).isEqualTo(processInstanceId);
                    assertThat(historicTaskInstance.getStartTime()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no start time").isNotNull();
                    assertThat(historicTaskInstance.getEndTime()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no end time").isNotNull();
                }
            }

            // activities
            List<HistoricActivityInstance> historicActivityInstances = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).list();
            if (historicActivityInstances != null && historicActivityInstances.size() > 0) {
                for (HistoricActivityInstance historicActivityInstance : historicActivityInstances) {
                    assertThat(historicActivityInstance.getProcessInstanceId()).isEqualTo(processInstanceId);
                    assertThat(historicActivityInstance.getStartTime()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no start time").isNotNull();
                    assertThat(historicActivityInstance.getEndTime()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no end time").isNotNull();
                }
            }
        }
    }

    public static void assertProcessEnded(ProcessEngine processEngine, String processInstanceId) {
        ProcessInstance processInstance = processEngine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();

        assertThat(processInstance)
            .as("expected finished process instance '" + processInstanceId + "' but it was still in the db")
            .isNull();

        assertProcessEndedHistoryData(processEngine, processInstanceId);
    }

    public static void assertHistoricTasksDeleteReason(ProcessEngine processEngine, ProcessInstance processInstance, String expectedDeleteReason, String... taskNames) {
        if (processEngine.getProcessEngineConfiguration().getHistoryLevel().isAtLeast(HistoryLevel.AUDIT)) {
            for (String taskName : taskNames) {
                List<HistoricTaskInstance> historicTaskInstances = processEngine.getHistoryService().createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstance.getId()).taskName(taskName).list();
                assertThat(historicTaskInstances).isNotEmpty();
                for (HistoricTaskInstance historicTaskInstance : historicTaskInstances) {
                    assertThat(historicTaskInstance.getEndTime()).isNotNull();
                    if (expectedDeleteReason == null) {
                        assertThat(historicTaskInstance.getDeleteReason()).isNull();
                    } else {
                        assertThat(historicTaskInstance.getDeleteReason().startsWith(expectedDeleteReason)).isTrue();
                    }
                }
            }
        }
    }

    public static void assertHistoricActivitiesDeleteReason(ProcessEngine processEngine, ProcessInstance processInstance, String expectedDeleteReason, String ... activityIds) {
        if (processEngine.getProcessEngineConfiguration().getHistoryLevel().isAtLeast(HistoryLevel.AUDIT)) {
            for (String activityId : activityIds) {
                List<HistoricActivityInstance> historicActivityInstances = processEngine.getHistoryService().createHistoricActivityInstanceQuery()
                    .activityId(activityId).processInstanceId(processInstance.getId()).list();
                assertThat(historicActivityInstances).as("Could not find historic activities").isNotEmpty();
                for (HistoricActivityInstance historicActivityInstance : historicActivityInstances) {
                    assertThat(historicActivityInstance.getEndTime()).isNotNull();
                    if (expectedDeleteReason == null) {
                        assertThat(historicActivityInstance.getDeleteReason()).isNull();
                    } else {
                        assertThat(historicActivityInstance.getDeleteReason().startsWith(expectedDeleteReason)).isTrue();
                    }
                }
            }
        }
    }

    public static void assertHistoryData(ProcessEngine processEngine) {
        ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
        HistoryService historyService = processEngine.getHistoryService();

        if (processEngineConfiguration.getHistoryLevel().isAtLeast(HistoryLevel.AUDIT)) {

            List<HistoricProcessInstance> historicProcessInstances =
                historyService.createHistoricProcessInstanceQuery().finished().list();

            for (HistoricProcessInstance historicProcessInstance : historicProcessInstances) {

                assertThat(historicProcessInstance.getProcessDefinitionId()).as("Historic process instance has no process definition id").isNotNull();
                assertThat(historicProcessInstance.getProcessDefinitionKey()).as("Historic process instance has no process definition key").isNotNull();
                assertThat(historicProcessInstance.getProcessDefinitionVersion()).as("Historic process instance has no process definition version").isNotNull();
                assertThat(historicProcessInstance.getDeploymentId()).as("Historic process instance has no process definition key").isNotNull();
                assertThat(historicProcessInstance.getStartActivityId()).as("Historic process instance has no start activiti id").isNotNull();
                assertThat(historicProcessInstance.getStartTime()).as("Historic process instance has no start time").isNotNull();
                assertThat(historicProcessInstance.getEndTime()).as("Historic process instance has no end time").isNotNull();

                String processInstanceId = historicProcessInstance.getId();

                // tasks
                List<HistoricTaskInstance> historicTaskInstances = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId).list();
                if (historicTaskInstances != null && historicTaskInstances.size() > 0) {
                    for (HistoricTaskInstance historicTaskInstance : historicTaskInstances) {
                        assertThat(historicTaskInstance.getProcessInstanceId()).isEqualTo(processInstanceId);
                        if (historicTaskInstance.getClaimTime() != null) {
                            assertThat(historicTaskInstance.getWorkTimeInMillis()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no work time").isNotNull();
                        }
                        assertThat(historicTaskInstance.getId()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no id").isNotNull();
                        assertThat(historicTaskInstance.getProcessInstanceId()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no process instance id").isNotNull();
                        assertThat(historicTaskInstance.getExecutionId()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no execution id").isNotNull();
                        assertThat(historicTaskInstance.getProcessDefinitionId()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no process definition id").isNotNull();
                        assertThat(historicTaskInstance.getTaskDefinitionKey()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no task definition key").isNotNull();
                        assertThat(historicTaskInstance.getCreateTime()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no create time").isNotNull();
                        assertThat(historicTaskInstance.getStartTime()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no start time").isNotNull();
                        assertThat(historicTaskInstance.getEndTime()).as("Historic task " + historicTaskInstance.getTaskDefinitionKey() + " has no end time").isNotNull();
                    }
                }

                // activities
                List<HistoricActivityInstance> historicActivityInstances = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId).list();
                if (historicActivityInstances != null && historicActivityInstances.size() > 0) {
                    for (HistoricActivityInstance historicActivityInstance : historicActivityInstances) {
                        assertThat(historicActivityInstance.getProcessInstanceId()).isEqualTo(processInstanceId);
                        assertThat(historicActivityInstance.getActivityId()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no activity id").isNotNull();
                        assertThat(historicActivityInstance.getActivityType()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no activity type").isNotNull();
                        assertThat(historicActivityInstance.getProcessDefinitionId()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no process definition id").isNotNull();
                        assertThat(historicActivityInstance.getProcessInstanceId()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no process instance id").isNotNull();
                        assertThat(historicActivityInstance.getExecutionId()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no execution id").isNotNull();
                        assertThat(historicActivityInstance.getStartTime()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no start time").isNotNull();
                        assertThat(historicActivityInstance.getEndTime()).as("Historic activity instance " + historicActivityInstance.getActivityId() + " has no end time").isNotNull();
                    }
                }
            }

        }
    }
}
