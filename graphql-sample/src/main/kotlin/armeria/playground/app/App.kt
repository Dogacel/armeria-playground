package armeria.playground.app

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
import java.util.concurrent.CompletionStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry

suspend fun main() {
    val server =
        Server.builder()
            .http(8080)
            .serviceUnder("/docs", DocService())
            .serviceUnder("/graphql", PrimaryGraphqlService.new())
            .build()

    server.start().join()
}

data class User(
    val id: String,
    val name: String,
)

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


class UserDataFetcher : DataFetcher<CompletionStage<User?>> {
    override fun get(environment: DataFetchingEnvironment): CompletionStage<User?> {
        val id: String = environment.getArgument("id")

        val userDataLoader = environment.getDataLoader<String, User>("user")

        return userDataLoader.load(id)
    }
}


class GroupDataFetcher : DataFetcher<CompletionStage<Group?>> {
    override fun get(environment: DataFetchingEnvironment): CompletionStage<Group?> {
        val id: String = environment.getArgument("id")

        val groupDataLoader = environment.getDataLoader<String, Group>("group")

        return groupDataLoader.load(id)
    }
}

class GroupUserDataFetcher : DataFetcher<CompletionStage<List<User>>> {
    override fun get(environment: DataFetchingEnvironment): CompletionStage<List<User>> {
        val group: Group = environment.getSource()

        val userDataLoader = environment.getDataLoader<String, User>("user")

        return userDataLoader.loadMany(group.userIds)
    }
}

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
