# postgure

Clojure ORM for PostgreSQL.

## Motivation

This project was developed to leverage Clojure's built-ins on the data manipulation of PostgreSQL relations. It got inspired by .NET's Entity Framework Core and its way of querying data using Language Integrated Query (LINQ). Postgure is basic and does not require any SQL knowledge.

This project is a good fit for you if:
- You target PostgreSQL databases
- You want to use Clojure's built-ins such as `->>`, `filter` and `clojure.set/join`
- You are able to provide `javax.sql.DataSource` instances to the ORM

This project is NOT a good fit for you if:
- You want to execute raw SQL. For this please refer to [next.jdbc](https://github.com/seancorfield/next-jdbc).
- You want to use Clojure's syntax to generate SQL strings. For this please refer to [Honey SQL](https://github.com/seancorfield/honeysql).

## Usage examples in a Leiningen app project

### project.clj

```
  :dependencies [[com.github.alexisc183/postgure "1.0.0"] ; The ORM.
                 [com.zaxxer/HikariCP "6.3.0"] ; Third-party lib to create data sources.
                 [org.clojure/clojure "1.12.1"]
                 [org.postgresql/postgresql "42.7.7"] ; Required JDBC driver for the third-party lib.
                 ]
```

### Sample data

#### person

| id | first_name | last_name | age |
|----|------------|-----------|-----|
| 1  | Jeanne     | Safi      | 26  |
| 2  | Ivan       | Boor      | 36  |
| 3  | Monica     | Murphy    | 33  |
| 4  | Alexander  | Wright    | 48  |

#### employee

| id | department | salary   |
|----|------------|----------|
| 1  | CEO        | 40000.00 |
| 3  | Management | 37010.66 |
| 4  | CIO        | 37500.50 |

### core.clj

```
(ns postgure-usage.core
  (:require [com.github.alexisc183.postgure.core :as pg])
  (:import [com.github.alexisc183.postgure DataContext]
           [com.zaxxer.hikari HikariDataSource])
  (:gen-class))
```

#### Simple program to print all the rows of the person table

```
(defn -main
  [& _]
  (let [ds (doto (HikariDataSource.)
             (.setJdbcUrl "jdbc:postgresql://localhost:5432/company")
             (.setUsername "my_user")
             (.setPassword "my_pass"))]
    (with-open [ctx (DataContext. ds)]
      (doseq [p (pg/from ctx "public" "person")]
        (println p)))))
```

Output:

```
{:id 1, :first_name Jeanne, :last_name Safi, :age 26}
{:id 2, :first_name Ivan, :last_name Boor, :age 36}
{:id 3, :first_name Monica, :last_name Murphy, :age 33}
{:id 4, :first_name Alexander, :last_name Wright, :age 48}
```

#### Simple program to print people of age 35 or older

```
(defn -main
  [& _]
  (let [ds (doto (HikariDataSource.)
             (.setJdbcUrl "jdbc:postgresql://localhost:5432/company")
             (.setUsername "my_user")
             (.setPassword "my_pass"))]
    (with-open [ctx (DataContext. ds)]
      (doseq [p (->> (pg/from ctx "public" "person")
                     (filter #(>= (:age %) 35)))]
        (println p)))))
```

Output:

```
{:id 2, :first_name Ivan, :last_name Boor, :age 36}
{:id 4, :first_name Alexander, :last_name Wright, :age 48}
```

#### Simple program that inserts a new person

```
(defn -main
  [& _]
  (let [ds (doto (HikariDataSource.)
             (.setJdbcUrl "jdbc:postgresql://localhost:5432/company")
             (.setUsername "my_user")
             (.setPassword "my_pass"))]
    (with-open [ctx (DataContext. ds)]
      (pg/insert-into ctx "public" "person" [{:first_name "Jim"
                                              :last_name "Jimenez"
                                              :age 30}]))))
```

Notice that an ID was not provided; serial columns are automatically inserted. Explicit serial values will be ignored.

#### Simple program to delete two people: one with ID 2 and another with ID 5

```
(defn -main
  [& _]
  (let [ds (doto (HikariDataSource.)
             (.setJdbcUrl "jdbc:postgresql://localhost:5432/company")
             (.setUsername "my_user")
             (.setPassword "my_pass"))]
    (with-open [ctx (DataContext. ds)]
      (pg/delete-from ctx "public" "person" [{:id 2}
                                             {:id 5}]))))
```

At database level, it is mandatory for the target table to have a primary key constraint. Specifying non-primary key columns in the maps is optional.

#### Simple program to update the employee with ID 3

```
(defn -main
  [& _]
  (let [ds (doto (HikariDataSource.)
             (.setJdbcUrl "jdbc:postgresql://localhost:5432/company")
             (.setUsername "my_user")
             (.setPassword "my_pass"))]
    (with-open [ctx (DataContext. ds)]
      (pg/do-update ctx "public" "employee" [{:id 3
                                              :department "Human Resources"
                                              :salary 37010.66M}]))))
```

At database level, a primary key constraint and at least one non-primary key column are mandatory for the target table. Missing keywords in the maps translate to NULL values to the corresponding columns.

#### Program to increment by 1000 the employee salaries

```
(defn -main
  [& _]
  (let [ds (doto (HikariDataSource.)
             (.setJdbcUrl "jdbc:postgresql://localhost:5432/company")
             (.setUsername "my_user")
             (.setPassword "my_pass"))]
    (with-open [ctx (DataContext. ds)]
      (->> (pg/from ctx "public" "employee")
           (map #(assoc % :salary (+ (:salary %) 1000)))
           (pg/do-update ctx "public" "employee")))))
```

#### Program to print all the people with their employee information

```
(defn -main
  [& _]
  (let [ds (doto (HikariDataSource.)
             (.setJdbcUrl "jdbc:postgresql://localhost:5432/company")
             (.setUsername "my_user")
             (.setPassword "my_pass"))]
    (with-open [ctx (DataContext. ds)]
      (let [people (pg/from ctx "public" "person")
            employees (pg/from ctx "public" "employee")]
        (doseq [pe (clojure.set/join people employees {:id :id})]
          (println pe))))))
```

Output:

```
{:id 3, :first_name Monica, :last_name Murphy, :age 33, :department Human Resources, :salary 38010.66M}
{:id 1, :first_name Jeanne, :last_name Safi, :age 26, :department CEO, :salary 41000.00M}
{:id 4, :first_name Alexander, :last_name Wright, :age 48, :department CIO, :salary 38500.50M}
```

## License

Copyright © 2025 C183

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
