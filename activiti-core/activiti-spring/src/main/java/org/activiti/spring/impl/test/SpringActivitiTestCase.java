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

package org.activiti.spring.impl.test;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.test.JobTestHelper;
import org.activiti.engine.impl.test.TestHelper;
import org.activiti.engine.test.ActivitiRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 *
 */
@RunWith(SpringRunner.class)
public abstract class SpringActivitiTestCase {

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    protected RepositoryService repositoryService;

    @Autowired
    protected RuntimeService runtimeService;

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected ProcessEngineConfiguration processEngineConfiguration;

    @Autowired
    protected ManagementService managementService;

    @Autowired
    protected HistoryService historyService;

    @Autowired
    protected TaskService taskService;

    @Rule
    public ActivitiRule activitiRule = new ActivitiRule();

    @Before
    public void setUp() {
        activitiRule.setProcessEngine(processEngine);
    }

    @After
    public void tearDown() {
        TestHelper.cleanUpDeployments(repositoryService);
    }

    protected void waitForJobExecutorToProcessAllJobs(long maxMillisToWait, long intervalMillis) {
        JobTestHelper.waitForJobExecutorToProcessAllJobs(activitiRule, maxMillisToWait, intervalMillis);
    }

    protected void assertProcessEnded(String processInstanceId) {
        TestHelper.assertProcessEnded(processEngine, processInstanceId);
    }

}
