package io.github.martinwitt.configreloader.aot;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

public class KubernetesClientRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerReflectionHints(hints, classLoader);
        registerResourceHints(hints);
        registerSerializationHints(hints, classLoader);
        registerProxyHints(hints, classLoader);
    }

    private void registerReflectionHints(RuntimeHints hints, ClassLoader cl) {
        String[] classes =
                new String[] {
                    "io.fabric8.kubernetes.client.impl.KubernetesClientImpl",
                    "io.fabric8.kubernetes.client.KubernetesClientBuilder",
                    "io.fabric8.kubernetes.client.dsl.internal.BaseOperationContext",
                    "io.fabric8.kubernetes.client.dsl.internal.WatchConnectionManager",
                    "io.fabric8.kubernetes.client.informers.cache.DefaultSharedIndexInformer",
                    "io.fabric8.kubernetes.client.impl.KubernetesClientImpl$APIGroupsOperationsImpl",
                    "io.fabric8.kubernetes.api.model.apps.Deployment",
                    "io.fabric8.kubernetes.api.model.apps.StatefulSet",
                    "io.fabric8.kubernetes.api.model.Secret",
                    "io.fabric8.kubernetes.api.model.ConfigMap",
                    "io.fabric8.kubernetes.api.model.ObjectMeta",
                    "io.fabric8.kubernetes.api.model.apps.DeploymentSpec",
                    "io.fabric8.kubernetes.api.model.apps.StatefulSetSpec",
                    "io.fabric8.kubernetes.api.model.PodTemplateSpec",
                    "io.fabric8.kubernetes.api.model.PodSpec",
                    "io.fabric8.kubernetes.api.model.Container",
                    "io.fabric8.kubernetes.api.model.EnvFromSource",
                    "io.fabric8.kubernetes.api.model.EnvVarSource",
                    "io.fabric8.kubernetes.api.model.SecretKeySelector",
                    "io.fabric8.kubernetes.api.model.ConfigMapKeySelector",
                    "io.fabric8.kubernetes.client.Config",
                    "io.fabric8.kubernetes.client.ConfigBuilder"
                };

        for (String className : classes) {
            try {
                Class<?> c = Class.forName(className, false, cl);
                hints.reflection()
                        .registerType(
                                c,
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS);
            } catch (Throwable ex) {
                // ignore if class not present
            }
        }

        // reachability-metadata.json: register specific constructor for KubernetesClientImpl
        try {
            Class<?> clientImpl =
                    Class.forName(
                            "io.fabric8.kubernetes.client.impl.KubernetesClientImpl", false, cl);
            // Register declared constructors (covers public and non-public constructors) so Graal
            // native image
            // allows reflective construction of this implementation during runtime.
            hints.reflection()
                    .registerType(
                            clientImpl,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);
        } catch (Throwable ex) {
            // ignore
        }
    }

    private void registerResourceHints(RuntimeHints hints) {
        hints.resources().registerPattern(".*\\.properties$");
        hints.resources().registerPattern(".*\\.yaml$");
        hints.resources().registerPattern(".*\\.yml$");
        hints.resources().registerPattern(".*\\.txt$");
    }

    private void registerSerializationHints(RuntimeHints hints, ClassLoader cl) {
        String[] classes =
                new String[] {
                    "io.fabric8.kubernetes.api.model.apps.Deployment",
                    "io.fabric8.kubernetes.api.model.apps.DeploymentList",
                    "io.fabric8.kubernetes.api.model.apps.StatefulSet",
                    "io.fabric8.kubernetes.api.model.apps.StatefulSetList",
                    "io.fabric8.kubernetes.api.model.Secret",
                    "io.fabric8.kubernetes.api.model.SecretList",
                    "io.fabric8.kubernetes.api.model.ConfigMap",
                    "io.fabric8.kubernetes.api.model.ConfigMapList",
                    "io.fabric8.kubernetes.api.model.ObjectMeta",
                    "io.fabric8.kubernetes.api.model.apps.DeploymentSpec",
                    "io.fabric8.kubernetes.api.model.apps.StatefulSetSpec",
                    "io.fabric8.kubernetes.api.model.PodTemplateSpec",
                    "io.fabric8.kubernetes.api.model.PodSpec",
                    "io.fabric8.kubernetes.api.model.Container",
                    "io.fabric8.kubernetes.api.model.EnvFromSource",
                    "io.fabric8.kubernetes.api.model.EnvVarSource",
                    "io.fabric8.kubernetes.api.model.SecretKeySelector",
                    "io.fabric8.kubernetes.api.model.ConfigMapKeySelector"
                };

        for (String className : classes) {
            try {
                Class<?> c = Class.forName(className, false, cl);
                hints.serialization().registerType(TypeReference.of(c));
            } catch (Throwable ex) {
                // ignore
            }
        }
    }

    private void registerProxyHints(RuntimeHints hints, ClassLoader cl) {
        try {
            Class<?> watchable =
                    Class.forName("io.fabric8.kubernetes.client.dsl.Watchable", false, cl);
            Class<?> autoClose = Class.forName("java.lang.AutoCloseable", false, cl);
            hints.proxies().registerJdkProxy(watchable, autoClose);
        } catch (Throwable ex) {
            // ignore
        }

        try {
            Class<?> nameable =
                    Class.forName("io.fabric8.kubernetes.client.dsl.Nameable", false, cl);
            Class<?> serializable = Class.forName("java.io.Serializable", false, cl);
            hints.proxies().registerJdkProxy(nameable, serializable);
        } catch (Throwable ex) {
            // ignore
        }
    }
}
