(ns com.github.alexisc183.postgure.core
  "Defines functions that perform CRUD operations on PostgreSQL relational databases.
   
  COMMON ARGUMENTS
   
  All the functions make use of an instance of `com.github.alexisc183.postgure.DataContext`
  to retrieve all the necessary database metadata and manage auto-closeable objects
  efficiently. A single instance can be reused across function calls. For the API
  documentation of `com.github.alexisc183.postgure.DataContext` please see:
    _
  
  Also all the functions ask for a target table within a database schema, being the latter
  commonly 'public' if no schema was specified on table creation in PostgreSQL.

  All the functions except `from` require a collection as the fourth argument, this to let
  the use of the `->>` macro and perform the same operation over multiple rows at once
  without the need of invoking these functions multiple times.
   
  DATA IS PROCESSED WITH CLOJURE'S BUILT-INS

  This means three things:
  1. Table columns are converted from PostgreSQL types to Java types.
  2. Table rows are mapped to Clojure maps, with keywords as keys that exactly match table
     column names.
  3. Rows are within a Clojure collection that can be operated with functions like `filter`
     and `map`. Data is brought into memory only when it is actually needed.
   
  JAVA TIME API IS PREFERRED OVER LEGACY
  
  PostgreSQL time-related types are converted to their `java.time`-equivalent types
  because these support more operations than the ones that extend `java.util.Date`."
  (:require [clojure.string :as str])
  (:import [com.github.alexisc183.postgure DataContext]
           [java.sql SQLException]
           [java.time LocalDate LocalDateTime LocalTime]))

(defn- enquote
  [ctx s]
  (.enquoteIdentifier (.singletonStatement ctx) s false))

(defn- throw-if-bad-args
  ([ctx schema table]
   (cond
     (not (instance? DataContext ctx)) (throw (ClassCastException. "ctx must be a com.github.alexisc183.postgure.DataContext"))
     (not (instance? String schema)) (throw (ClassCastException. "schema must be a String"))
     (not (instance? String table)) (throw (ClassCastException. "table must be a String"))))
  ([ctx schema table coll]
   (throw-if-bad-args ctx schema table)
   (when (not (coll? coll)) (throw (IllegalArgumentException. "coll must be a collection")))))

(defn delete-from
  "Takes a `com.github.alexisc183.postgure.DataContext` as `ctx` and deletes all the rows
  included in `coll` from the provided `schema` and `table` strings. This is an eager
  operation and returns nil."
  [ctx schema table coll]
  (letfn [(pkey-names
            []
            (let [[_ pk-rs] (.columnsPKeysMetadata ctx schema table)]
              (->> (repeat pk-rs)
                   (take-while #(.next %))
                   (map #(.getString % 4))
                   vec)))
          (gen-sql
            [pk-names row-count pkey-count]
            (let [last-i (dec row-count)
                  in-placeholders (str
                                   "("
                                   (->> (range 0 row-count)
                                        (map #(str "?" (when (< % last-i) ", ")))
                                        (reduce str))
                                   ")")
                  last-j (dec pkey-count)]
              (str
               "delete from "
               (enquote ctx schema)
               "."
               (enquote ctx table)
               " where "
               (->> pk-names
                    (map-indexed #(str
                                   (enquote ctx %2)
                                   " in "
                                   in-placeholders
                                   (when (< %1 last-j) " and ")))
                    (reduce str)))))]
    (throw-if-bad-args ctx schema table coll)
    (when-let [rows (not-empty (vec coll))]
      (if-let [pk-names (not-empty (pkey-names))]
        (let [row-count (count rows)
              pkey-count (count pk-names)
              prepared-statement (.prepareStatement ctx (gen-sql
                                                         pk-names
                                                         row-count
                                                         pkey-count))
              param-count (* row-count pkey-count)
              pkey-keywords (->> pk-names (map keyword) vec)]
          (loop [i 0
                 j 0
                 param-ordinal 1]
            (cond
              (> param-ordinal param-count) (do
                                              (.execute prepared-statement)
                                              nil)
              (= i row-count) (recur 0 (inc j) param-ordinal)
              :else (do
                      (.setObject
                       prepared-statement
                       param-ordinal
                       (-> rows (nth i) ((nth pkey-keywords j))))
                      (recur (inc i) j (inc param-ordinal))))))
        (throw (SQLException. "The provided table needs at least one column as primary key as criterion to delete rows"))))))

(defn from
  "Takes a `com.github.alexisc183.postgure.DataContext` as `ctx` and returns a lazy sequence
  containing the rows from the provided `schema` and `table` strings."
  [ctx schema table]
  (letfn [(get-cell
            [rs meta col-ordinal]
            [(keyword (.getColumnName meta col-ordinal))
             (case (.getColumnTypeName meta col-ordinal)
               "date" (-> rs
                          (.getString col-ordinal)
                          (LocalDate/parse))
               "time" (-> rs
                          (.getString col-ordinal)
                          (LocalTime/parse))
               "timestamp" (-> rs
                               (.getString col-ordinal)
                               (str/replace \space \T)
                               (LocalDateTime/parse))
               (.getObject rs col-ordinal))])
          (get-row
            [lazy-seed]
            (let [[rs meta col-ordinals] (lazy-seed)]
              (->> col-ordinals
                   (map #(get-cell rs meta %)) ; LazySeq of [k v]s.
                   (reduce (fn [m [k v]] (assoc m k v)) {}))))]
    (throw-if-bad-args ctx schema table)
    (->> (repeat #(.singletonSeed ctx schema table)) ; Infinite lazy seq of lazy seed.
         (take-while #(let [[rs _ _] (%)]
                        (.next rs))) ; Finite LazySeq of lazy seed.
         (map get-row))))

(defn insert-into
  "Takes a `com.github.alexisc183.postgure.DataContext` as `ctx` and inserts all the rows
  included in `coll` into the provided `schema` and `table` strings. This is an eager
  operation and returns nil."
  [ctx schema table coll]
  (letfn [(col-names-and-types
            []
            (let [[col-rs _] (.columnsPKeysMetadata ctx schema table)]
              (->> (repeat col-rs)
                   (take-while #(.next %))
                   (map (fn [rs] [(.getString rs 4) (.getString rs 6)]))
                   vec)))
          (gen-insert-statement
            [manual-col-names row-count]
            (let [row-indices (range 0 row-count)
                  last-i (dec row-count)
                  last-j (dec (count manual-col-names))]
              (str
               "insert into "
               (enquote ctx schema)
               "."
               (enquote ctx table)
               " ("
               (->> manual-col-names
                    (map-indexed #(str (enquote ctx %2) (when (< %1 last-j) ", ")))
                    (reduce str))
               ") values "
               (->> row-indices
                    (map (fn [i] (str
                                  "("
                                  (->> manual-col-names
                                       (map-indexed (fn [j _] (str "?" (when (< j last-j) ", "))))
                                       (reduce str))
                                  ")"
                                  (when (< i last-i) ", "))))
                    (reduce str)))))
          (gen-insert-default-statement
            [row-count]
            (let [row-indices (range 0 row-count)
                  last-i (dec row-count)]
              (str
               "insert into "
               (enquote ctx schema)
               "."
               (enquote ctx table)
               " values "
               (->> row-indices
                    (map #(str "(default)" (when (< % last-i) ", ")))
                    (reduce str)))))]
    (throw-if-bad-args ctx schema table coll)
    (when-let [rows (not-empty (vec coll))]
      (let [manual-col-names (->> (col-names-and-types)
                                  (filter #(not (contains? #{"bigserial" "serial" "smallserial"} (last %))))
                                  (map first))
            row-count (count rows)
            manual-col-count (count manual-col-names)
            prepared-statement (.prepareStatement
                                ctx
                                (if (empty? manual-col-names)
                                  (gen-insert-default-statement row-count)
                                  (gen-insert-statement manual-col-names row-count)))
            param-count (* row-count manual-col-count)
            manual-col-keywords (->> manual-col-names (map keyword) vec)]
        (loop [i 0
               j 0
               param-ordinal 1]
          (cond
            (> param-ordinal param-count) (do
                                            (.execute prepared-statement)
                                            nil)
            (= j manual-col-count) (recur (inc i) 0 param-ordinal)
            :else (do
                    (.setObject
                     prepared-statement
                     param-ordinal
                     (-> rows (nth i) ((nth manual-col-keywords j))))
                    (recur i (inc j) (inc param-ordinal)))))))))

(defn do-update
  "Takes a `com.github.alexisc183.postgure.DataContext` as `ctx` and updates `schema`.`table`
  (strings) according to the data of `coll`. This is an eager operation and returns nil."
  [ctx schema table coll]
  (letfn [(columns-pkeys
            []
            (let [[col-rs pk-rs] (.columnsPKeysMetadata ctx schema table)]
              [(->> (repeat col-rs)
                    (take-while #(.next %))
                    (map (fn [rs] {:name (.getString rs 4)
                                   :sql-type (.getInt rs 5)
                                   :pg-type (.getString rs 6)}))
                    vec)
               (->> (repeat pk-rs)
                    (take-while #(.next %))
                    (map #(.getString % 4))
                    vec)]))
          (gen-values-clause
            [col-types row-count col-count]
            (let [row-indices (range 0 row-count)
                  last-i (dec row-count)
                  last-j (dec col-count)]
              (str
               "values "
               (->> row-indices
                    (map (fn [i] (str
                                  "("
                                  (->> col-types
                                       (map-indexed #(str
                                                      "?"
                                                      (when-not
                                                       (contains? #{"bigserial" "serial" "smallserial"} %2)
                                                        (str "::" %2))
                                                      (when (< %1 last-j) ", ")))
                                       (reduce str))
                                  ")"
                                  (when (< i last-i) ", "))))
                    (reduce str)))))
          (gen-with-statement
            [col-names col-types row-count col-count]
            (let [last-j (dec col-count)]
              (str
               "with tmp ("
               (->> col-names
                    (map-indexed #(str (enquote ctx %2) (when (< %1 last-j) ", ")))
                    (reduce str))
               ") as ( "
               (gen-values-clause col-types row-count col-count)
               " ) ")))
          (gen-update-statement
            [col-names pk-names]
            (let [schema-table (str (enquote ctx schema) "." (enquote ctx table))
                  where-col-names (->> pk-names (map #(enquote ctx %)) set)
                  set-col-names (->> col-names
                                     (map #(enquote ctx %))
                                     (filter #(not (contains? where-col-names %))))
                  where-col-last-index (dec (count where-col-names))
                  set-col-last-index (dec (count set-col-names))]
              (str
               "update "
               schema-table
               " set "
               (->> set-col-names
                    (map-indexed #(str %2 " = tmp." %2 (when (< %1 set-col-last-index) ", ")))
                    (reduce str))
               " from tmp where "
               (->> where-col-names
                    (map-indexed #(str schema-table "." %2 " = tmp." %2 (when (< %1 where-col-last-index) " and ")))
                    (reduce str)))))]
    (throw-if-bad-args ctx schema table coll)
    (when-let [rows (not-empty (vec coll))]
      (let [[col-entries pk-names] (columns-pkeys) ; col-entries: vec[{:name, :sql-type, :pg-type}].
            col-count (count col-entries)
            pk-count (count pk-names)]
        (when (zero? pk-count) (throw (SQLException. "The provided table needs at least one column as primary key as criterion to update rows")))
        (when (zero? (- col-count pk-count)) (throw (SQLException. "The provided table needs at least one column without a primary key constraint in order to update rows")))
        (let [row-count (count rows)
              col-names (map :name col-entries)
              sql-types (->> col-entries (map :sql-type) vec)
              prepared-statement (.prepareStatement
                                  ctx
                                  (str
                                   (gen-with-statement col-names (map :pg-type col-entries) row-count col-count)
                                   (gen-update-statement col-names pk-names)))
              param-count (* row-count col-count)
              col-keywords (->> col-names (map keyword) vec)]
          (loop [i 0
                 j 0
                 param-ordinal 1]
            (cond
              (> param-ordinal param-count) (do
                                              (.execute prepared-statement)
                                              nil)
              (= j col-count) (recur (inc i) 0 param-ordinal)
              :else (do
                      (.setObject
                       prepared-statement
                       param-ordinal
                       (-> rows (nth i) ((nth col-keywords j)))
                       (nth sql-types j))
                      (recur i (inc j) (inc param-ordinal))))))))))
