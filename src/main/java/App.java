import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;


import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import akka.pattern.Patterns;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class App {

    private static final String LOCAL_HOST = "localhost";
    private static final int PORT = 8080;
    private static final String TEST_URL = "testUrl";
    private static final String COUNT = "count";
    private static final int MAP_ASYNC = 1;
    private static final int TIME_OUT = 5;
    private static final String TIME_RESPONSE = "Responce time = ";
    private static final String AVG_RESPONSE_TIME_PTR = "Average response time = ";

    private static Flow<HttpRequest, HttpResponse, NotUsed> createFlow(Http http, ActorSystem system,
                                                                       ActorMaterializer materializer, ActorRef actor) {
        return Flow
                .of(HttpRequest.class)
                .map(
                        (req) -> {
                            Query query = req.getUri().query();
                            String url = query.get(TEST_URL).get();
                            int count = Integer.parseInt(
                                    query.get(COUNT).get()
                            );
                            System.out.println(url + " - " + count + "");
                            return new Pair<String, Integer>(url, count);
                        }
                )
                .mapAsync(MAP_ASYNC,
                        req -> {
                    CompletionStage<Object> completionStage = Patterns.ask(
                            actor,
                            new Message(req.first()),
                            Duration.ofSeconds(TIME_OUT)
                    );
                    return completionStage.thenCompose(
                            res -> {
                                if ((Integer)res >= 0) {
                                    return CompletableFuture.completedFuture(
                                            new Pair<>(
                                                    req.first(),
                                                    (Integer)res
                                            )
                                    );
                                }
                                Flow<Pair<String, Integer>, Integer, NotUsed> flow = Flow
                                        .<Pair<String, Integer>>create()
                                        .mapConcat(
                                                pair -> new ArrayList<>(
                                                        Collections.nCopies(
                                                                pair.second(),
                                                                pair.first()
                                                        )
                                                )
                                        )
                                        .mapAsync(
                                                req.second(),
                                                url -> {
                                                    long start = System.currentTimeMillis();
                                                    asyncHttpClient().prepareGet(url).execute();
                                                    long end = System.currentTimeMillis();
                                                    System.out.println(TIME_RESPONSE + (int)(end - start) + "\n");
                                                    return CompletableFuture.completedFuture((int)(end - start));
                                                }
                                        );
                                return Source
                                        .single(req)
                                        .via(flow)
                                        .toMat(
                                                Sink.fold(
                                                        0,
                                                        Integer::sum
                                                ),
                                                Keep.right()
                                        )
                                        .run(materializer)
                                        .thenApply(
                                                sum -> new Pair<>(
                                                        req.first(),
                                                        (sum / req.second())
                                                )
                                        );
                            }
                    );
                        })
                .map(
                        req -> {
                            actor.tell(
                                    new StorageMessage(
                                            req.first(),
                                            req.second()
                                    ),
                                    ActorRef.noSender()
                            );
                            System.out.println();
                        }
                )
    }

    public static void main(String[] args) throws IOException {
        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");
        final Http http = Http.get(system);
        final ActorMaterializer materializer =
                ActorMaterializer.create(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow =
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost(LOCAL_HOST, PORT),
                materializer
        );
        System.out.println("Server online at http://" + LOCAL_HOST + ":" + PORT);
        System.in.read();
        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate()); // and shutdown
    }

}



