package armeria.playground.app

import ApplicationConfig
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.graphql.GraphqlService
import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import io.github.classgraph.ClassGraph
import java.util.concurrent.CompletionStage
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.kotlin.forKotlin
import org.pkl.config.kotlin.to
import org.pkl.core.ModuleSource

suspend fun main() {
    val config = ConfigEvaluator
        .preconfigured()
        .forKotlin()
        .use { evaluator ->
            evaluator.evaluate(ModuleSource.modulePath("application_config_local.pkl"))
        }
        .to<ApplicationConfig>()


    val server =
        Server.builder()
            .http(config.server.port)
            .apply {
                if (config.server.enableDocService) {
                    serviceUnder("/docs", DocService())
                }
            }
            .apply {
                if (config.server.enableGraphQL) {
                    serviceUnder("/graphql", PrimaryGraphqlService.new())
                }
            }
            .build()

    server.start().join()
}


@Target(AnnotationTarget.CLASS)
annotation class WireDataFetcher(val typeName: String, val fieldName: String)

@Target(AnnotationTarget.CLASS)
annotation class WireDataLoader(val loadType: KClass<*>)

@Target(AnnotationTarget.CLASS)
annotation class HasDataLoader(val name: String = "")

@HasDataLoader
data class User(
    val id: String,
    val name: String,
)

@HasDataLoader
data class Group(
    val id: String,
    val name: String,
    val userIds: List<String> = listOf(),
)

object PrimaryGraphqlService {
    fun new(): GraphqlService {
        val schema =
            javaClass.getResource("/schema.graphqls")?.readText() ?: error("resources/schema.graphqls not found")

        // Parse schema
        val schemaParser = SchemaParser()
        val typeRegistry = schemaParser.parse(schema)

        // Classpath scanning
        // Perform the scan
        val scanResult = ClassGraph()
            .enableAllInfo() // Enable scanning for all class information
            .acceptPackages("armeria.playground")
            .scan()

        // Get all class names from the scan result
        val classNames = scanResult.allClasses.names

        // Load the classes and filter those with the WireDataFetcher annotation
        val annotatedClasses = classNames.mapNotNull { className ->
            try {
                val kClass = Class.forName(className).kotlin
                if (kClass.hasAnnotation<WireDataFetcher>()) kClass else null
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoClassDefFoundError) {
                null
            }
        }

        // Print the names of the annotated classes
        val types = annotatedClasses.associate { kClass ->
            val annotation = kClass.findAnnotation<WireDataFetcher>()
            annotation?.typeName to annotation?.fieldName
        }

        // Define runtime wiring
        val runtimeWiring: RuntimeWiring =
            RuntimeWiring
                .newRuntimeWiring()
                .type(
                    TypeRuntimeWiring.newTypeWiring("Query")
                        .dataFetcher("user", UserDataFetcher())
                        .dataFetcher("group", GroupDataFetcher()),
                )
                .type(
                    TypeRuntimeWiring.newTypeWiring("Group")
                        .dataFetcher("users", GroupUserDataFetcher()),
                )
                .build()

        val dataLoaderRegistry =
            DataLoaderRegistry
                .newRegistry()
                .register("user", UserBatchLoader().asDataLoader())
                .register("group", GroupBatchLoader().asDataLoader())
                .build()

        // Generate schema
        val schemaGenerator = SchemaGenerator()
        val graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring)
        val graphql = GraphQL.newGraphQL(graphQLSchema).build()

        return GraphqlService
            .builder()
            .graphql(graphql)
            .dataLoaderRegistry { _ -> dataLoaderRegistry }
            .build()
    }
}

val users: Map<String, User> =
    mapOf(
        "1" to User("1", "hero"),
        "2" to User("2", "human"),
        "3" to User("3", "droid"),
    )
val groups: Map<String, Group> =
    mapOf(
        "1" to Group("1", "admin", listOf("1", "2")),
        "2" to Group("2", "user", listOf("3")),
    )

private inline fun <K, reified V : Any> DataFetchingEnvironment.getDataLoader(): DataLoader<K, V> {
    return this.getDataLoader<K, V>(V::class.qualifiedName)
}

@WireDataFetcher(typeName = "Query", fieldName = "user")
class UserDataFetcher : DataFetcher<CompletionStage<User?>> {
    override fun get(environment: DataFetchingEnvironment): CompletionStage<User?> {
        val id: String = environment.getArgument("id")

        val userDataLoader = environment.getDataLoader<String, User>()

        return userDataLoader.load(id)
    }
}

@WireDataFetcher(typeName = "Query", fieldName = "group")
class GroupDataFetcher : DataFetcher<CompletionStage<Group?>> {
    override fun get(environment: DataFetchingEnvironment): CompletionStage<Group?> {
        val id: String = environment.getArgument("id")

        val groupDataLoader = environment.getDataLoader<String, Group>()

        return groupDataLoader.load(id)
    }
}

@WireDataFetcher(typeName = "Group", fieldName = "users")
class GroupUserDataFetcher : DataFetcher<CompletionStage<List<User>>> {
    override fun get(environment: DataFetchingEnvironment): CompletionStage<List<User>> {
        val group: Group = environment.getSource()

        val userDataLoader = environment.getDataLoader<String, User>()

        return userDataLoader.loadMany(group.userIds)
    }
}

@WireDataLoader(User::class)
class UserBatchLoader : BatchLoader<String, User> {
    override fun load(keys: List<String>): CompletionStage<List<User>> {
        return CoroutineScope(Dispatchers.IO).future {
            delay(250)
            keys.mapNotNull { users[it] }
        }
    }

    fun asDataLoader(): DataLoader<String, User> {
        return DataLoaderFactory.newDataLoader(this)
    }
}

@WireDataLoader(Group::class)
class GroupBatchLoader : BatchLoader<String, Group> {
    override fun load(keys: List<String>): CompletionStage<List<Group>> {
        return CoroutineScope(Dispatchers.IO).future {
            delay(250)
            keys.mapNotNull { groups[it] }
        }
    }

    fun asDataLoader(): DataLoader<String, Group> {
        return DataLoaderFactory.newDataLoader(this)
    }
}
