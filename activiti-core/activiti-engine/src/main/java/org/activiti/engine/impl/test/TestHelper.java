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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.impl.ProcessEngineImpl;
import org.activiti.engine.impl.bpmn.deployer.ResourceNameUtil;
import org.activiti.engine.impl.bpmn.parser.factory.ActivityBehaviorFactory;
import org.activiti.engine.impl.db.DbSqlSession;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.util.ReflectUtil;
import org.activiti.engine.test.TestActivityBehaviorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class TestHelper extends Assertions {

    private static final Logger logger = LoggerFactory.getLogger(TestHelper.class);

    public static final String EMPTY_LINE = "\n";

    private static final List<String> TABLENAMES_EXCLUDED_FROM_DB_CLEAN_CHECK = singletonList("ACT_GE_PROPERTY");

    static Map<String, ProcessEngine> processEngines = new HashMap<String, ProcessEngine>();

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
    public static void ensureCleanDb(ProcessEngine processEngine) {
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
