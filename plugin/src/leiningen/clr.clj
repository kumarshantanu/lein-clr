(ns leiningen.clr
  (:require [clojure.pprint  :as pp]
            [clojure.string  :as str]
            [clojure.java.io :as io]
            [leiningen.clr.internal   :as in]
            [leiningen.core.classpath :as lc])
  (:import (java.io Reader BufferedReader File InputStream InputStreamReader
                    OutputStream Writer)
           (java.util Map)))


;; ===== Project keys =====

(def pk-target-path         [:clr :target-path])
(def pk-cmd-templates       [:clr :cmd-templates])
(def pk-compile-cmd         [:clr :compile-cmd])
(def pk-main-cmd            [:clr :main-cmd])
(def pk-assembly-paths      [:clr :assembly-paths])
(def pk-deps-cmds           [:clr :deps-cmds])
(def pk-assembly-deps-regex [:clr :assembly-deps-regex])
(def pk-load-paths          [:clr :load-paths])
(def pk-unchecked-math      [:clr :unchecked-math])
(def pk-warn-on-reflection  [:clr :warn-on-reflection])

(def pk-aot [:clr :aot])


;; ===== Target paths =====

(defn target-path
  [project]
  (or (get-in project pk-target-path)
      (str (:target-path project) File/separator "clr")))


(defn target-bin-path
  [project]
  (str (target-path project) File/separator "bin"))


(defn target-lib-path
  [project]
  (str (target-path project) File/separator "lib"))


(defn target-src-path
  [project]
  (str (target-path project) File/separator "src"))


;; ===== Project keys =====

(defn cmd-templates
  [project]
  (get-in project pk-cmd-templates))


(defn proj-key-cmd
  "Return a sequence of command and arguments"
  [proj-key default-value project]
  (or (when-let [cmd (get-in project proj-key)]
        (assert (vector? cmd))
        (in/resolve-path cmd (cmd-templates project)))
      default-value))


(defn default-cmd
  [^String cmd] {:pre [(string? cmd)]}
  (let [^String os-name (System/getProperty "os.name")]
    (if (and os-name (.startsWith ^String os-name "Windows"))
      [cmd]
      ["mono" (or (in/which cmd) cmd)])))


(def clj-compile-cmd (partial proj-key-cmd pk-compile-cmd
                              (default-cmd "Clojure.Compile.exe")))


(def clj-main-cmd (partial proj-key-cmd pk-main-cmd
                           (default-cmd "Clojure.Main.exe")))


(defn all-load-paths
  [project]
  (let [load-paths (get-in project pk-load-paths)]
    (when load-paths
      (in/warn "[:clr :load-paths] is deprecated and will be discontinued soon. Consider using :resource-paths"))
    (->> load-paths
         (map #(in/resolve-path % (cmd-templates project)))
         (concat (lc/get-classpath project)
                 [(target-src-path project)]))))


(defn aot-namespaces
  [project]
  (let [aot-nses (or (get-in project pk-aot)
                     (:aot project))
        all-nses (mapcat in/scan-namespaces (all-load-paths project))]
    (mapcat (fn [each]
              (if (instance? java.util.regex.Pattern each)
                (filter (partial re-matches each) all-nses)
                [(str each)]))
      aot-nses)))


(defn assembly-search-paths
  [project]
  (->> (get-in project pk-assembly-paths)
       (map in/as-vector)
       (concat [[(target-lib-path project)
                 (get-in project pk-assembly-deps-regex)]])
       (mapcat in/filter-assembly-paths)))


(defn get-eval-string
  [project]
  (str (when (get-in project pk-unchecked-math)
         "(set! *unchecked-math* true)")
       (when (or (get-in project pk-warn-on-reflection)
                 (:warn-on-reflection project))
         "(set! *warn-on-reflection* true)")))


(defn asm-load-init
  [project init-file]
  (in/spit-assembly-load-instruction
    init-file
    (assembly-search-paths project))
  (spit init-file (str \newline (get-eval-string project)) :append true)
  init-file)


(defn configure-compile-env
  [project ^Map process-env load-paths]
  (in/configure-load-path process-env load-paths)
  (in/configure-compile-path process-env (target-bin-path project))
  (->> (get-in project pk-unchecked-math)
       (in/configure-unchecked-math process-env))
  (->> (:warn-on-reflection project)
       (or (get-in project pk-warn-on-reflection))
       (in/configure-warn-on-reflection process-env)))


;;; ========== Tasks ==========


(defn task-clean
  [project]
  (let [tp (target-path project)]
    (in/verbose "Recursively deleting directory: " tp)
    (in/rm-rf tp)))


(defn task-deps
  "Fetch dependencies. Do not fetch if already fetched earlier."
  [project]
  (let [lib   (target-lib-path project)
        src   (target-src-path project)
        src-p #"^((?!project\.clj|META\-INF|.*\.class|cljs/|.*\.cljs).*)$"
        cmds  (get-in project pk-deps-cmds)]
    (if-not (.exists (File. lib))
      (do
        (in/verbose "Making sure" lib "exists")
        (in/mkdir-p lib)
        (in/verbose "Fetching dependencies" cmds)
        (->> cmds
             (map #(in/resolve-path % (cmd-templates project)))
             (map #(in/run-cmd % lib))
             dorun)
        (in/verbose "Extracting JAR dependencies")
        (->> (lc/get-classpath project)
             (filter #(.isFile (File. %)))
             (map #(in/unzip-file % src src-p))
             dorun))
      (in/verbose "Not fetching dependencies. Run `lein clean` to re-fetch."))))


(defn task-compile
  [project namespaces]
  (task-deps project)
  (let [allp (all-load-paths project)
        srcp (concat (:source-paths project) (:test-paths project))
        nses (cond
               (empty? namespaces)             (aot-namespaces project)
               (and (= 1 (count namespaces))
                (= ":all" (first namespaces))) (mapcat in/scan-namespaces srcp)
               :otherwise                      namespaces)
        exec (concat (clj-compile-cmd project) nses)]
    (in/with-process-builder pb (:root project) exec
      (configure-compile-env project (.environment ^ProcessBuilder pb) allp)
      (apply in/verbose "Running: " (map pr-str exec))
      (in/run-process pb))))


(defn task-help
  []
  (println "
Available tasks:
clean    delete files generated by lein-clr
compile  compile clj source to .dll .exe .pdb/.mdb files; available switch :all
deps     fetch project dependencies (called implicitly by other tasks)
help     show this help screen
repl     load a REPL with sources into CLOJURE_LOAD_PATH
run      run a namespace having `-main` function
test     run tests in specified/all test namespaces
"))


(defn task-repl
  [project]
  (task-deps project)
  (let [init-file  (asm-load-init project (in/get-temp-file))
        allp (all-load-paths project)
        _    (in/verbose-init-with init-file)
        exec (concat (clj-main-cmd project) ["-i" init-file "-e" (get-eval-string project) "-r"])]
    (in/with-process-builder pb (:root project) exec
      (in/configure-load-path (.environment ^ProcessBuilder pb) allp)
      (apply in/verbose "Running:" (map pr-str exec))
      (in/run-process pb :pipe-input))))


(def no-main "No :main namespace specified in project.clj.")
(def no-ns   "Option -m requires a namespace argument.")

(defn parse-run-args
  "Return a vector where first elem is the main ns, followed by remaining args"
  [project args]
  (let [proj-ns #(or (:main project) (in/exit-error 1 no-main))
        user-ns #(or (second args)   (in/exit-error 1 no-ns))]
    (cond
      (empty? args)         [(str (proj-ns))]
      (= (first args) "--") [(str (proj-ns)) (rest args)]
      (= (first args) "-m") [(user-ns) (nthrest args 2)]
      :otherwise            [(str (proj-ns)) args])))


(defn task-run
  [project args]
  (task-deps project)
  (let [init-file  (asm-load-init project (in/get-temp-file))
        allp       (all-load-paths project)
        [rns args] (parse-run-args project args)
        _          (in/verbose-init-with init-file)
        r-ex       (concat (clj-main-cmd project)
                           ["-i" init-file "-e" (get-eval-string project)
                            "-m" rns] args)]
    ;; run the namespace (r-ex)
    (in/with-process-builder pb (:root project) r-ex
      (in/configure-load-path (.environment ^ProcessBuilder pb) allp)
      (apply in/verbose "Running: " (map pr-str r-ex))
      (in/run-process pb))))


(defn task-test
  [project namespaces]
  (task-deps project)
  (let [init-file (asm-load-init project (in/get-temp-file))
        allp (all-load-paths project)
        nses (mapcat in/scan-namespaces (:test-paths project))
        rtns (if (seq namespaces) namespaces nses)
        nstr (str/join " " (map (partial str "'") rtns))
        _    (in/spit-require-ns init-file rtns)
        _    (in/verbose-init-with init-file)
        expr (format "(use 'clojure.test) (run-tests %s)" nstr)
        exec (concat (clj-main-cmd project)
                     ["-i" init-file "-e" (str (get-eval-string project)
                                               expr)])]
    ;; run the tests
    (in/with-process-builder pb (:root project) exec
      (in/configure-load-path (.environment ^ProcessBuilder pb) allp)
      (apply in/verbose "Running: " (map pr-str exec))
      (in/run-process pb))))


(defmacro try-pst
  [& body]
  `(try ~@body
     (catch Throwable t#
       (.printStackTrace t#))))


(defn clr
  "Automate build tasks for ClojureCLR projects

  For more information, run the below command:

  lein clr help"
  [project & [task & args]]
  (case task
    "clean"   (task-clean project)
    "compile" (try-pst (task-compile project args))
    "deps"    (try-pst (task-deps project))
    "help"    (task-help)
    "repl"    (try-pst (task-repl project))
    "run"     (try-pst (task-run  project args))
    "test"    (try-pst (task-test project args))
    "-v"      (binding [in/*verbose* true] (apply clr project args))
    (do (println "No such task: " (pr-str task))
        (task-help))))