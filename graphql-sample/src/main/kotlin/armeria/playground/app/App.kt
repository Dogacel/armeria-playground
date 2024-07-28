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
        val schema = javaClass.getResource("/schema.graphqls")?.readText()!!

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

class UserDataFetcher : DataFetcher<User?> {
    override fun get(environment: DataFetchingEnvironment): User? {
        val id: String = environment.getArgument("id")
        return users[id]
    }
}

val groups: Map<String, Group> =
    mapOf(
        "1" to Group("1", "admin", listOf("1", "2")),
        "2" to Group("2", "user", listOf("3")),
    )

class GroupDataFetcher : DataFetcher<Group?> {
    override fun get(environment: DataFetchingEnvironment): Group? {
        val id: String = environment.getArgument("id")
        return groups[id]
    }
}

class GroupUserDataFetcher : DataFetcher<List<User>> {
    override fun get(environment: DataFetchingEnvironment): List<User> {
        val group: Group = environment.getSource()
        return group.userIds.mapNotNull { users[it] }
    }
}
