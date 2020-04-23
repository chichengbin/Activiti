package org.activiti.engine.impl.test;

import java.io.InputStream;
import java.lang.reflect.Method;
import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.impl.bpmn.deployer.ResourceNameUtil;
import org.activiti.engine.impl.cfg.CommandExecutorImpl;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.activiti.engine.impl.interceptor.CommandInterceptor;
import org.activiti.engine.impl.interceptor.CommandInvoker;
import org.activiti.engine.impl.interceptor.DebugCommandInvoker;
import org.activiti.engine.impl.util.ReflectUtil;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.test.Deployment;
import org.activiti.engine.test.EnableVerboseExecutionTreeLogging;
import org.activiti.engine.test.mock.ActivitiMockSupport;
import org.activiti.engine.test.mock.MockServiceTask;
import org.activiti.engine.test.mock.MockServiceTasks;
import org.activiti.engine.test.mock.NoOpServiceTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnotationSupport {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationSupport.class);

    protected AnnotationSupport() {
    }

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
}
