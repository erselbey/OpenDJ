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
package org.opends.server.api;
import org.forgerock.i18n.LocalizableMessage;




import java.util.List;

import org.forgerock.opendj.server.config.server.SynchronizationProviderCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.*;



/**
 * This class defines the set of methods and structures that are
 * available for use in a Directory Server synchronization provider.
 * A synchronization provider ensures that changes in one instance of
 * the Directory Server are properly communicated to other instances,
 * and potentially to other kinds of applications, so that they can be
 * updated accordingly.
 *
 * @param <T> the configuration for the synchronization provider.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class
       SynchronizationProvider<T extends SynchronizationProviderCfg>
{
  /**
   * Performs any initialization that might be necessary for this
   * synchronization provider.
   *
   * @param serverContext The server context.
   * @param  config  The configuration information for this
   *                 synchronization provider.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid configuration for this
   *                           synchronization provider.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   initializing the
   *                                   synchronization provider that
   *                                   is not related to the server
   *                                   configuration.
   */
  public abstract void initializeSynchronizationProvider(ServerContext serverContext, T config)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this synchronization provider.  It should be possible to call
   * this method on an uninitialized synchronization provider instance
   * in order to determine whether the synchronization provider would
   * be able to use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The synchronization provider
   *                              configuration for which to make the
   *                              the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this synchronization provider, or {@code false} if
   *          not.
   */
  public boolean isConfigurationAcceptable(
                      SynchronizationProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by synchronization
    // provider implementations that wish to perform more detailed
    // validation.
    return true;
  }



  /**
   * Performs any necessary final initialization processing for this
   * synchronization provider.
   * This will be called just after the provider has been
   * registered with the server but before it has been unloaded.
   */
  public void completeSynchronizationProvider()
  {
    // No implementation is required by default.
  }

  /**
   * Performs any necessary finalization for this synchronization
   * provider.  This will be called just after the provider has been
   * unregistered with the server but before it has been unloaded.
   */
  public void finalizeSynchronizationProvider()
  {
    // No implementation is required by default.
  }



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult handleConflictResolution(
         PreOperationAddOperation addOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult.ContinueProcessing();
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided add operation is performed.  This
   * method will be invoked immediately before processing the add
   * operation in the backend.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult doPreOperation(
         PreOperationAddOperation addOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided add operation is performed.  This
   * method will be invoked immediately after processing the add
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(
         PostOperationAddOperation addOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult
         handleConflictResolution(
         PreOperationDeleteOperation deleteOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult.ContinueProcessing();
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided delete operation is performed.  This
   * method will be invoked immediately before processing the delete
   * operation in the backend.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult
         doPreOperation(PreOperationDeleteOperation deleteOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided delete operation is performed.  This
   * method will be invoked immediately after processing the delete
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(
         PostOperationDeleteOperation deleteOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult
         handleConflictResolution(
         PreOperationModifyOperation modifyOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult.ContinueProcessing();
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided modify operation is performed.  This
   * method will be invoked immediately before processing the modify
   * operation in the backend.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult
         doPreOperation(PreOperationModifyOperation modifyOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided modify operation is performed.  This
   * method will be invoked immediately after processing the modify
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(
         PostOperationModifyOperation modifyOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult handleConflictResolution(
         PreOperationModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult.ContinueProcessing();
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided modify DN operation is performed.
   * This method will be invoked immediately before processing the
   * modify DN operation in the backend.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult doPreOperation(
         PreOperationModifyDNOperation modifyDNOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided modify DN operation is performed.  This
   * method will be invoked immediately after processing the modify DN
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(
         PostOperationModifyDNOperation modifyDNOperation)
         throws DirectoryException;

  /**
   * Performs any processing that may be required whenever the server
   * schema has been updated.  This may be invoked for schema
   * modifications made with the server online, and it may also be
   * called if the server detects that there were any scheam changes
   * made with the server offline (e.g., by directly editing the
   * schema configuration files).
   * <BR><BR>
   * At the time this method is called, the schema changes will have
   * already been applied to the server.  As such, this method must
   * make a best effort attempt to process the associated schema
   * changes, and is not allowed to throw any exceptions.
   *
   * @param  modifications  The set of modifications that have been
   *                        made to the server schema.
   */
  public abstract void processSchemaChange(List<Modification>
                                                modifications);
}

