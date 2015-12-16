package com.netflix.eureka2.testkit.embedded.server;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.EurekaWriteServerConfigurationModule;
import com.netflix.eureka2.server.EurekaWriteServerModule;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.WriteTransportServer;
import com.netflix.eureka2.server.transport.tcp.interest.TcpInterestServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.governator.DefaultGovernatorConfiguration;
import com.netflix.governator.DefaultGovernatorConfiguration.Builder;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;
import rx.Observable;

import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteServerBuilder extends EmbeddedServerBuilder<WriteServerConfig, EmbeddedWriteServerBuilder> {

    private Observable<ChangeNotification<Server>> replicationPeers;

    public EmbeddedWriteServerBuilder withReplicationPeers(Observable<ChangeNotification<Server>> replicationPeers) {
        this.replicationPeers = replicationPeers;
        return this;
    }

    public EmbeddedWriteServer build() {
        List<Module> coreModules = new ArrayList<>();

        if (configuration == null) {
            coreModules.add(EurekaWriteServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX));
        } else {
            coreModules.add(EurekaWriteServerConfigurationModule.fromConfig(configuration));
        }
        coreModules.add(new CommonEurekaServerModule());
        coreModules.add(new EurekaWriteServerModule());

        if (adminUI) {
            coreModules.add(new EmbeddedKaryonAdminModule(configuration.getEurekaTransport().getWebAdminPort()));
        }

        List<Module> overrides = new ArrayList<>();
        overrides.add(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                        bind(AbstractEurekaServer.class).to(EmbeddedWriteServer.class);
                    }
                }
        );
        if (networkRouter != null) {
            overrides.add(new NetworkRouterModule(networkRouter));
        }

        Module applicationModules = combineWithExtensionModules(Modules.combine(coreModules));
        applicationModules = combineWithConfigurationOverrides(applicationModules, overrides);

        Builder<?> configurationBuilder = DefaultGovernatorConfiguration.builder().addProfile(ServerType.Write.name());
        if (ext) {
            configurationBuilder.addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class));
        }
        LifecycleInjector injector = Governator.createInjector(
                configurationBuilder.build(),
                applicationModules
        );
        return injector.getInstance(EmbeddedWriteServer.class);
    }

    static class NetworkRouterModule extends AbstractModule {

        private final NetworkRouter networkRouter;

        NetworkRouterModule(NetworkRouter networkRouter) {
            this.networkRouter = networkRouter;
        }

        @Override
        protected void configure() {
            bind(NetworkRouter.class).toInstance(networkRouter);
            bind(WriteTransportServer.class).to(EmbeddedWriteTransportServer.class).in(Scopes.SINGLETON);
//            bind(TcpRegistrationServer.class).to(EmbeddedTcpRegistrationServer.class).in(Scopes.SINGLETON);
//            bind(TcpReplicationServer.class).to(EmbeddedTcpReplicationServer.class).in(Scopes.SINGLETON);
//            bind(TcpInterestServer.class).to(EmbeddedTcpInterestServer.class).in(Scopes.SINGLETON);
        }
    }
}
