package org.activiti.spring.impl.test;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.impl.test.TestHelper;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Removes all deployments at the end of a complete test class.
 * <p>
 * Use this as follows in a Spring test:
 *

 * {@literal @}RunWith(SpringRunner.class)
 * {@literal @}TestExecutionListeners(CleanTestExecutionListener.class)
 * {@literal @}ContextConfiguration("...")
 */
public class CleanTestExecutionListener extends AbstractTestExecutionListener {

  @Override
  public void afterTestClass(TestContext testContext) {
    RepositoryService repositoryService = testContext.getApplicationContext().getBean(RepositoryService.class);
    TestHelper.cleanUpDeployments(repositoryService);
  }

}
