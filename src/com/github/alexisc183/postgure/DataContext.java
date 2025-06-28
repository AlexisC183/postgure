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

public class DataContext implements AutoCloseable {
	private final DataSource source;
	private Connection connection;
	private Statement statement;
	private final ArrayList<ResultSet> resultSets = new ArrayList<>();
	private final HashMap<SchemaTableEntry, Object> seeds = new HashMap<>();
	private final ArrayList<PreparedStatement> preparedStatements = new ArrayList<>();

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

	public Object columnsPKeysMetadata(String schema, String table) throws SQLException {
		ensureConnection();

		DatabaseMetaData metadata = connection.getMetaData();
		String catalog = connection.getCatalog();
		ResultSet columns = metadata.getColumns(catalog, schema, table, "%");
		ResultSet primaryKeys = metadata.getPrimaryKeys(catalog, schema, table);

		resultSets.add(columns);
		resultSets.add(primaryKeys);

		return core$vector.invokeStatic(columns, primaryKeys);
	}

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

	public Statement singletonStatement() throws SQLException {
		ensureConnection();
		ensureStatement();

		return statement;
	}

	public PreparedStatement prepareStatement(String sql) throws SQLException {
		Objects.requireNonNull(sql);

		ensureConnection();

		PreparedStatement preparedStatement = connection.prepareStatement(sql);

		preparedStatements.add(preparedStatement);

		return preparedStatement;
	}

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
