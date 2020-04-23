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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.impl.asyncexecutor.AsyncExecutor;

/**
 * This helper class helps sharing the same code for jobExecutor test helpers,
 * between JUnit 3 and JUnit 4 test support classes
 */
public class JobTestHelper {

    public static void waitForJobExecutorToProcessAllJobs(ProcessEngine processEngine, long maxMillisToWait, long intervalMillis) {
        waitForJobExecutorToProcessAllJobs(processEngine, maxMillisToWait, intervalMillis, true);
    }

    public static void waitForJobExecutorToProcessAllJobs(ProcessEngine processEngine, long maxMillisToWait, long intervalMillis, boolean shutdownExecutorWhenFinished) {
        AsyncExecutor asyncExecutor = processEngine.getProcessEngineConfiguration().getAsyncExecutor();
        asyncExecutor.start();

        try {
            Timer timer = new Timer();
            InterruptTask task = new InterruptTask(Thread.currentThread());
            timer.schedule(task, maxMillisToWait);
            boolean areJobsAvailable = true;
            try {
                while (areJobsAvailable && !task.isTimeLimitExceeded()) {
                    Thread.sleep(intervalMillis);
                    try {
                        areJobsAvailable = areJobsAvailable(processEngine);
                    } catch (Throwable t) {
                        // Ignore, possible that exception occurs due to locking/updating of table on MSSQL when
                        // isolation level doesn't allow READ of the table
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            } finally {
                timer.cancel();
            }
            if (areJobsAvailable) {
                throw new ActivitiException("time limit of " + maxMillisToWait + " was exceeded");
            }

        } finally {
            if (shutdownExecutorWhenFinished) {
                asyncExecutor.shutdown();
            }
        }
    }

    public static void waitForJobExecutorToProcessAllJobsAndExecutableTimerJobs(ProcessEngine processEngine, long maxMillisToWait, long intervalMillis) {
        waitForJobExecutorToProcessAllJobsAndExecutableTimerJobs(processEngine, maxMillisToWait, intervalMillis, true);
    }

    public static void waitForJobExecutorToProcessAllJobsAndExecutableTimerJobs(ProcessEngine processEngine, long maxMillisToWait, long intervalMillis, boolean shutdownExecutorWhenFinished) {
        ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
        AsyncExecutor asyncExecutor = processEngineConfiguration.getAsyncExecutor();
        asyncExecutor.start();
        processEngineConfiguration.setAsyncExecutorActivate(true);

        try {
            Timer timer = new Timer();
            InterruptTask task = new InterruptTask(Thread.currentThread());
            timer.schedule(task, maxMillisToWait);
            boolean areJobsAvailable = true;
            try {
                while (areJobsAvailable && !task.isTimeLimitExceeded()) {
                    Thread.sleep(intervalMillis);
                    try {
                        areJobsAvailable = areJobsOrExecutableTimersAvailable(processEngine);
                    } catch (Throwable t) {
                        // Ignore, possible that exception occurs due to locking/updating of table on MSSQL when
                        // isolation level doesn't allow READ of the table
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            } finally {
                timer.cancel();
            }
            if (areJobsAvailable) {
                throw new ActivitiException("time limit of " + maxMillisToWait + " was exceeded");
            }

        } finally {
            if (shutdownExecutorWhenFinished) {
                processEngineConfiguration.setAsyncExecutorActivate(false);
                asyncExecutor.shutdown();
            }
        }
    }

    public static void waitForJobExecutorOnCondition(ProcessEngine processEngine, long maxMillisToWait, long intervalMillis, Callable<Boolean> condition) {
        AsyncExecutor asyncExecutor = processEngine.getProcessEngineConfiguration().getAsyncExecutor();
        asyncExecutor.start();

        try {
            Timer timer = new Timer();
            InterruptTask task = new InterruptTask(Thread.currentThread());
            timer.schedule(task, maxMillisToWait);
            boolean conditionIsViolated = true;
            try {
                while (conditionIsViolated) {
                    Thread.sleep(intervalMillis);
                    conditionIsViolated = !condition.call();
                }
            } catch (InterruptedException e) {
                // ignore
            } catch (Exception e) {
                throw new ActivitiException("Exception while waiting on condition: " + e.getMessage(), e);
            } finally {
                timer.cancel();
            }

            if (conditionIsViolated) {
                throw new ActivitiException("time limit of " + maxMillisToWait + " was exceeded");
            }

        } finally {
            asyncExecutor.shutdown();
        }
    }

    public static void executeJobExecutorForTime(ProcessEngine processEngine, long maxMillisToWait, long intervalMillis) {
        AsyncExecutor asyncExecutor = processEngine.getProcessEngineConfiguration().getAsyncExecutor();
        asyncExecutor.start();

        try {
            Timer timer = new Timer();
            InterruptTask task = new InterruptTask(Thread.currentThread());
            timer.schedule(task, maxMillisToWait);
            try {
                while (!task.isTimeLimitExceeded()) {
                    Thread.sleep(intervalMillis);
                }
            } catch (InterruptedException e) {
                // ignore
            } finally {
                timer.cancel();
            }

        } finally {
            asyncExecutor.shutdown();
        }
    }

    public static boolean areJobsAvailable(ProcessEngine processEngine) {
        ManagementService managementService = processEngine.getManagementService();
        return !managementService.createJobQuery().list().isEmpty();
    }

    public static boolean areJobsOrExecutableTimersAvailable(ProcessEngine processEngine) {
        ManagementService managementService = processEngine.getManagementService();
        boolean emptyJobs = managementService.createJobQuery().list().isEmpty();
        if (emptyJobs) {
            return !managementService.createTimerJobQuery().executable().list().isEmpty();
        } else {
            return true;
        }
    }

    private static class InterruptTask extends TimerTask {

        protected boolean timeLimitExceeded;
        protected Thread thread;

        public InterruptTask(Thread thread) {
            this.thread = thread;
        }

        public boolean isTimeLimitExceeded() {
            return timeLimitExceeded;
        }

        public void run() {
            timeLimitExceeded = true;
            thread.interrupt();
        }
    }

}
