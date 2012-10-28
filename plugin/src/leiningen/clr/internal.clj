(ns leiningen.clr.internal
  (:require [clojure.pprint  :as pp]
            [clojure.string  :as str]
            [clojure.java.io :as io]
            [leiningen.core.main :as lm])
  (:import (java.io File
  	                Reader BufferedReader InputStream InputStreamReader
                    OutputStream Writer)
           (java.util Map)))


(defn exit-error
  ([code msg & more] {:pre [(pos? code)]}
    (binding [*out* *err*]
      (apply println msg more))
    (lm/abort code))
  ([code] {:pre [(pos? code)]}
    (lm/abort code)))


(def ^:dynamic *verbose* false)


(defn verbose
  [x & args]
  (when *verbose*
    (apply println x args)))


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
    (verbose "Using CLOJURE_LOAD_PATH: " clp)))


(defn configure-compile-path
  [^Map process-env target-path] {:pre [(string? target-path)]}
  ;; set CLOJURE_COMPILE_PATH to the target path
  (.put process-env CLOJURE_COMPILE_PATH target-path)
  (verbose "Using CLOJURE_COMPILE_PATH: " target-path)
  (mkdir-p target-path))


(defmacro with-process-builder
  [pb-symbol base-dir cmd-and-args & body]
  `(let [^ProcessBuilder ~pb-symbol (ProcessBuilder. ~cmd-and-args)]
     ;; set project-root
     (.directory ^ProcessBuilder ~pb-symbol (File. ~base-dir))
     (verbose "Using base directory: " ~base-dir)
     ~@body))


(defn resolve-path
  [f]
  (let []
    (cond
      (symbol? f) (or (.get ^Map (System/getenv) (name f))
                      (exit-error 1 "No such environment variable: " (name f)))
      (string? f) f
      (vector? f) (str/join File/separator (map resolve-path f))
      :otherwise  (exit-error 1 "Expected string/symbol/vector, found "
                                (pr-str f)))))
