# Changes and TODO


## TODO

* CLR re-implementation (of Leiningen features)
  * eval-in-project
  * bultitude(??)
  * enable plugins written in ClojureCLR
* Tasks
  * test (test selector support - doable??)
* Script support
  * Executing scripts _a la_ lein-exec


## 2012-Dec-?? / 0.3.0

* Honor `[:clr :unchecked-math]`
* Honor `[:clr :warn-on-reflection]` and `:warn-on-reflection`
* `deps` as implicit as well as a named task invokable at command line
* [TODO] `compile` as implicit as well as named task invokable at command line
* [TODO] Pre-compile and Post-compile commands; to compile C#, F# etc. sources
* [TODO] Load compiled assemblies into assembly search path


## 2012-Nov-19 / 0.2.0

* Project config support (`:clr` key in `project.clj`)
  * Support for `:assembly-paths`
    * transparently calls `assembly-load-from` for matching assemblies
  * Command resolution
    * Support searching within env-var value, e.g. `[*PATH "foo.exe"]`
    * Command templates `:cmd-templates`
* Dependency support
  * Maven dependencies on CLOJURE_LOAD_PATH
  * Command-based dependencies (via NuGet etc.) -- `:deps-cmds`
  * Assembly deps regex `:assembly-deps-regex` to match versions/types
    * transparently calls `assembly-load-from` for matching assemblies
* Fixes for `compile` task
* sample.project.clj with config options
* Updated Leiningen-project template


## 2012-Oct-28 / 0.1.0

* Tasks
  * clean
  * compile
  * repl
  * run
  * test (without test-selector support)
* Project config support (`:clr` key in `project.clj`)
  * Configurable Clojure executable names: `:compile-cmd` `:main-cmd`
  * Mono support: `:compile-cmd` and `:main-cmd`
  * External libraries support: `:load-paths`
  * Support for multiple ClojureCLR versions via env-var lookup
* Leiningen-project template to generate skeleton project