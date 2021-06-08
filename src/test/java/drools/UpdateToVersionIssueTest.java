package drools;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Inspired" by examples from:
 * https://github.com/kiegroup/drools/blob/master/drools-model/drools-model-compiler/src/test/java/org/drools/modelcompiler/IncrementalCompilationTest.java
 *
 * @author Nicolas BUCHON
 *
 */
public class UpdateToVersionIssueTest extends BaseModelTest {
  static final Logger LOG = LoggerFactory.getLogger(UpdateToVersionIssueTest.class);
  private static final String DRL1 = "package org.drools.incremental\n import " + Message.class.getCanonicalName()
      + ";\n rule R1 when\n $m : Message( value.startsWith(\"H\") )\n then\n System.out.println($m.getValue() + \" firing r1\"); end\n";
  private static final String DRL2_1_FUNCTION_BASE = "package org.drools.incremental\nimport " + Message.class.getCanonicalName()
      + ";\n {}rule R2 when\n  $m : Message( value == \"Hi Universe\" )\nthen\n  System.out.println($m.getValue() + \" firing r2a\");end\n";
  private static final String DRL2_2_FUNCTION_BASE = "package org.drools.incremental\nimport " + Message.class.getCanonicalName()
      + ";\n {}rule R2 when\n   $m : Message( value == \"Hello World\" )\nthen\n  System.out.println($m.getValue() + \" firing r2b\");end\n";

  private static final String DRL2_1_TOKEN_BASE = "package org.drools.incremental\nimport " + Message.class.getCanonicalName()
      + ";\n {}rule R2 when\n  $t : TokenNewDay()\n $m : Message( value == \"Hi Universe\" )\nthen\n  System.out.println($m.getValue() + \" firing r2a\"+\" \"+$t.getTs());end\n";
  private static final String DRL2_2_TOKEN_BASE = "package org.drools.incremental\nimport " + Message.class.getCanonicalName()
      + ";\n {}rule R2 when\n  $t : TokenNewDay()\n  $m : Message( value == \"Hello World\" )\nthen\n  System.out.println($m.getValue() + \" firing r2b\"+\" \"+$t.getTs());end\n";

  private static final String FUNCTION_2_1 = "function String testFormat(String value){\n return value+\" test1\";\n}\n";
  private static final String FUNCTION_2_2 = "function String testFormat(String value){\n return value+\" test2\";\n}\n";

  private static final String TOKEN_2_1 =
      "declare TokenNewDay\n @role(event)\n ts : long\nend\n rule \"Init TokenNewDay\"\n when\n not TokenNewDay()\n then\n  TokenNewDay token = new TokenNewDay();\n token.setTs(kcontext.getKnowledgeRuntime().getSessionClock().getCurrentTime());\n insert(token);\n System.out.println(\"Init Token: \"+ token);\n end\n";
  private static final String TOKEN_2_2 = UpdateToVersionIssueTest.TOKEN_2_1 + "\n declare Whatever\n   @role(event)\n @expires(3d)\n end\n";

  private static final String GROUP_ID = "me.nbuchon";

  public UpdateToVersionIssueTest(final RUN_TYPE testRunType) {
    super(testRunType);
  }

  public class Message implements Serializable {
    private final String value;

    public Message(final String value) {
      this.value = value;
    }

    public String getValue() {
      return this.value;
    }
  }

  /**
   * Control condition : everything is consistent
   *
   * @throws Exception
   */
  @Test
  public void upgradeToVersionControlCondition() throws Exception {
    UpdateToVersionIssueTest.LOG.info("*************************************************");
    UpdateToVersionIssueTest.LOG.info("***************** TEST CLOUD ********************");
    UpdateToVersionIssueTest.LOG.info("*************************************************");

    final KieServices ks = KieServices.Factory.get();

    // Create an in-memory jar for version 1.0.0
    final ReleaseId releaseId1 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.0.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId1, UpdateToVersionIssueTest.DRL1, UpdateToVersionIssueTest.DRL2_1_FUNCTION_BASE.replace("{}", ""));

    // Create a session insert and fire rules
    final KieContainer kc = ks.newKieContainer(releaseId1);
    final KieSession ksession = kc.newKieSession();
    final EntryPoint entryPoint = ksession.getEntryPoint("DEFAULT");
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(1, ksession.fireAllRules());

    // Create a new jar for version 1.1.0
    final ReleaseId releaseId2 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.1.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId2, UpdateToVersionIssueTest.DRL1, UpdateToVersionIssueTest.DRL2_2_FUNCTION_BASE.replace("{}", ""));

    // try to update the container to version 1.1.0
    Assert.assertTrue(kc.updateToVersion(releaseId2).getMessages().isEmpty());

    // continue working with the session
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(3, ksession.fireAllRules());
    // why 3? because r1 already triggered by message 1, and r2B triggered by both messages

    ksession.dispose();
  }

  /**
   * Test with a function (not even used!) in rule 2 (both version), modified and updated as well
   *
   * @throws Exception
   */
  @Test
  public void upgradeToVersionWithModifiedFunction() throws Exception {
    UpdateToVersionIssueTest.LOG.info("*************************************************");
    UpdateToVersionIssueTest.LOG.info("***************** TEST CLOUD F ******************");
    UpdateToVersionIssueTest.LOG.info("*************************************************");

    final KieServices ks = KieServices.Factory.get();

    // Create an in-memory jar for version 1.0.0
    final ReleaseId releaseId1 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.0.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId1, UpdateToVersionIssueTest.DRL1,
        UpdateToVersionIssueTest.DRL2_1_FUNCTION_BASE.replace("{}", UpdateToVersionIssueTest.FUNCTION_2_1));

    // Create a session insert and fire rules
    final KieContainer kc = ks.newKieContainer(releaseId1);
    final KieSession ksession = kc.newKieSession();
    final EntryPoint entryPoint = ksession.getEntryPoint("DEFAULT");
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(1, ksession.fireAllRules());

    // Create a new jar for version 1.1.0
    final ReleaseId releaseId2 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.1.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId2, UpdateToVersionIssueTest.DRL1,
        UpdateToVersionIssueTest.DRL2_2_FUNCTION_BASE.replace("{}", UpdateToVersionIssueTest.FUNCTION_2_2));

    // try to update the container to version 1.1.0
    Assert.assertTrue(kc.updateToVersion(releaseId2).getMessages().isEmpty());

    // continue working with the session
    entryPoint.insert(new Message("Hello World"));
    // Expected is 3, as in control condition
    // result is 4, KO
    Assert.assertEquals(4, ksession.fireAllRules());
    // firing again R1 rule for first message?!

    ksession.dispose();
  }

  /**
   * Another control condition : everything is consistent (adding unique token init and use)
   *
   * @throws Exception
   */
  @Test
  public void upgradeToVersionControlConditionToken() throws Exception {
    UpdateToVersionIssueTest.LOG.info("*************************************************");
    UpdateToVersionIssueTest.LOG.info("***************** TEST CLOUD T ******************");
    UpdateToVersionIssueTest.LOG.info("*************************************************");

    final KieServices ks = KieServices.Factory.get();

    // Create an in-memory jar for version 1.0.0
    final ReleaseId releaseId1 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.0.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId1, UpdateToVersionIssueTest.DRL1, UpdateToVersionIssueTest.DRL2_1_TOKEN_BASE.replace("{}", UpdateToVersionIssueTest.TOKEN_2_1));

    // Create a session insert and fire rules
    final KieContainer kc = ks.newKieContainer(releaseId1);
    final KieSession ksession = kc.newKieSession();
    final EntryPoint entryPoint = ksession.getEntryPoint("DEFAULT");
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(2, ksession.fireAllRules());
    // One token init, one R1

    // inserting + firing again:
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(1, ksession.fireAllRules());
    // token already exist, only R1 matching new fact

    // Create a new jar for version 1.1.0
    final ReleaseId releaseId2 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.1.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId2, UpdateToVersionIssueTest.DRL1, UpdateToVersionIssueTest.DRL2_2_TOKEN_BASE.replace("{}", UpdateToVersionIssueTest.TOKEN_2_2));

    // try to update the container to version 1.1.0
    Assert.assertTrue(kc.updateToVersion(releaseId2).getMessages().isEmpty());

    // continue working with the session
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(4, ksession.fireAllRules());
    // why 4? 1 new R1, and 3 r2B for 3 messages
    this.logObjectsInSession(ksession);

    ksession.dispose();
  }

  /***
   * Another test, adding a token supposed to be unique, in both versions of rule 2, ending in having 2 instances of it
   * in the session
   *
   * @throws Exception
   */
  @Test
  public void upgradeToVersionWithModifiedFunctionAndToken() throws Exception {
    UpdateToVersionIssueTest.LOG.info("*************************************************");
    UpdateToVersionIssueTest.LOG.info("***************** TEST CLOUD FT *****************");
    UpdateToVersionIssueTest.LOG.info("*************************************************");

    final KieServices ks = KieServices.Factory.get();

    // Create an in-memory jar for version 1.0.0
    final ReleaseId releaseId1 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.0.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId1, UpdateToVersionIssueTest.DRL1,
        UpdateToVersionIssueTest.DRL2_1_TOKEN_BASE.replace("{}", UpdateToVersionIssueTest.FUNCTION_2_1 + UpdateToVersionIssueTest.TOKEN_2_1));

    // Create a session and fire rules
    final KieContainer kc = ks.newKieContainer(releaseId1);
    final KieSession ksession = kc.newKieSession();
    final EntryPoint entryPoint = ksession.getEntryPoint("DEFAULT");
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(2, ksession.fireAllRules());
    // 2 because init Token AND R1, OK

    // inserting + firing again:
    entryPoint.insert(new Message("Hello World"));
    Assert.assertEquals(1, ksession.fireAllRules());
    // token already exist, only R1 matching new fact OK

    // Create a new jar for version 1.1.0
    final ReleaseId releaseId2 = ks.newReleaseId(UpdateToVersionIssueTest.GROUP_ID, "test-upgrade", "1.1.0");
    this.createAndDeployJar(ks, EventProcessingOption.CLOUD, releaseId2, UpdateToVersionIssueTest.DRL1,
        UpdateToVersionIssueTest.DRL2_2_TOKEN_BASE.replace("{}", UpdateToVersionIssueTest.FUNCTION_2_2 + UpdateToVersionIssueTest.TOKEN_2_2));

    // try to update the container to version 1.1.0
    Assert.assertTrue(kc.updateToVersion(releaseId2).getMessages().isEmpty());

    // continue working with the session
    entryPoint.insert(new Message("Hello World"));
    // Expected is 4, as in control condition
    // result is 7, KO
    Assert.assertEquals(7, ksession.fireAllRules());
    // All seems forgotten : firing again R1 rule for first messages (3)
    // + 1 new init token, + 3 r2b

    this.logObjectsInSession(ksession);

    ksession.dispose();
  }

  private void logObjectsInSession(final KieSession kieSession) {
    final Map<String, AtomicInteger> evtFactsCount = new HashMap<>();
    UpdateToVersionIssueTest.LOG.info("");
    UpdateToVersionIssueTest.LOG.info("****************logging objects in session********************");

    for (final Object o : kieSession.getObjects()) {
      final String oName = o.getClass().getName();
      final AtomicInteger counter = evtFactsCount.get(oName);
      if (counter == null) {
        evtFactsCount.put(oName, new AtomicInteger(1));
      } else {
        counter.addAndGet(1);
      }
    }
    evtFactsCount.keySet().forEach(className -> {
      UpdateToVersionIssueTest.LOG.info(className + " : " + evtFactsCount.get(className).get());
      kieSession.getObjects(new ObjectFilter() {

        @Override
        public boolean accept(final Object object) {
          return className.equals(object.getClass().getName());
        }
      }).forEach(obj -> UpdateToVersionIssueTest.LOG.info("------>" + obj.toString()));

    });
    UpdateToVersionIssueTest.LOG.info("**************************************************************");
    UpdateToVersionIssueTest.LOG.info("");
  }
}
