/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS.
 */
package org.opends.server.core.networkgroups;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.Workflow;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Modification;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;
import static org.testng.Assert.*;

/**
 * This set of tests test the network groups.
 */
@SuppressWarnings("javadoc")
public class NetworkGroupTest extends DirectoryServerTestCase {

  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available,
    // so we'll start the server.
    TestCaseUtils.startServer();
  }

  /**
   * Provides information to create a network group with one workflow inside.
   *
   * Each set of DNs contains:
   * - one network group identifier
   * - one base DN for the workflow to register with the network group

   */
  @DataProvider (name = "DNSet_0")
  public Object[][] initDNSet_0() throws Exception
  {
    // Network group ID
    String networkGroupID1 = "networkGroup1";
    String networkGroupID2 = "networkGroup2";

    // Workflow base DNs
    DN dn1 = DN.valueOf("o=test1");
    DN dn2 = DN.valueOf("o=test2");

    // Network group info
    return new Object[][] {
        // Test1: create a network group with the identifier networkGroupID1
        { networkGroupID1, dn1 },

        // Test2: create the same network group to check that previous
        // network group was properly cleaned.
        { networkGroupID1, dn1 },

        // Test3: create another network group
        { networkGroupID2, dn2 },
    };
  }


  /**
   * Provides a single DN to search a workflow in a network group.
   *
   * Each set of DNs is composed of:
   * - one baseDN
   * - one subordinateDN
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the default network group
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the administration network group
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the internal network group
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "DNSet_1")
  public Object[][] initDNSet_1() throws Exception
  {
    DN dnRootDSE = DN.valueOf("");
    DN dnConfig  = DN.valueOf("cn=config");
    DN dnMonitor = DN.valueOf("cn=monitor");
    DN dnSchema  = DN.valueOf("cn=schema");
    DN dnTasks   = DN.valueOf("cn=tasks");
    DN dnBackups = DN.valueOf("cn=backups");
    DN dnDummy   = DN.valueOf("o=dummy_suffix");

    DN dnSubordinateConfig  = DN.valueOf("cn=Work Queue,cn=config");
    DN dnSubordinateMonitor = DN.valueOf("cn=schema Backend,cn=monitor");
    DN dnSubordinateTasks   = DN.valueOf("cn=Scheduled Tasks,cn=tasks");
    // No DN subordinate for schema because the schema backend is
    // currently empty.
    // No DN subordinate for cn=backups because by default there is no
    // child entry under cn=backups.

    // Sets of DNs
    return new Object[][] {
        { dnRootDSE,  null,                 true,  },
        { dnConfig,   dnSubordinateConfig,  true,  },
        { dnMonitor,  dnSubordinateMonitor, true,  },
        { dnTasks,    dnSubordinateTasks,   true,  },
        { dnSchema,   null,                 true,  },
        { dnBackups,  null,                 true,  },
        { dnDummy,    null,                 false, },
    };
  }

  /**
   * Tests the network group registration.
   *
   * @param networkGroupID   the ID of the network group to register
   * @param workflowBaseDN1  the base DN of the first workflow node to register
   *                         in the network group
   */
  @Test (dataProvider = "DNSet_0", groups = "virtual")
  public void testNetworkGroupRegistration(String networkGroupID, DN workflowBaseDN) throws Exception
  {
    NetworkGroup networkGroup = new NetworkGroup(networkGroupID);
    registerWorkflow(networkGroup, workflowBaseDN);

    try
    {
      registerWorkflow(networkGroup, workflowBaseDN);
      fail("DirectoryException should have been thrown on double registration");
    }
    catch (DirectoryException de)
    {
      assertTrue(StaticUtils.hasDescriptor(de.getMessageObject(),
          ERR_REGISTER_WORKFLOW_NODE_ALREADY_EXISTS));
    }
  }

  /**
   * This test checks that network groups are updated as appropriate when
   * backend base DNs are added or removed. When a new backend base DN is
   * added, the new suffix should be accessible for the route process - ie.
   * a workflow should be created and be a potential candidate for the route
   * process. Similarly, when a backend base DN is removed its associated
   * workflow should be removed; subsequently, any request targeting the
   * removed suffix should be rejected and a no such entry status code be
   * returned.
   */
  @Test
  public void testBackendBaseDNModification() throws Exception
  {
    String suffix  = "dc=example,dc=com";
    String suffix2 = "o=networkgroup suffix";
    String backendBaseDNName = "ds-cfg-base-dn";

    // Initialize a backend with a base entry.
    TestCaseUtils.clearJEBackend(true, "userRoot", suffix);

    // Check that suffix is accessible while suffix2 is not.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Add a new suffix in the backend and create a base entry for the
    // new suffix.
    String backendConfigDN = "ds-cfg-backend-id=userRoot," + DN_BACKEND_BASE;
    modifyAttribute(backendConfigDN, ModificationType.ADD, backendBaseDNName, suffix2);
    addBaseEntry(suffix2, "networkgroup suffix");

    // Both old and new suffix should be accessible.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.SUCCESS);

    // Remove the new suffix...
    modifyAttribute(backendConfigDN, ModificationType.DELETE, backendBaseDNName, suffix2);

    // ...and check that the removed suffix is no more accessible.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Replace the suffix with suffix2 in the backend
    modifyAttribute(backendConfigDN, ModificationType.REPLACE, backendBaseDNName, suffix2);

    // Now none of the suffixes are accessible: this means the entries
    // under the old suffix are not moved to the new suffix.
    searchEntry(suffix, ResultCode.NO_SUCH_OBJECT);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Add a base entry for the new suffix
    addBaseEntry(suffix2, "networkgroup suffix");

    // The new suffix is accessible while the old one is not.
    searchEntry(suffix, ResultCode.NO_SUCH_OBJECT);
    searchEntry(suffix2, ResultCode.SUCCESS);

    // Reset the configuration with previous suffix
    modifyAttribute(backendConfigDN, ModificationType.REPLACE, backendBaseDNName, suffix);
  }

  /**
   * Tests the mechanism to attribute a network group to a client connection,
   * comparing the priority.
   * Create 2 network groups with different priorities.
   */
  @Test(groups = "virtual")
  public void testNetworkGroupPriority() throws Exception
  {
    String ng1 = "group1";
    String ng2 = "group2";
    DN dn1 = DN.valueOf("o=test1");
    DN dn2 = DN.valueOf("o=test2");

    // Create and register the network group with the server.
    NetworkGroup networkGroup1 = new NetworkGroup(ng1);
    NetworkGroup networkGroup2 = new NetworkGroup(ng2);

    // Register the workflow with the network group.
    registerWorkflow(networkGroup1, dn1);
    registerWorkflow(networkGroup2, dn2);
  }

  private void registerWorkflow(NetworkGroup networkGroup, DN dn) throws DirectoryException
  {
    String workflowId = dn.toString();
    networkGroup.registerWorkflow(workflowId, dn, createAndRegister(workflowId, null));
  }

  /**
   * This test checks that the network group takes into account the
   * subordinate naming context defined in the RootDSEBackend.
   */
  @Test
  public void testRootDseSubordinateNamingContext() throws Exception
  {
    // Backends for the test
    String backend1   = "o=test-rootDSE-subordinate-naming-context-1";
    String backend2   = "o=test-rootDSE-subordinate-naming-context-2";
    String backendID1 = "test-rootDSE-subordinate-naming-context-1";
    String backendID2 = "test-rootDSE-subordinate-naming-context-2";

    TestCaseUtils.clearDataBackends();

    // At this point, the list of subordinate naming context is not defined
    // yet (null): any public backend should be visible. Create a backend
    // with a base entry and check that the test naming context is visible.
    TestCaseUtils.initializeMemoryBackend(backendID1, backend1, true);
    searchPublicNamingContexts(ResultCode.SUCCESS, 1);

    // Create another test backend and check that the new backend is visible
    TestCaseUtils.initializeMemoryBackend(backendID2, backend2, true);
    searchPublicNamingContexts(ResultCode.SUCCESS, 2);

    // Now put in the list of subordinate naming context the backend1 naming context.
    // This white list will prevent the backend2 to be visible.
    TestCaseUtils.dsconfig(
        "set-root-dse-backend-prop",
        "--set", "subordinate-base-dn:" + backend1);
    searchPublicNamingContexts(ResultCode.SUCCESS, 1);

    // === Cleaning

    // Reset the subordinate naming context list.
    // Both naming context should be visible again.
    TestCaseUtils.dsconfig(
        "set-root-dse-backend-prop",
        "--reset", "subordinate-base-dn");
    searchPublicNamingContexts(ResultCode.SUCCESS, 2);

    // Clean the test backends. There is no more naming context.
    TestCaseUtils.clearMemoryBackend(backendID1);
    TestCaseUtils.clearMemoryBackend(backendID2);
    searchPublicNamingContexts(ResultCode.NO_SUCH_OBJECT, 0);
  }


  /**
   * Searches the list of naming contexts.
   *
   * @param expectedRC  the expected result code
   * @param expectedNamingContexts  the number of expected naming contexts
   */
  private void searchPublicNamingContexts(ResultCode expectedRC, int expectedNamingContexts) throws Exception
  {
    InternalClientConnection conn = InternalClientConnection.getRootConnection();
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.SINGLE_LEVEL);
    SearchOperation search = conn.processSearch(request);

    // Check the number of found naming context
    assertEquals(search.getResultCode(), expectedRC);
    if (expectedRC == ResultCode.SUCCESS)
    {
      assertEquals(search.getEntriesSent(), expectedNamingContexts);
    }
  }


  /**
   * Searches an entry on a given connection.
   *
   * @param baseDN the request base DN string
   * @param expectedRC the expected result code
   */
  private void searchEntry(String baseDN, ResultCode expectedRC) throws Exception
  {
    SearchRequest request = newSearchRequest(DN.valueOf(baseDN), SearchScope.BASE_OBJECT);
    SearchOperation search = getRootConnection().processSearch(request);
    assertEquals(search.getResultCode(), expectedRC);
  }


  /**
   * Creates a base entry for the given suffix.
   *
   * @param suffix      the suffix for which the base entry is to be created
   */
  private void addBaseEntry(String suffix, String namingAttribute) throws Exception
  {
    TestCaseUtils.addEntry(
        "dn: " + suffix,
        "objectClass: top",
        "objectClass: organization",
        "o: " + namingAttribute);
  }


  /**
   * Adds/Deletes/Replaces an attribute in a given entry.
   *
   * @param baseDN          the request base DN string
   * @param modType         the modification type (add/delete/replace)
   * @param attributeName   the name  of the attribute to add/delete/replace
   * @param attributeValue  the value of the attribute to add/delete/replace
   */
  private void modifyAttribute(
      String baseDN,
      ModificationType modType,
      String  attributeName,
      String  attributeValue
      ) throws Exception
  {
    ArrayList<Modification> mods = new ArrayList<Modification>();
    Attribute attributeToModify = Attributes.create(attributeName, attributeValue);
    mods.add(new Modification(modType, attributeToModify));
    ModifyOperation modifyOperation = getRootConnection().processModify(DN.valueOf(baseDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }


  /**
   * Checks the DN routing through a network group.
   *
   * @param dnToSearch      the DN of a workflow in the network group; may
   *                        be null
   * @param dnSubordinate   a subordinate of dnToSearch
   * @param unrelatedDN     a DN with no hierarchical relationship with
   *                        any of the DNs above, may be null
   * @param shouldExist     true if we are supposed to find a workflow for
   *                        dnToSearch
   */
  @Test (dataProvider = "DNSet_1", groups = "virtual")
  public void doCheckNetworkGroup(
      DN           dnToSearch,
      DN           dnSubordinate,
      boolean      shouldExist
      )
  {
    if (dnToSearch == null)
    {
      return;
    }

    // Let's retrieve the workflow that maps best the dnToSearch
    Workflow workflow = NetworkGroup.getWorkflowCandidate(dnToSearch);
    if (shouldExist)
    {
      assertNotNull(workflow);
    }
    else
    {
      assertNull(workflow);
    }

    // let's retrieve the workflow that handles the DN subordinate:
    // it should be the same than the one for dnToSearch
    if (dnSubordinate != null)
    {
       Workflow workflow2 = NetworkGroup.getWorkflowCandidate(dnSubordinate);
       assertEquals(workflow2, workflow);
    }
  }

}
