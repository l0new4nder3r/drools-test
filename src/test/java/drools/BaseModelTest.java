package drools;

import java.util.List;
import java.util.stream.Collectors;

import org.drools.compiler.kie.builder.impl.DrlProject;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.conf.ClockTypeOption;

/***
 * "Inspired" from
 * https://github.com/kiegroup/drools/blob/00657c78d252951abbf8b541a7fc2417df8d6fb1/drools-model/drools-model-compiler/src/test/java/org/drools/modelcompiler/BaseModelTest.java#L50
 *
 * @param testRunType
 */
@RunWith(Parameterized.class)
public abstract class BaseModelTest {
  public enum RUN_TYPE {
    PATTERN_DSL(true, false),
    STANDARD_FROM_DRL(false, false),
    STANDARD_WITH_ALPHA_NETWORK(false, true),
    PATTERN_WITH_ALPHA_NETWORK(true, true);

    private final boolean executableModel;
    private final boolean alphaNetworkCompiler;

    RUN_TYPE(final boolean executableModel, final boolean isAlphaNetworkCompiler) {
      this.executableModel = executableModel;
      this.alphaNetworkCompiler = isAlphaNetworkCompiler;
    }

    public boolean isAlphaNetworkCompiler() {
      return this.alphaNetworkCompiler;
    }

    public boolean isExecutableModel() {
      return this.executableModel;
    }
  }

  final static Object[] PLAIN = { RUN_TYPE.STANDARD_FROM_DRL, };

  final static Object[] WITH_ALPHA_NETWORK = { RUN_TYPE.STANDARD_FROM_DRL, RUN_TYPE.PATTERN_DSL, RUN_TYPE.STANDARD_WITH_ALPHA_NETWORK, RUN_TYPE.PATTERN_WITH_ALPHA_NETWORK, };

  @Parameters(name = "{0}")
  public static Object[] params() {
    if (Boolean.valueOf(System.getProperty("alphanetworkCompilerEnabled"))) {
      return BaseModelTest.WITH_ALPHA_NETWORK;
    } else {
      return BaseModelTest.PLAIN;
    }
  }

  protected final RUN_TYPE testRunType;


  public BaseModelTest(final RUN_TYPE testRunType) {
    this.testRunType = testRunType;
  }

  protected KieBuilder createKieBuilder(final KieServices ks, final KieModuleModel model, final EventProcessingOption eventProcessingMode, final ReleaseId releaseId,
                                        final KieFile... stringRules) {
    return this.createKieBuilder(ks, model, eventProcessingMode, releaseId, true, stringRules);
  }

  protected KieBuilder createKieBuilder(final KieServices ks, final KieModuleModel model, final EventProcessingOption eventProcessingMode, final ReleaseId releaseId,
                                        final boolean failIfBuildError, final KieFile... stringRules) {
    ks.getRepository().removeKieModule(releaseId);

    final KieFileSystem kfs = ks.newKieFileSystem();
    if (model != null) {
      kfs.writeKModuleXML(model.toXML());
    } else {
      final KieModuleModel kieModuleModel = ks.newKieModuleModel();

      final KieBaseModel kieBaseModel =
          kieModuleModel.newKieBaseModel("KBase").setDefault(true).setEqualsBehavior(EqualityBehaviorOption.IDENTITY).setEventProcessingMode(eventProcessingMode);

      if (eventProcessingMode.equals(EventProcessingOption.STREAM)) {
        kieBaseModel.newKieSessionModel("KSession").setDefault(true).setType(KieSessionModel.KieSessionType.STATEFUL).setClockType(ClockTypeOption.get("pseudo"));
      } else {
        kieBaseModel.newKieSessionModel("KSession").setDefault(true);
      }
      kfs.writeKModuleXML(kieModuleModel.toXML());
    }

    kfs.writePomXML(KJARUtils.getPom(releaseId));
    for (int i = 0; i < stringRules.length; i++) {
      kfs.write(stringRules[i].path, stringRules[i].content);
    }

    KieBuilder kieBuilder;
    kieBuilder = ks.newKieBuilder(kfs).buildAll(DrlProject.class);

    if (failIfBuildError) {
      final List<Message> messages = kieBuilder.getResults().getMessages();
      if (!messages.isEmpty()) {
        Assert.fail(messages.toString());
      }
    }

    return kieBuilder;
  }

  public static <T> List<T> getObjectsIntoList(final KieSession ksession, final Class<T> clazz) {
    return (List<T>) ksession.getObjects(new ClassObjectFilter(clazz)).stream().collect(Collectors.toList());
  }

  protected void createAndDeployJar(final KieServices ks, final ReleaseId releaseId, final KieFile... ruleFiles) {
    this.createAndDeployJar(ks, null, null, releaseId, ruleFiles);
  }

  protected void createAndDeployJar(final KieServices ks, final KieModuleModel model, final ReleaseId releaseId, final String... drls) {
    this.createAndDeployJar(ks, model, null, releaseId, this.toKieFiles(drls));
  }

  protected void createAndDeployJar(final KieServices ks, final EventProcessingOption eventProcessingMode, final ReleaseId releaseId,
                                    final String... drls) {
    this.createAndDeployJar(ks, null, eventProcessingMode, releaseId, this.toKieFiles(drls));
  }

  protected void createAndDeployJar(final KieServices ks, final KieModuleModel model, final EventProcessingOption eventProcessingMode, final ReleaseId releaseId,
                                    final KieFile... ruleFiles) {

    final KieBuilder kieBuilder = this.createKieBuilder(ks, model, eventProcessingMode, releaseId, ruleFiles);
    final InternalKieModule kieModule = (InternalKieModule) kieBuilder.getKieModule();
    ks.getRepository().addKieModule(kieModule);
  }

  public static class KieFile {

    public final String path;
    public final String content;

    public KieFile(final int index, final String content) {
      this(String.format("src/main/resources/r%d.drl", index), content);
    }

    public KieFile(final String path, final String content) {
      this.path = path;
      this.content = content;
    }
  }

  public KieFile[] toKieFiles(final String[] stringRules) {
    final KieFile[] kieFiles = new KieFile[stringRules.length];
    for (int i = 0; i < stringRules.length; i++) {
        kieFiles[i] = new KieFile(i, stringRules[i]);
    }
    return kieFiles;
  }

  protected ObjectTypeNode getObjectTypeNodeForClass(final KieSession ksession, final Class<?> clazz) {
    final EntryPointNode epn = ((InternalKnowledgeBase) ksession.getKieBase()).getRete().getEntryPointNodes().values().iterator().next();
    for (final ObjectTypeNode otn : epn.getObjectTypeNodes().values()) {
      if (otn.getObjectType().isAssignableFrom(clazz)) { return otn; }
    }
    return null;
  }
}