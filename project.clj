(defproject postgure "1.0.0"
  :aot :all
  :description "Clojure ORM for PostgreSQL"
  :url "https://github.com/AlexisC183/postgure"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.1"]]
  :java-source-paths ["src/com/github/alexisc183/postgure"]
  :repl-options {:init-ns com.github.alexisc183.postgure.core})
