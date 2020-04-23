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

package org.activiti.examples.bpmn.receivetask;

import static org.activiti.engine.impl.test.Assertions.assertProcessEnded;
import static org.assertj.core.api.Assertions.assertThat;

import org.activiti.engine.impl.test.PluggableActivitiTestCase;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.test.Deployment;

/**
 *
 */
public class ReceiveTaskTest extends PluggableActivitiTestCase {

    @Deployment
    public void testWaitStateBehavior() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("receiveTask");
        Execution execution = runtimeService.createExecutionQuery().processInstanceId(processInstance.getId()).activityId("waitState").singleResult();
        assertThat(execution).isNotNull();

        runtimeService.trigger(execution.getId());
        assertProcessEnded(processEngine, processInstance.getId());
    }

}
