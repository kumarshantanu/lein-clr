(ns leiningen.clr.internal
  (:require [clojure.pprint  :as pp]
            [clojure.string  :as str]
            [clojure.java.io :as io]
            [leiningen.core.main :as lm])
  (:import (java.io File
  	                Reader BufferedReader InputStream InputStreamReader
                    OutputStream Writer)
           (java.util     Enumeration Map)
           (java.util.zip ZipEntry ZipFile)))


(def ^:dynamic *verbose* false)


(defn verbose
  [x & args]
  (when *verbose*
    (apply println "[DEBUG]" x args))
  (flush))


(defn warn
  [x & args]
  (apply println "\n[WARNING!]" x args "\n")
  (flush))


(defn exit-error
  ([code msg & more] {:pre [(pos? code)]}
    (binding [*out* *err*]
      (apply println "\n[ERROR]" msg more)
      (flush))
    (lm/abort code))
  ([code] {:pre [(pos? code)]}
    (lm/abort code)))


(defn echo
  [x]
  (println "[--ECHO--]" x)
  (flush)
  x)


(defn sleep
  [millis]
  (try (Thread/sleep millis)
    (catch InterruptedException _
      (.interrupt (Thread/currentThread)))))


(def IDLE 10)
(def EOF -1)


(defn pipe-output
  [quit? ^InputStream out ^Writer dest]
  (let [rdr (BufferedReader. (io/reader out))]
    (loop []
      (when
        (if-not (.ready rdr)
          (do (sleep IDLE)
              (or (.ready rdr) (not @quit?)))
          (let [i (.read ^BufferedReader rdr)]
            (when-not (= i EOF)
              (.write ^Writer dest i)
              (.flush dest)
              true)))
        (recur)))
    (verbose "Output closed: " dest)))


(defn pipe-input
  [quit? ^OutputStream in ^Reader source]
  (let [con (BufferedReader. (io/reader source))
        wtr (io/writer in)]
    (loop [i (.read ^BufferedReader con)]
      (when-not (or (= i EOF) @quit?)
        (.write wtr i)
        (.flush wtr)
        (when-not @quit?
          (recur (.read ^BufferedReader con)))))
    (.close in)
    (reset! quit? true)
    (verbose "Input closed: " source)))


(defmacro futurex
  [& body]
  `(future
     (try ~@body
       (catch Exception e#
         (.printStackTrace e#)))))


(defn run-process
  ([^ProcessBuilder process-builder pipe-input?]
    (let [^Process process (.start process-builder)
          quit? (atom false)
          e (futurex (pipe-output quit? (.getErrorStream process) *err*))
          o (futurex (pipe-output quit? (.getInputStream process) *out*))
          i (when pipe-input?
              (futurex (pipe-input quit? (.getOutputStream process) System/in)))]
      (let [exit (.waitFor process)]
        (reset! quit? true)
        (when i @i)
        (when (pos? exit)
          @e @o
          (lm/abort exit)))))
  ([^ProcessBuilder process-builder]
    (run-process process-builder false)))


(defn scan-namespaces
  "Given a toplevel directory, recursively scan .clj files and return a
  collection of namespaces."
  ([toplevel-dir parent-ns] {:pre [(vector? parent-ns)]}
    (let [tl-dir (if (instance? File toplevel-dir)
                     toplevel-dir
                     (File. toplevel-dir))
          clj-x? #(.endsWith ^String (.getName ^File %) ".clj")
          hyphen #(.replace ^String % \_ \-)]
      (mapcat (fn [^File each]
                (cond
                  (.isDirectory each) (scan-namespaces each
                                                       (->> (.getName each)
                                                            hyphen
                                                            (conj parent-ns)))
                  (and (.isFile each)
                       (clj-x? each)) (let [s (.getName each)]
                                        (vector (->> (subs s 0 (- (count s) 4))
                                                     hyphen
                                                     (conj parent-ns)
                                                     (str/join "."))))
                  :otherwise          []))
              (.listFiles ^File tl-dir))))
  ([toplevel-dir]
  	(scan-namespaces toplevel-dir [])))


(defn as-file
  [x]
  (if (instance? File x)
    x
    (File. (str x))))


(defn mkdir-p
  [dir]
  (let [^File d (as-file dir)]
    (when-not (.exists d)
      (.mkdirs d))))


(defn rm-rf
  [dir]
  (let [^File d (as-file dir)]
    (when (.exists d)
      (when (.isDirectory d)
        (dorun (map #(if (.isDirectory %) (rm-rf %) (.delete %))
                    (.listFiles d))))
      (.delete d))))


(defn which
  "Implementation of the `which` command for systems like `mono`. Return
  absolute path of the file, or nil if no valid file found."
  ([cmd path] {:pre [(string? cmd)
  	                 (string? path)]}
  	(let [tokens (.split ^String path File/pathSeparator)
  	      find-f (fn [dir-name]
  	      	       (let [f (-> (File. dir-name)
  	      	       	           (.getAbsolutePath)
  	      	       	           (str File/separator cmd)
  	      	       	           (File.))]
  	      	         (when (.isFile ^File f)
  	      	           (.getAbsolutePath ^File f))))]
  	  (some find-f tokens)))
  ([cmd]
    (when-let [path (.get ^Map (System/getenv) "PATH")]
      (which cmd path))))


(def CLOJURE_LOAD_PATH    "CLOJURE_LOAD_PATH")
(def CLOJURE_COMPILE_PATH "CLOJURE_COMPILE_PATH")


(defn configure-load-path
  [^Map process-env paths]
  ;; include source paths into CLOJURE_LOAD_PATH
  (let [clp (str/join File/pathSeparator paths)]
    (.put ^Map process-env CLOJURE_LOAD_PATH clp)
    (verbose "Using CLOJURE_LOAD_PATH:" clp)))


(defn configure-compile-path
  [^Map process-env target-path] {:pre [(string? target-path)]}
  ;; set CLOJURE_COMPILE_PATH to the target path
  (.put process-env CLOJURE_COMPILE_PATH target-path)
  (verbose "Using CLOJURE_COMPILE_PATH:" target-path)
  (mkdir-p target-path))


(defmacro with-process-builder
  [pb-symbol base-dir cmd-and-args & body]
  `(let [^ProcessBuilder ~pb-symbol (ProcessBuilder. ~cmd-and-args)]
     ;; set project-root
     (.directory ^ProcessBuilder ~pb-symbol (File. ~base-dir))
     (verbose "Using base directory:" ~base-dir)
     ~@body))


(defn run-cmd
  [exec root]
  (verbose "Running" exec)
  (with-process-builder
    pb root exec
    (run-process pb)))


(defn resolve-template
  [template args]
  (if (vector? template)
    (-> (fn [each]
          (cond
            ;; symbol
            (symbol? each)
            (let [[pc & digits :as sym] (name each)]
              (if (= \% pc)
                (let [num (if (seq digits)
                              (Integer/parseInt (str/join digits))
                              1)]
                  (nth args (dec num)))
                each))
            ;; vector
            (vector? each)
            (resolve-template each args)
            ;; fallback
            :otherwise
            each))
        (map template)
        vec)
    ;; fallback
    template))


(defn resolve-path-str
  "Convert path to string and return it."
  [f]
  (cond
    (symbol? f) (or (System/getenv (name f))
                    (exit-error 1 "No such environment variable:" (name f)))
    (string? f) f
    (vector? f) (str/join File/separator
                          (map resolve-path-str f))
    :otherwise  (exit-error 1 "Expected string/symbol/vector, found"
                                (pr-str f))))


(defn resolve-path
  "If you supply a vector of args, you get back a vector.
  See also:
    resolve-path-str"
  [f template-map]
  (let [path-search (fn [[path file]]
                      (let [file (resolve-path-str (vector file))
                            path (resolve-path-str (-> (rest (name path))
                                                       str/join
                                                       symbol))]
                          (or (which file path)
                              (exit-error 1 "Cannot locate" file "in" path))))
        assert-tkey (fn [needle]
                      (when-not (contains? template-map needle)
                        (exit-error 1 "Template key" needle "not found in"
                                    (pr-str template-map))))
        templ-subst (fn [f]
                      (let [left   (take-while (comp not keyword?) f)
                            needle (nth f (count left))
                            right  (->> (drop (inc (count left)) f)
                                        (map #(resolve-path %1 template-map)))]
                        (assert-tkey needle)
                        (resolve-path
                          (->> (resolve-template (get template-map needle) right)
                               (concat left)
                               vec)
                          template-map)))]
    (cond
      (symbol? f) (or (System/getenv (name f))
                      (exit-error 1 "No such environment variable:" (name f)))
      (keyword? f) (do (assert-tkey f)
                       (get template-map f))
      (string? f) f
      (vector? f) (cond
                    ;; path search
                    (and (= 2 (count f))
                         (symbol? (first f))
                         (let [[p1 & the-name] (seq (name (first f)))]
                           (and (= \* p1) (seq the-name))))
                    (path-search f)
                    ;; template substitution
                    (some keyword? f)
                    (templ-subst f)
                    ;; fallback (regular) resolution
                    :otherwise
                    (->> (map #(resolve-path % template-map) f)
                         (map resolve-path-str)
                         flatten
                         vec))
      :otherwise  (exit-error 1 "Expected string/symbol/vector, found"
                                (pr-str f)))))


(defn get-temp-file
  []
  (let [f (File/createTempFile "lein-clr-" ".tmp")]
    (.deleteOnExit ^File f)
    (doto (.getAbsolutePath ^File f)
      (spit ""))))


(defn as-vector
  [x]
  (cond (coll? x) (into [] x)
        (seq? x)  (into [] x)
        :default  [x]))


(defn recursive-assembly-paths
  ([dir parent-name-vec] {:pre [(or (instance? File dir) (string? dir))
                                (vector? parent-name-vec)]}
    (let [dir     (if (string? dir) (File. dir) dir)
          as-name #(str/join File/separator (conj parent-name-vec %))
          entries (.listFiles ^File dir)
          is-dll? (fn [^File f] (and (.isFile f)
                                     (re-find #"\.([dD][lL][lL]|[eE][xX][eE])$"
                                              (.getName f))))
          d-files (map (comp as-name #(.getName %)) (filter is-dll? entries))
          subdirs (filter #(.isDirectory ^File %) entries)]
      (-> #(recursive-assembly-paths % (conj parent-name-vec (.getName %)))
          (mapcat subdirs)
          (concat d-files))))
  ([dir]
    (recursive-assembly-paths dir [])))


(defn filter-assembly-paths
  [[base regex]]
  (->> (recursive-assembly-paths base)
       (filter (fn [name]
                 (let [r (or regex #".*")]
                   (if (re-find r name)
                     (do (verbose "Including assembly file" name) true)
                     (verbose "NOT including assembly file" name)))))
       (map (partial str base File/separator))))


(defn spit-assembly-load-instruction
  [temp-file assembly-paths]
  (when (seq assembly-paths)
    (let [content (->> assembly-paths
                       (map (comp (partial format "(assembly-load-from %s)") pr-str))
                       (str/join "\n"))]
      (spit temp-file content
            :append true))))


(defn spit-require-ns
  [temp-file nses]
  (when (seq nses)
    (spit temp-file (-> (partial format "\n(require '%s)")
                        (map nses)
                        str/join)
          :append true)))


(defn verbose-init-with
  [temp-file]
  (verbose "Initializing with:" (slurp temp-file)))


(defn unzip-file
  [zip-filename dest regex] {:pre [(string? zip-filename)
                                   (string? dest)]}
  (with-open [^ZipFile zip-file (ZipFile. zip-filename)]
    (let [^Enumeration zip-entries (.entries zip-file)]
      (while (.hasMoreElements zip-entries)
        (let [^ZipEntry entry (.nextElement zip-entries)
              ^String   ename (.getName entry)
              ^String   epath (str dest File/separator ename)]
          (when (re-find regex ename)
            (if (.isDirectory entry)
              (do (verbose "Creating directory:" ename)
                (mkdir-p epath))
              (do (verbose "Extracting file:" ename)
                (mkdir-p (.getParentFile (File. epath)))
                (io/copy (.getInputStream zip-file entry)
                         (io/file epath))))))))))