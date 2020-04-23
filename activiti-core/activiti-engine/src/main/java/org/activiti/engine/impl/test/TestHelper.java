/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.engine.impl.test;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.ProcessEngineImpl;
import org.activiti.engine.impl.bpmn.deployer.ResourceNameUtil;
import org.activiti.engine.impl.bpmn.parser.factory.ActivityBehaviorFactory;
import org.activiti.engine.impl.cfg.CommandExecutorImpl;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.db.DbSqlSession;
import org.activiti.engine.impl.history.HistoryLevel;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.activiti.engine.impl.interceptor.CommandInterceptor;
import org.activiti.engine.impl.interceptor.CommandInvoker;
import org.activiti.engine.impl.interceptor.DebugCommandInvoker;
import org.activiti.engine.impl.util.ReflectUtil;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.test.Deployment;
import org.activiti.engine.test.EnableVerboseExecutionTreeLogging;
import org.activiti.engine.test.TestActivityBehaviorFactory;
import org.activiti.engine.test.mock.ActivitiMockSupport;
import org.activiti.engine.test.mock.MockServiceTask;
import org.activiti.engine.test.mock.MockServiceTasks;
import org.activiti.engine.test.mock.NoOpServiceTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(TestHelper.class);

    public static final String EMPTY_LINE = "\n";

    private static final List<String> TABLENAMES_EXCLUDED_FROM_DB_CLEAN_CHECK = singletonList("ACT_GE_PROPERTY");

    static Map<String, ProcessEngine> processEngines = new HashMap<String, ProcessEngine>();

    // Assertion methods ///////////////////////////////////////////////////

    public static void assertProcessEnded(ProcessEngine processEngine, String processInstanceId) {
        ProcessInstance processInstance = processEngine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();

        assertThat(processInstance)
            .as("expected finished process instance '" + processInstanceId + "' but it was still in the db")
            .isNull();

        assertProcessEndedHistoryData(processEngine, processInstanceId);
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

    // Test annotation support /////////////////////////////////////////////

    public static void annotationEnableVerboseExecutionTreeLoggingSetUp(Class<?> testClass, ProcessEngineConfigurationImpl processEngineConfiguration) {
        // Enable verbose execution tree debugging if needed
        if (testClass.isAnnotationPresent(EnableVerboseExecutionTreeLogging.class)) {
            swapCommandInvoker(processEngineConfiguration, true);
        }
    }

    public static void annotationEnableVerboseExecutionTreeLoggingTearDown(Class<?> testClass, ProcessEngineConfigurationImpl processEngineConfiguration) {
        // Reset command invoker
        if (testClass.isAnnotationPresent(EnableVerboseExecutionTreeLogging.class)) {
            swapCommandInvoker(processEngineConfiguration, false);
        }
    }

    public static void swapCommandInvoker(ProcessEngineConfigurationImpl processEngineConfiguration, boolean debug) {
        CommandExecutor commandExecutor = processEngineConfiguration.getCommandExecutor();
        if (commandExecutor instanceof CommandExecutorImpl) {
            CommandExecutorImpl commandExecutorImpl = (CommandExecutorImpl) commandExecutor;

            CommandInterceptor previousCommandInterceptor = null;
            CommandInterceptor commandInterceptor = commandExecutorImpl.getFirst();

            while (commandInterceptor != null) {

                boolean matches = debug ? (commandInterceptor instanceof CommandInvoker) : (commandInterceptor instanceof DebugCommandInvoker);
                if (matches) {

                    CommandInterceptor commandInvoker = debug ? new DebugCommandInvoker() : new CommandInvoker();
                    if (previousCommandInterceptor != null) {
                        previousCommandInterceptor.setNext(commandInvoker);
                    } else {
                        commandExecutorImpl.setFirst(previousCommandInterceptor);
                    }
                    break;

                } else {
                    previousCommandInterceptor = commandInterceptor;
                    commandInterceptor = commandInterceptor.getNext();
                }
            }

        } else {
            logger.warn("Not using " + CommandExecutorImpl.class + ", ignoring the "
                + EnableVerboseExecutionTreeLogging.class + " annotation");
        }
    }

    public static String annotationDeploymentSetUp(ProcessEngine processEngine, Class<?> testClass, String methodName) {
        String deploymentId = null;
        Method method = null;
        try {
            method = testClass.getMethod(methodName, (Class<?>[]) null);
        } catch (Exception e) {
            logger.warn("Could not get method by reflection. This could happen if you are using @Parameters in combination with annotations.", e);
            return null;
        }
        Deployment deploymentAnnotation = method.getAnnotation(Deployment.class);
        if (deploymentAnnotation != null) {
            logger.debug("annotation @Deployment creates deployment for {}.{}", testClass.getSimpleName(), methodName);
            String[] resources = deploymentAnnotation.resources();
            if (resources.length == 0) {
                String name = method.getName();
                String resource = getBpmnProcessDefinitionResource(testClass, name);
                resources = new String[] {resource};
            }

            DeploymentBuilder deploymentBuilder = processEngine.getRepositoryService().createDeployment().name(testClass.getSimpleName() + "." + methodName);

            for (String resource : resources) {
                deploymentBuilder.addClasspathResource(resource);
            }

            if (deploymentAnnotation.tenantId() != null
                && deploymentAnnotation.tenantId().length() > 0) {
                deploymentBuilder.tenantId(deploymentAnnotation.tenantId());
            }
            deploymentId = deploymentBuilder.deploy().getId();
        }

        return deploymentId;
    }

    public static void annotationDeploymentTearDown(ProcessEngine processEngine, String deploymentId, Class<?> testClass, String methodName) {
        logger.debug("annotation @Deployment deletes deployment for {}.{}", testClass.getSimpleName(), methodName);
        if (deploymentId != null) {
            try {
                processEngine.getRepositoryService().deleteDeployment(deploymentId, true);
            } catch (ActivitiObjectNotFoundException e) {
                // Deployment was already deleted by the test case. Ignore.
            }
        }
    }

    public static void annotationMockSupportSetup(Class<?> testClass, String methodName, ActivitiMockSupport mockSupport) {

        // Get method
        Method method = null;
        try {
            method = testClass.getMethod(methodName, (Class<?>[]) null);
        } catch (Exception e) {
            logger.warn("Could not get method by reflection. This could happen if you are using @Parameters in combination with annotations.", e);
            return;
        }

        handleMockServiceTaskAnnotation(mockSupport, method);
        handleMockServiceTasksAnnotation(mockSupport, method);
        handleNoOpServiceTasksAnnotation(mockSupport, method);
    }

    protected static void handleMockServiceTaskAnnotation(ActivitiMockSupport mockSupport, Method method) {
        MockServiceTask mockedServiceTask = method.getAnnotation(MockServiceTask.class);
        if (mockedServiceTask != null) {
            handleMockServiceTaskAnnotation(mockSupport, mockedServiceTask);
        }
    }

    protected static void handleMockServiceTaskAnnotation(ActivitiMockSupport mockSupport, MockServiceTask mockedServiceTask) {
        mockSupport.mockServiceTaskWithClassDelegate(mockedServiceTask.originalClassName(), mockedServiceTask.mockedClassName());
    }

    protected static void handleMockServiceTasksAnnotation(ActivitiMockSupport mockSupport, Method method) {
        MockServiceTasks mockedServiceTasks = method.getAnnotation(MockServiceTasks.class);
        if (mockedServiceTasks != null) {
            for (MockServiceTask mockedServiceTask : mockedServiceTasks.value()) {
                handleMockServiceTaskAnnotation(mockSupport, mockedServiceTask);
            }
        }
    }

    protected static void handleNoOpServiceTasksAnnotation(ActivitiMockSupport mockSupport, Method method) {
        NoOpServiceTasks noOpServiceTasks = method.getAnnotation(NoOpServiceTasks.class);
        if (noOpServiceTasks != null) {

            String[] ids = noOpServiceTasks.ids();
            Class<?>[] classes = noOpServiceTasks.classes();
            String[] classNames = noOpServiceTasks.classNames();

            if ((ids == null || ids.length == 0) && (classes == null || classes.length == 0) && (classNames == null || classNames.length == 0)) {
                mockSupport.setAllServiceTasksNoOp();
            } else {

                if (ids != null && ids.length > 0) {
                    for (String id : ids) {
                        mockSupport.addNoOpServiceTaskById(id);
                    }
                }

                if (classes != null && classes.length > 0) {
                    for (Class<?> clazz : classes) {
                        mockSupport.addNoOpServiceTaskByClassName(clazz.getName());
                    }
                }

                if (classNames != null && classNames.length > 0) {
                    for (String className : classNames) {
                        mockSupport.addNoOpServiceTaskByClassName(className);
                    }
                }

            }

        }
    }

    public static void annotationMockSupportTeardown(ActivitiMockSupport mockSupport) {
        mockSupport.reset();
    }

    /**
     * get a resource location by convention based on a class (type) and a relative resource name. The return value will be the full classpath location of the type, plus a suffix built from the name
     * parameter: <code>BpmnDeployer.BPMN_RESOURCE_SUFFIXES</code>. The first resource matching a suffix will be returned.
     */
    public static String getBpmnProcessDefinitionResource(Class<?> type, String name) {
        for (String suffix : ResourceNameUtil.BPMN_RESOURCE_SUFFIXES) {
            String resource = type.getName().replace('.', '/') + "." + name + "." + suffix;
            InputStream inputStream = ReflectUtil.getResourceAsStream(resource);
            if (inputStream == null) {
                continue;
            } else {
                return resource;
            }
        }
        return type.getName().replace('.', '/') + "." + name + "." + ResourceNameUtil.BPMN_RESOURCE_SUFFIXES[0];
    }

    // Engine startup and shutdown helpers
    // ///////////////////////////////////////////////////

    public static ProcessEngine getProcessEngine(String configurationResource) {
        ProcessEngine processEngine = processEngines.get(configurationResource);
        if (processEngine == null) {
            logger.debug("==== BUILDING PROCESS ENGINE ========================================================================");
            processEngine = ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(configurationResource).buildProcessEngine();
            logger.debug("==== PROCESS ENGINE CREATED =========================================================================");
            processEngines.put(configurationResource, processEngine);
        }
        return processEngine;
    }

    public static void closeProcessEngines() {
        for (ProcessEngine processEngine : processEngines.values()) {
            processEngine.close();
        }
        processEngines.clear();
    }

    public static void cleanUpDeployments() {
        processEngines.values().forEach(processEngine -> cleanUpDeployments(processEngine.getRepositoryService()));
    }

    public static void cleanUpDeployments(RepositoryService repositoryService) {
        repositoryService.createDeploymentQuery().list()
            .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
    }

    /**
     * Each test is assumed to clean up all DB content it entered. After a test method executed, this method scans all tables to see if the DB is completely clean.
     * It fails in case the DB is not clean. If the DB is not clean, it is cleaned by performing a create a drop.
     */
    public static void assertAndEnsureCleanDb(ProcessEngine processEngine) {
        logger.debug("verifying that db is clean after test");
        Map<String, Long> tableCounts = processEngine.getManagementService().getTableCount();
        StringBuilder outputMessage = new StringBuilder();
        for (String tableName : tableCounts.keySet()) {
            String tableNameWithoutPrefix = tableName.replace(processEngine.getProcessEngineConfiguration().getDatabaseTablePrefix(), "");
            if (!TABLENAMES_EXCLUDED_FROM_DB_CLEAN_CHECK.contains(tableNameWithoutPrefix)) {
                Long count = tableCounts.get(tableName);
                if (count != 0L) {
                    outputMessage.append("  ").append(tableName).append(": ").append(count).append(" record(s) ");
                }
            }
        }
        if (outputMessage.length() > 0) {
            outputMessage.insert(0, "DB NOT CLEAN: \n");
            logger.error(EMPTY_LINE);
            logger.error(outputMessage.toString());

            ((ProcessEngineImpl) processEngine).getProcessEngineConfiguration().getCommandExecutor().execute(new Command<Object>() {
                public Object execute(CommandContext commandContext) {
                    DbSqlSession dbSqlSession = commandContext.getDbSqlSession();
                    dbSqlSession.dbSchemaDrop();
                    dbSqlSession.dbSchemaCreate();
                    return null;
                }
            });

            fail(outputMessage.toString());
        }
    }

    // Mockup support ////////////////////////////////////////////////////////

    public static TestActivityBehaviorFactory initializeTestActivityBehaviorFactory(ActivityBehaviorFactory existingActivityBehaviorFactory) {
        return new TestActivityBehaviorFactory(existingActivityBehaviorFactory);
    }

}
