package com.github.alexisc183.postgure;

import clojure.core$range;
import clojure.core$vector;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Data source wrapper used to create and manage JDBC auto-closeable objects.
 * <p>
 * A <code>DataContext</code> is required in order to make the Clojure ORM for PostgreSQL work; invoking the sole constructor of this class and passing the instance around is enough, specially if instantiated in a <code>with-open</code> macro like this, which is the preferred way:
 * <pre>
 * (with-open [ctx (DataContext. source)]
 *   (pg/insert-into ctx "public" "person" []))
 * </pre>
 * Passing a <code>null</code> argument to a constructor or method in this class will cause a {@link java.lang.NullPointerException} to be thrown. 
 * 
 * @author AlexisC183
 * @version 1, 2025-07-02
 * @since postgure 1.0.0
 */
public class DataContext implements AutoCloseable {
	private final DataSource source;
	private Connection connection;
	private Statement statement;
	private final ArrayList<ResultSet> resultSets = new ArrayList<>();
	private final HashMap<SchemaTableEntry, Object> seeds = new HashMap<>();
	private final ArrayList<PreparedStatement> preparedStatements = new ArrayList<>();

	/**
	 * Creates a data context with the provided data source.
	 * 
	 * @param source the data source for the database connectivity
	 */
	public DataContext(DataSource source) {
		this.source = Objects.requireNonNull(source);
	}

	private void ensureConnection() throws SQLException {
		if (connection == null) {
			connection = source.getConnection();
		}
	}

	private void ensureStatement() throws SQLException {
		if (statement == null) {
			statement = connection.createStatement();
		}
	}

	/**
	 * Returns a result set of column metadata and a result set of primary key metadata from the specified schema and table, in a Clojure vector.
	 * <p>
	 * Note: This API is intended for internal use.
	 * 
	 * @param schema the target schema
	 * @param table the target table
	 * @return a Clojure vector with the result sets
	 * @throws SQLException if a database error occurs
	 */
	public Object columnsPKeysMetadata(String schema, String table) throws SQLException {
		Objects.requireNonNull(schema);
		Objects.requireNonNull(table);

		ensureConnection();

		DatabaseMetaData metadata = connection.getMetaData();
		String catalog = connection.getCatalog();
		ResultSet columns = metadata.getColumns(catalog, schema, table, "%");
		ResultSet primaryKeys = metadata.getPrimaryKeys(catalog, schema, table);

		resultSets.add(columns);
		resultSets.add(primaryKeys);

		return core$vector.invokeStatic(columns, primaryKeys);
	}

	/**
	 * Returns a Clojure vector of <code>ResultSet</code>, <code>ResultSetMetaData</code> and <code>core$range</code> from the specified schema and table.
	 * <p>
	 * Note: This API is intended for internal use.
	 * 
	 * @param schema the target schema
	 * @param table the target table
	 * @return a Clojure vector used as a seed to retrieve all the rows of a table
	 * @throws SQLException if a database error occurs
	 */
	public Object singletonSeed(String schema, String table) throws SQLException {
		Objects.requireNonNull(schema);
		Objects.requireNonNull(table);

		SchemaTableEntry entry = new SchemaTableEntry(schema, table);

		if (seeds.containsKey(entry)) {
			return seeds.get(entry);
		}

		ensureConnection();
		ensureStatement();

		String sql = "select * from " + statement.enquoteIdentifier(schema, false) + "." + statement.enquoteIdentifier(table, false);
		ResultSet resultSet = statement.executeQuery(sql);

		resultSets.add(resultSet);

		ResultSetMetaData metadata = resultSet.getMetaData();
		Object columnOrdinals = core$range.invokeStatic(1, metadata.getColumnCount() + 1);
		Object seed = core$vector.invokeStatic(resultSet, metadata, columnOrdinals);

		seeds.put(entry, seed);

		return seed;
	}

	/**
	 * Returns a unique statement.
	 * <p>
	 * Note: This API is intended for internal use.
	 * 
	 * @return the statement
	 * @throws SQLException if a database error occurs
	 */
	public Statement singletonStatement() throws SQLException {
		ensureConnection();
		ensureStatement();

		return statement;
	}

	/**
	 * Creates a new prepared statement.
	 * <p>
	 * Note: This API is intended for internal use.
	 * 
	 * @param sql a SQL statement string that may contain ? symbols as placeholders in which to set values later
	 * @return a prepared statement
	 * @throws SQLException if a database error occurs
	 */
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		Objects.requireNonNull(sql);

		ensureConnection();

		PreparedStatement preparedStatement = connection.prepareStatement(sql);

		preparedStatements.add(preparedStatement);

		return preparedStatement;
	}

	/**
	 * Closes all the auto-closeable objects produced by this instance.
	 * 
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public void close() throws SQLException {
		for (ResultSet rs : resultSets) {
			rs.close();
		}
		for (PreparedStatement ps : preparedStatements) {
			ps.close();
		}
		if (statement != null) {
			statement.close();
		}
		if (connection != null) {
			connection.close();
		}
	}
}
