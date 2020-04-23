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

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.EndEvent;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.DynamicBpmnService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.ProcessEngineImpl;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractActivitiTestCase extends TestCase {

  private static final Logger logger = LoggerFactory.getLogger(AbstractActivitiTestCase.class);

  protected ProcessEngine processEngine;

  protected String deploymentIdFromDeploymentAnnotation;
  protected List<String> deploymentIdsForAutoCleanup = new ArrayList<String>();
  protected Throwable exception;

  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected RepositoryService repositoryService;
  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected HistoryService historyService;
  protected ManagementService managementService;
  protected DynamicBpmnService dynamicBpmnService;

  protected void setUp() throws Exception {
    // Always reset authenticated user to avoid any mistakes
    Authentication.setAuthenticatedUserId(null);
  }

  protected void additionalConfiguration(ProcessEngineConfiguration processEngineConfiguration) {
  }

  protected void initializeProcessEngine() {
      additionalConfiguration(processEngineConfiguration);
      processEngine = processEngineConfiguration.buildProcessEngine();
  };

  // Default: do nothing
  protected void closeDownProcessEngine() {
      processEngine.close();
  }

  @Override
  public void runBare() throws Throwable {
    initializeProcessEngine();

    if (repositoryService == null) {
      initializeServices();
    }

    try {

      deploymentIdFromDeploymentAnnotation = AnnotationSupport.annotationDeploymentSetUp(processEngine, getClass(), getName());

      super.runBare();

      Assertions.assertHistoryData(processEngine);

    } catch (AssertionError e) {
      logger.error("ASSERTION FAILED: {}", e, e);
      exception = e;
      throw e;

    } catch (Throwable e) {
      logger.error("EXCEPTION: {}", e, e);
      exception = e;
      throw e;

    } finally {

      if (deploymentIdFromDeploymentAnnotation != null) {
        AnnotationSupport.annotationDeploymentTearDown(processEngine, deploymentIdFromDeploymentAnnotation, getClass(), getName());
        deploymentIdFromDeploymentAnnotation = null;
      }

      for (String autoDeletedDeploymentId : deploymentIdsForAutoCleanup) {
        repositoryService.deleteDeployment(autoDeletedDeploymentId, true);
      }
      deploymentIdsForAutoCleanup.clear();

      assertAndEnsureCleanDb();
      processEngineConfiguration.getClock().reset();

      // Can't do this in the teardown, as the teardown will be called as part of the super.runBare
      closeDownProcessEngine();
    }
  }

    /**
   * Each test is assumed to clean up all DB content it entered. After a test method executed, this method scans all tables to see if the DB is completely clean.
   * It fails in case the DB is not clean. If the DB is not clean, it is cleaned by performing a create a drop.
   */
  protected void assertAndEnsureCleanDb() {
    TestHelper.ensureCleanDb(processEngine);
  }

  protected void initializeServices() {
    processEngineConfiguration = ((ProcessEngineImpl) processEngine).getProcessEngineConfiguration();
    repositoryService = processEngine.getRepositoryService();
    runtimeService = processEngine.getRuntimeService();
    taskService = processEngine.getTaskService();
    historyService = processEngine.getHistoryService();
    managementService = processEngine.getManagementService();
    dynamicBpmnService = processEngine.getDynamicBpmnService();
  }

  /**
   * Since the 'one task process' is used everywhere the actual process content doesn't matter, instead of copying around the BPMN 2.0 xml one could use this methodwhich gives a {@link BpmnModel}
   * version of the same process back.
   */
  public BpmnModel createOneTaskTestProcess() {
    BpmnModel model = new BpmnModel();
    org.activiti.bpmn.model.Process process = new org.activiti.bpmn.model.Process();
    model.addProcess(process);
    process.setId("oneTaskProcess");
    process.setName("The one task process");

    StartEvent startEvent = new StartEvent();
    startEvent.setId("start");
    process.addFlowElement(startEvent);

    UserTask userTask = new UserTask();
    userTask.setName("The Task");
    userTask.setId("theTask");
    userTask.setAssignee("kermit");
    process.addFlowElement(userTask);

    EndEvent endEvent = new EndEvent();
    endEvent.setId("theEnd");
    process.addFlowElement(endEvent);

    process.addFlowElement(new SequenceFlow("start", "theTask"));
    process.addFlowElement(new SequenceFlow("theTask", "theEnd"));

    return model;
  }

  public BpmnModel createTwoTasksTestProcess() {
    BpmnModel model = new BpmnModel();
    org.activiti.bpmn.model.Process process = new org.activiti.bpmn.model.Process();
    model.addProcess(process);
    process.setId("twoTasksProcess");
    process.setName("The two tasks process");

    StartEvent startEvent = new StartEvent();
    startEvent.setId("start");
    process.addFlowElement(startEvent);

    UserTask userTask = new UserTask();
    userTask.setName("The First Task");
    userTask.setId("task1");
    userTask.setAssignee("kermit");
    process.addFlowElement(userTask);

    UserTask userTask2 = new UserTask();
    userTask2.setName("The Second Task");
    userTask2.setId("task2");
    userTask2.setAssignee("kermit");
    process.addFlowElement(userTask2);

    EndEvent endEvent = new EndEvent();
    endEvent.setId("theEnd");
    process.addFlowElement(endEvent);

    process.addFlowElement(new SequenceFlow("start", "task1"));
    process.addFlowElement(new SequenceFlow("start", "task2"));
    process.addFlowElement(new SequenceFlow("task1", "theEnd"));
    process.addFlowElement(new SequenceFlow("task2", "theEnd"));

    return model;
  }

  /**
   * Creates and deploys the one task process. See {@link #createOneTaskTestProcess()}.
   *
   * @return The process definition id (NOT the process definition key) of deployed one task process.
   */
  public String deployOneTaskTestProcess() {
    BpmnModel bpmnModel = createOneTaskTestProcess();
    Deployment deployment = repositoryService.createDeployment().addBpmnModel("oneTasktest.bpmn20.xml", bpmnModel).deploy();

    deploymentIdsForAutoCleanup.add(deployment.getId()); // For auto-cleanup

    ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
    return processDefinition.getId();
  }

  public String deployTwoTasksTestProcess() {
    BpmnModel bpmnModel = createTwoTasksTestProcess();
    Deployment deployment = repositoryService.createDeployment().addBpmnModel("twoTasksTestProcess.bpmn20.xml", bpmnModel).deploy();

    deploymentIdsForAutoCleanup.add(deployment.getId()); // For auto-cleanup

    ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
    return processDefinition.getId();
  }

}
