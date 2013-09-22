;; This is an annotated example of the options that may be set in the
;; :clr map of a project.clj file. It is a fairly contrived example
;; in order to cover all options exhaustively.
(defproject org.example/sample "0.1.0-SNAPSHOT"
  ;; ----- other entries omitted -----
  ;; (required) Project level usage of the lein-clr plugin is recommended
  :plugins [[lein-clr "0.2.1"]]
  ;; (optional) regular attributes used by lein-clr
  :dependencies   []  ; JAR files are decompressed and .clj files are put on load-path
  :source-paths   ["src"]
  :resource-paths ["resources"]
  :test-paths     ["test"]
  :aot  [#"foo\.core"]
  :main foo.core
  :target-path "target"  ; to infer CLR target-path when [:clr :target-path] is unspecified
  ;; Configuration for the :lein-clr plugin (required)
  :clr {;; (optional) command templates
        ;; Command templates are an easy way to reuse lengthy commands and command arguments.
        ;; You can use them wherever you need to provide command vectors, as in the examples
        ;; in :deps-cmds, :compile-cmd and :main-cmd below. Note that only keywords correspond
        ;; to command template keys.
        :cmd-templates  {;; uses specified file at a location pointed to by env-var CLJCLR14_40
                         ;; ?PATH instructs lein-clr to ignore the token if not found in PATH
                         :clj-exe   [[?PATH "mono"] [CLJCLR14_40 %1]]
                         ;; uses specified file at location "target/clr/clj/Debug 4.0"
                         :clj-dep   [[?PATH "mono"] ["target/clr/clj/Debug 4.0" %1]]
                         ;; ClojureCLR download URL
                         :clj-url   "http://sourceforge.net/projects/clojureclr/files/clojure-clr-1.4.1-Debug-4.0.zip/download"
                         ;; ClojureCLR ZIP filename after download
                         :clj-zip   "clojure-clr-1.4.1-Debug-4.0.zip"
                         ;; Fetch a file from remote-url (%2) into a local file (%1) using cURL
                         :curl      ["curl" "--insecure" "-f" "-L" "-o" %1 %2]
                         ;; Fetch specific version (%2) of a dependency (%1) using NuGet
                         :nuget-ver [[?PATH "mono"] [*PATH "nuget.exe"] "install" %1 "-Version" %2]
                         ;; Fetch any/latest version of a dependency (%1) using NuGet
                         :nuget-any [[?PATH "mono"] [*PATH "nuget.exe"] "install" %1]
                         ;; Unzip a ZIP file (%2) into specified destination directory (%1)
                         :unzip     ["unzip" "-d" %1 %2]
                         ;; Fetch a file from remote-url (%2) into a local file (%1) using wget
                         :wget      ["wget" "--no-check-certificate" "--no-clobber" "-O" %1 %2]}
        ;; (optional) comands to fetch dependencies, they are run in lib folder
        ;; You can use command templates to specify those, or use ordinary command vector.
        :deps-cmds      [;; using curl to download ClojureCLR 1.4
                         [:curl  :clj-zip :clj-url]
                         ;; using wget to download ClojureCLR 1.4
                         [:wget  :clj-zip :clj-url]
                         ;; unzip the downloaded ClojureCLR ZIP file
                         [:unzip "../clj" :clj-zip]
                         ;; NuGet dependency by version
                         [:nuget-ver "NHibernate" "3.3.2.4000"]
                         ;; NuGet dependency (any/latest version)
                         [:nuget-any "MySQL.Data"]
                         ;; copy dependency files (for demonstration only, not recommended)
                         ["cp" "-r" "C:\\alldeps\\foo" "."]]
        ;; (optional) for matching path of assembly dependencies
        ;; the deps-cmds may download many files, not all of which you may want to include
        ;; use regex to filter out unwanted files
        :assembly-deps-regex #".*[Nn]et(40|35).*"
        ;; (optional) the compile command - a command vector having executable and optional args
        ;; You can use command template or ordinary command vector to specify this.
        ;; For Mono, you may use ["mono" [*PATH "Clojure.Compile.exe"]] instead of the example below
        ;; A 2-element vector where the first element is a symbol beginning with an asterisk
        ;; is treated as search-path - first element (env val) is searched for second element.
        :compile-cmd    ["Clojure.Compile.exe"] ; .NET default, Mono example below
        ;; (optional) the main command - a vector having executable and optional args
        ;; A symbol is looked up as environment variable,
        ;; vector elements are concatenated using path-separator
        :main-cmd       ["mono" [CLJCLR14_PATH "Clojure.Main.exe"]]
        ;; (optional) path to third-party assemblies other than `:deps-cmds`
        ;; note that you can optionally provide a regex to match the path
        :assembly-paths [["ext/foo" #".*[Nn]et40.*"]
                         "ext/baaz"]
        ;; (optional) path where build files are stored
        :target-path    "path/to/clr-build/files"})
