package io.smallrye.reactive.messaging.mqtt;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import io.smallrye.reactive.messaging.spi.ConfigurationHelper;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.mqtt.MqttClient;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqttSource {

  private final PublisherBuilder<MqttMessage> source;
  private AtomicBoolean subscribed = new AtomicBoolean();

  public MqttSource(Vertx vertx, Config config) {
    ConfigurationHelper conf = ConfigurationHelper.create(config);
    MqttClientOptions options = new MqttClientOptions();
    options.setClientId(conf.get("client-id"));
    options.setAutoGeneratedClientId(conf.getAsBoolean("auto-generated-client-id", false));
    options.setAutoKeepAlive(conf.getAsBoolean("auto-keep-alive", true));
    options.setSsl(conf.getAsBoolean("ssl", false));
    options.setWillQoS(conf.getAsInteger("will-qos", 0));
    options.setKeepAliveTimeSeconds(conf.getAsInteger("keep-alive-seconds", 30));
    options.setMaxInflightQueue(conf.getAsInteger("max-inflight-queue", 10));
    options.setCleanSession(conf.getAsBoolean("auto-clean-session", true));
    options.setWillFlag(conf.getAsBoolean("will-flag", false));
    options.setWillRetain(conf.getAsBoolean("will-retain", false));
    options.setMaxMessageSize(conf.getAsInteger("max-message-size", -1));
    options.setReconnectAttempts(conf.getAsInteger("reconnect-attempts", 5));
    options.setReconnectInterval(TimeUnit.SECONDS.toMillis(conf.getAsInteger("reconnect-interval-seconds", 1)));
    options.setUsername(conf.get("username"));
    options.setPassword(conf.get("password"));
    options.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(conf.getAsInteger("connect-timeout-seconds", 60)));
    options.setTrustAll(conf.getAsBoolean("trust-all", false));

    String host = conf.getOrDie("host");
    int port = conf.getAsInteger("port", options.isSsl() ? 8883 : 1883);
    String server = conf.get("server-name");
    String topic = conf.getOrDie("topic");
    MqttClient client = MqttClient.create(vertx, options);
    int qos = conf.getAsInteger("qos", 0);
    boolean broadcast = conf.getAsBoolean("broadcast", false);

    this.source =
      ReactiveStreams.fromPublisher(
        client.rxConnect(port, host, server)
          .flatMapObservable(a ->
            Observable.<MqttMessage>create(emitter -> {
              client.publishHandler(message -> {
                emitter.onNext(new ReceivingMqttMessage(message));
              });
              client.subscribe(topic, qos, done -> {
                if (done.failed()) {
                  // Report on the flow
                  emitter.onError(done.cause());
                }
                subscribed.set(done.succeeded());
              });
            }))
          .toFlowable(BackpressureStrategy.BUFFER)
          .compose(f -> {
            if (broadcast) {
              return f.publish().autoConnect();
            } else {
              return f;
            }
          })
          .doOnCancel(() -> {
            subscribed.set(false);
            client.disconnect();
          })
      );
  }

  PublisherBuilder<MqttMessage> getSource() {
    return source;
  }

  public boolean isSubscribed() {
    return subscribed.get();
  }
}
