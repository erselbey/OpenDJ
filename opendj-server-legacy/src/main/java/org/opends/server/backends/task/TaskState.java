/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.task;

import static org.opends.messages.TaskMessages.*;

import org.forgerock.i18n.LocalizableMessage;

/** This enumeration defines the various states that a task can have during its lifetime. */
public enum TaskState
{
  /**
   * The task state that indicates that the task has not yet been scheduled,
   * or possibly that the scheduler is currently not running.
   */
  UNSCHEDULED(INFO_TASK_STATE_UNSCHEDULED.get()),



  /**
   * The task state that indicates that the task has been disabled by an
   * administrator.
   */
  DISABLED(INFO_TASK_STATE_DISABLED.get()),



  /**
   * The task state that indicates that the task's scheduled start time has not
   * yet arrived.
   */
  WAITING_ON_START_TIME(INFO_TASK_STATE_WAITING_ON_START_TIME.get()),



  /**
   * The task state that indicates that at least one of the task's defined
   * dependencies has not yet completed.
   */
  WAITING_ON_DEPENDENCY(INFO_TASK_STATE_WAITING_ON_DEPENDENCY.get()),



  /**
   * The task state that indicates that the task is currently running.
   */
  RUNNING(INFO_TASK_STATE_RUNNING.get()),



  /**
   * The task state that indicates that the task is recurring.
   */
  RECURRING(INFO_TASK_STATE_RECURRING.get()),



  /**
   * The task state that indicates that the task has completed without any
   * errors.
   */
  COMPLETED_SUCCESSFULLY(INFO_TASK_STATE_COMPLETED_SUCCESSFULLY.get()),



  /**
   * The task state that indicates that the task was able to complete its
   * intended goal, but that one or more errors were encountered during the
   * process.
   */
  COMPLETED_WITH_ERRORS(INFO_TASK_STATE_COMPLETED_WITH_ERRORS.get()),



  /**
   * The task state that indicates that the task was unable to complete because
   * it was interrupted by the shutdown of the task backend.
   */
  STOPPED_BY_SHUTDOWN(INFO_TASK_STATE_STOPPED_BY_SHUTDOWN.get()),



  /**
   * The task state that indicates that one or more errors prevented the task
   * from completing.
   */
  STOPPED_BY_ERROR(INFO_TASK_STATE_STOPPED_BY_ERROR.get()),



  /**
   * The task state that indicates that the task was stopped by an administrator
   * after it had already started but before it was able to complete.
   */
  STOPPED_BY_ADMINISTRATOR(INFO_TASK_STATE_STOPPED_BY_ADMINISTRATOR.get()),



  /**
   * The task state that indicates that the task was canceled by an
   * administrator before it started running.
   */
  CANCELED_BEFORE_STARTING(INFO_TASK_STATE_CANCELED_BEFORE_STARTING.get());






  /**
   * Indicates whether a task with the specified state is currently pending
   * execution.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  {@code true} if the task state indicates that the task is
   *          currently pending, or {@code false} otherwise.
   */
  public static boolean isPending(TaskState taskState)
  {
    switch (taskState)
    {
      case UNSCHEDULED:
      case WAITING_ON_START_TIME:
      case WAITING_ON_DEPENDENCY:
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether a task with the specified state is currently running.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  {@code true} if the task state indicates that the task is
   *          currently running, or {@code false} otherwise.
   */
  public static boolean isRunning(TaskState taskState)
  {
    switch (taskState)
    {
      case RUNNING:
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether a task with the specified state is recurring.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  {@code true} if the task state indicates that the task
   *          is recurring, or {@code false} otherwise.
   */
  public static boolean isRecurring(TaskState taskState)
  {
    switch (taskState)
    {
      case RECURRING:
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether a task with the specified state has completed all the
   * processing that it will do, regardless of whether it completed its
   * intended goal.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  {@code false} if the task state indicates that the task has
   *          not yet started or is currently running, or {@code true} otherwise
   */
  public static boolean isDone(TaskState taskState)
  {
    switch (taskState)
    {
      case UNSCHEDULED:
      case WAITING_ON_START_TIME:
      case WAITING_ON_DEPENDENCY:
      case RUNNING:
        return false;
      default:
        return true;
    }
  }



  /**
   * Indicates whether a task with the specified state has been able to complete
   * its intended goal.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  {@code true} if the task state indicates that the task
   *          completed successfully or with minor errors that still allowed it
   *          to achieve its goal, or {@code false} otherwise.
   */
  public static boolean isSuccessful(TaskState taskState)
  {
    switch (taskState)
    {
      case WAITING_ON_START_TIME:
      case WAITING_ON_DEPENDENCY:
      case RUNNING:
      case STOPPED_BY_ERROR:
      case COMPLETED_WITH_ERRORS:
        return false;
      default:
        return true;
    }
  }


  /**
   * Indicates whether this task has been cancelled.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  {@code true} if the task state indicates that the task
   *          was cancelled either before or during execution, or
   *          {@code false} otherwise.
   */
  public static boolean isCancelled(TaskState taskState)
  {
    switch(taskState)
    {
      case STOPPED_BY_ADMINISTRATOR:
      case CANCELED_BEFORE_STARTING:
        return true;
      default:
        return false;
    }
  }

  /**
   * Retrieves the task state that corresponds to the provided string value.
   *
   * @param  s  The string value for which to retrieve the corresponding task
   *            state.
   *
   * @return  The corresponding task state, or {@code null} if none could
   *          be associated with the provided string.
   */
  public static TaskState fromString(String s)
  {
    switch (s.toLowerCase())
    {
    case "unscheduled":
      return UNSCHEDULED;
    case "disabled":
      return DISABLED;
    case "waiting_on_start_time":
      return WAITING_ON_START_TIME;
    case "waiting_on_dependency":
      return WAITING_ON_DEPENDENCY;
    case "running":
      return RUNNING;
    case "recurring":
      return RECURRING;
    case "completed_successfully":
      return COMPLETED_SUCCESSFULLY;
    case "completed_with_errors":
      return COMPLETED_WITH_ERRORS;
    case "stopped_by_shutdown":
      return STOPPED_BY_SHUTDOWN;
    case "stopped_by_error":
      return STOPPED_BY_ERROR;
    case "stopped_by_administrator":
      return STOPPED_BY_ADMINISTRATOR;
    case "canceled_before_starting":
      return CANCELED_BEFORE_STARTING;
    default:
      return null;
    }
  }

  private LocalizableMessage displayName;

  /**
   * Gets a locale sensitive representation of this state.
   *
   * @return LocalizableMessage describing state
   */
  public LocalizableMessage getDisplayName() {
    return displayName;
  }

  private TaskState(LocalizableMessage displayName) {
    this.displayName = displayName;
  }
}

